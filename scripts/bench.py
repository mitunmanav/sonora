#!/usr/bin/env python3
"""Capture Android TV benchmark evidence. See scripts/perf-ab.md."""

import argparse
from collections import defaultdict
import csv
import datetime
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
WALKS = {
    "mixed": (22, 22, 22, 22, 20, 22, 22, 21, 21, 19, 21, 21),
    "horizontal": (22, 21) * 6,
    "vertical": (20, 19) * 6,
}
MAIN = "android.intent.action.MAIN"
HOME = "android.intent.category.HOME"
LEANBACK = "android.intent.category.LEANBACK_LAUNCHER"


def component_name(value):
    if not re.fullmatch(r"[\w.]+/[\w.$]+", value, re.ASCII):
        raise ValueError(f"Expected an Android component, got {value!r}")
    package, activity = value.split("/")
    return f"{package}/{package + activity if activity.startswith('.') else activity}"


def focused_component(dump):
    match = re.search(r"^\s*mCurrentFocus=Window\{[^\n}]* ([\w.]+/[\w.$]+)\}", dump, re.M)
    return component_name(match[1]) if match else None


def counter(dump, field):
    match = re.search(rf"^\s*{re.escape(field)}:[ \t]*(\d+)(?:ms\b|\b)", dump, re.M)
    return int(match[1]) if match else None


def cold_time(dump):
    value = counter(dump, "TotalTime")
    launch = re.search(r"^LaunchState:\s*(\w+)", dump, re.M)
    if not value or not re.search(r"^Status: ok\s*$", dump, re.M) or (launch and launch[1] != "COLD"):
        raise ValueError("No verified cold first-frame time")
    return value


def frame_metrics(dump):
    fields = {
        "frames": "Total frames rendered",
        "deadline_misses": "Number Frame deadline missed",
        "p50_ms": "50th percentile",
        "p95_ms": "95th percentile",
        "p99_ms": "99th percentile",
        "gpu_p90_ms": "90th gpu percentile",
    }
    values = {key: counter(dump, field) for key, field in fields.items()}
    frames, misses = values["frames"], values["deadline_misses"]
    if frames is None or (misses is not None and misses > frames):
        raise ValueError("Invalid Android frame counts")
    values["deadline_rate_pct"] = 100 * misses / frames if frames and misses is not None else None
    if values["gpu_p90_ms"] == 4950:
        values["gpu_p90_ms"] = None  # Mi TV overflow bucket
    return values


def idle_cpu(report, pid):
    samples = []
    column = None
    for line in report.splitlines():
        cells = line.replace("S[%CPU]", "S %CPU").split()
        if cells and cells[0] == "PID":
            column = cells.index("%CPU") if "%CPU" in cells else None
        elif column is not None and len(cells) > column and cells[0] == pid:
            samples.append(float(cells[column]))
    return samples[1:]


def latest_present_ns(dump):
    presented = []
    for line in dump.splitlines():
        parts = line.split()
        if len(parts) == 3 and all(part.isdecimal() for part in parts):
            timestamp = int(parts[1])
            if 0 < timestamp < 2**63 - 1:
                presented.append(timestamp)
    return max(presented, default=0)


def block_order(sides):
    order = "".join(sides)
    if order not in ("ABBA", "BAAB"):
        raise ValueError(f"Expected four ABBA or BAAB slots, got {order}")
    return order


def metric_spread(values):
    known = [value for value in values if value is not None]
    if not known:
        return 0, None, None, None
    return len(known), statistics.median(known), min(known), max(known)


def compare(directories):
    groups = defaultdict(dict)
    for directory in directories:
        config = json.loads((directory / "run.json").read_text())
        if not all(config.get(field) for field in ("block", "side", "slot")):
            raise ValueError(f"{directory} needs --block, --side and --slot")
        rows = [json.loads(line) for line in (directory / "results.jsonl").read_text().splitlines()]
        if not rows or rows[-1] != {"sample": "run", "status": "ok"}:
            raise ValueError(f"{directory} is incomplete")
        key = (config["serial"], config["profile"], config["block"])
        if config["slot"] in groups[key]:
            raise ValueError(f"Duplicate slot {config['slot']} in {key}")
        checksums = tuple(line.split()[0] for line in (directory / "apk-sha256.txt").read_text().splitlines())
        if not checksums:
            raise ValueError(f"{directory} has no APK hash")
        fixtures = tuple(sorted(line for option in ("video", "files") if config.get(option)
                                for line in (directory / f"{option}-sha256.txt").read_text().splitlines()))
        groups[key][config["slot"]] = config, rows[:-1], checksums, fixtures

    report = csv.writer(sys.stdout)
    report.writerow(("device", "profile", "block", "order", "workload", "metric",
                     "A_n", "A_median", "A_min", "A_max", "B_n", "B_median", "B_min",
                     "B_max", "B_minus_A_slot_median"))
    for key, slots in sorted(groups.items()):
        order = block_order(slots[n][0]["side"] for n in sorted(slots))
        reference = slots[1][0]
        hashes = {}
        workload_slots = defaultdict(lambda: defaultdict(list))
        for slot, (config, rows, checksums, fixtures) in sorted(slots.items()):
            for field in ("trials", "workloads", "idle_seconds", "gap", "settle", "skip_idle",
                          "app", "video", "files", "files_dir", "loaded_smoke"):
                if config.get(field) != reference.get(field):
                    raise ValueError(f"Slot {slot} in {key} differs in {field}")
            side = config["side"]
            if hashes.setdefault(side, checksums) != checksums:
                raise ValueError(f"Installed APK changed for side {side} in {key}")
            if fixtures != slots[1][3]:
                raise ValueError(f"Slot {slot} in {key} used different fixture files")
            for row in rows:
                workload = row["sample"].rsplit("-", 1)[0]
                if row["sample"].endswith("-warmup"):
                    continue
                for metric, value in row.items():
                    if value is None or isinstance(value, (int, float)):
                        workload_slots[workload, metric][slot].append(value)
        for (workload, metric), samples in sorted(workload_slots.items()):
            spreads, medians = {}, {}
            for side in "AB":
                values = [samples[n] for n in slots if slots[n][0]["side"] == side]
                spreads[side] = metric_spread(value for group in values for value in group)
                medians[side] = metric_spread(metric_spread(group)[1] for group in values)[1]
            delta = medians["B"] - medians["A"] if None not in medians.values() else None
            report.writerow((*key, order, workload, metric, *spreads["A"], *spreads["B"], delta))


class Capture:
    def __init__(self, adb, serial, directory, args):
        self.adb = [adb, "-s", serial]
        self.directory = directory
        self.args = args
        self.home = component_name(f"{args.pkg}/{args.activity}")
        self.target = directory

    def call(self, *command, name=None, optional=False, timeout=25):
        result = subprocess.run(self.adb + list(command), capture_output=True, timeout=timeout)
        if name:
            (self.target / name).write_bytes(result.stdout)
            if result.stderr:
                (self.target / (name + ".stderr")).write_bytes(result.stderr)
        if result.returncode and not optional:
            raise RuntimeError(f"ADB {command[0]} failed ({result.returncode}): "
                               + result.stderr.decode(errors="replace").strip())
        return result.stdout.decode(errors="replace").replace("\r\n", "\n")

    def shell(self, *command, **kwargs):
        return self.call("shell", shlex.join(map(str, command)), **kwargs)

    def focus(self, component=None, *, package=None, timeout=0):
        deadline = time.monotonic() + timeout
        while True:
            dump = self.shell("dumpsys", "window", "displays", timeout=6)
            current = focused_component(dump)
            if current and (current == component or current.split("/")[0] == package):
                return current
            if time.monotonic() >= deadline:
                (self.target / "unexpected-focus.txt").write_text(dump)
                raise RuntimeError(f"Expected focus {component or package}, found {current}")
            time.sleep(0.1)

    def pid(self, package):
        return self.shell("pidof", package, optional=True).strip() or None

    def resolve(self, *intent):
        output = self.shell("cmd", "package", "resolve-activity", "--brief", *intent)
        return component_name(output.strip().splitlines()[-1] if output.strip() else "")

    def screenshot(self, name):
        self.call("exec-out", "screencap", "-p", name=name)

    def metadata(self):
        for name, command in {
            "model": ("getprop", "ro.product.model"),
            "build": ("getprop", "ro.build.fingerprint"),
            "sdk": ("getprop", "ro.build.version.sdk"),
            "display": ("dumpsys", "display"),
            "packages": ("pm", "list", "packages", "-f"),
            "launcher-build": ("dumpsys", "package", self.args.pkg),
            "thermal-before": ("dumpsys", "thermalservice"),
            "processes-before": ("ps", "-A"),
        }.items():
            self.shell(*command, name=name + ".txt", optional=name == "thermal-before")
        paths = self.shell("pm", "path", self.args.pkg).splitlines()
        if not paths or any(not line.startswith("package:") for line in paths):
            raise RuntimeError("Could not resolve installed APKs")
        self.shell("sha256sum", *(line.removeprefix("package:") for line in paths), name="apk-sha256.txt")
        for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell("settings", "get", "global", setting, name=setting + ".txt")
        self.shell("cat", "/proc/meminfo", name="system-before.txt")

    def fixtures(self):
        if self.args.video:
            video = unquote(urlsplit(self.args.video[1]).path)
            self.shell("sha256sum", video, name="video-sha256.txt")
        if self.args.files:
            files = self.shell("find", self.args.files_dir, "-type", "f",
                               "-exec", "sha256sum", "{}", ";", name="files-sha256.txt")
            if not files.strip():
                raise RuntimeError("Files fixture directory has no readable files")

    def sample(self, name, action):
        self.target = self.directory / name
        self.target.mkdir()
        try:
            metrics = action()
        except (RuntimeError, ValueError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
            self.record(name, {"status": "aborted", "reason": str(error) or type(error).__name__})
            raise
        finally:
            self.target = self.directory
        self.record(name, {"status": "ok", **metrics})

    def record(self, name, result):
        result = {"sample": name, **result}
        with (self.directory / "results.jsonl").open("a") as stream:
            print(json.dumps(result), file=stream)
        print(json.dumps(result), flush=True)

    def cold_start(self, settings):
        self.shell("am", "start", "-W", "-n", settings, name="settings.txt")
        self.focus(settings, timeout=6)
        self.shell("am", "force-stop", self.args.pkg)
        if self.pid(self.args.pkg):
            raise RuntimeError("Launcher was still running before cold launch")
        start = self.shell("am", "start", "-W", "-n", self.home, name="launch.txt")
        self.focus(self.home, timeout=6)
        return cold_time(start)

    def memory(self, tag, package):
        dump = self.shell("dumpsys", "meminfo", package, name=f"{tag}-meminfo.txt")
        return counter(dump, "TOTAL PSS")

    def gfx(self, package, name="frames.txt"):
        dump = self.shell("dumpsys", "gfxinfo", package, "framestats", name=name)
        return frame_metrics(dump)

    def walk(self, name, settings):
        metrics = {}
        if not self.args.skip_idle:
            metrics["first_frame_ms"] = self.cold_start(settings)
        else:
            self.shell("am", "start", "-W", "-n", self.home, name="launch.txt")
        self.focus(self.home, timeout=6)
        time.sleep(self.args.settle)
        self.screenshot("start.png")
        metrics["launcher_before_kib"] = self.memory("before", self.args.pkg)
        pid = self.pid(self.args.pkg)
        self.shell("dumpsys", "gfxinfo", self.args.pkg, "reset")
        relative = self.home.replace(f"{self.args.pkg}/{self.args.pkg}.", f"{self.args.pkg}/.")
        # Keep the focus dump drained and the Wi-Fi shell sessions short.
        for offset in range(0, len(WALKS[name]), 4):
            keys = " ".join(map(str, WALKS[name][offset:offset + 4]))
            self.call("shell", f"""
                for key in {keys}; do
                    focus=$(dumpsys window displays | grep mCurrentFocus=)
                    case "$focus" in
                        *" {self.home}}}"|*" {relative}}}") ;;
                        *) echo "$focus" >&2; exit 3 ;;
                    esac
                    cut -d ' ' -f 1 /proc/uptime
                    input keyevent "$key" || exit 4
                    sleep {self.args.gap}
                done
                """, name=f"key-uptime-{offset // 4 + 1}.txt", timeout=4 * (self.args.gap + 2) + 10)
        self.focus(self.home)
        if not pid or self.pid(self.args.pkg) != pid:
            raise RuntimeError("Launcher process changed during navigation")
        metrics.update(self.gfx(self.args.pkg))
        if not metrics["frames"]:
            raise RuntimeError("No frames rendered during navigation")
        metrics["launcher_after_kib"] = self.memory("after", self.args.pkg)
        metrics["launcher_graphics_kib"] = counter((self.target / "after-meminfo.txt").read_text(), "Graphics")
        metrics["available_kib"] = counter(self.shell("cat", "/proc/meminfo", name="system.txt"), "MemAvailable")
        self.screenshot("end.png")
        return metrics

    def idle(self):
        self.focus(self.home)
        pid = self.pid(self.args.pkg)
        if not pid or not pid.isdecimal():
            raise RuntimeError("Idle measurement needs one launcher PID")
        before = self.memory("before", self.args.pkg)
        self.shell("dumpsys", "gfxinfo", self.args.pkg, "reset")
        count = self.args.idle_seconds // 15 + 1
        report = self.shell("top", "-b", "-n", count, "-d", 15, "-p", pid,
                            name="cpu.txt", timeout=self.args.idle_seconds + 30)
        self.focus(self.home)
        if self.pid(self.args.pkg) != pid:
            raise RuntimeError("Launcher process changed during idle")
        cpu = idle_cpu(report, pid)
        return {**self.gfx(self.args.pkg), "launcher_before_kib": before,
                "launcher_after_kib": self.memory("after", self.args.pkg),
                "cpu_one_core_pct": statistics.median(cpu) if cpu else None, "cpu_samples": len(cpu)}

    def video_surfaces(self, package, tag):
        layers = self.shell("dumpsys", "SurfaceFlinger", "--list", name=f"surfaces-{tag}.txt",
                            optional=True)
        surfaces = [layer for layer in layers.splitlines()
                    if layer.startswith("SurfaceView") and f"{package}/" in layer]
        return {layer: latest_present_ns(self.shell("dumpsys", "SurfaceFlinger", "--latency", layer,
                                                   name=f"surface-{tag}-{index}.txt", optional=True))
                for index, layer in enumerate(surfaces)}

    def app_journey(self, component, *, uri=None, dwell=5, arrows=(), warmup=False):
        package = component.split("/")[0]
        self.focus(self.home)
        launcher_pid = self.pid(self.args.pkg)
        intent = ("-a", MAIN, "-c", LEANBACK, "-f", "0x10200000")
        if uri:
            intent = ("-a", "android.intent.action.VIEW", "-t", "video/mp4", "-d", uri)
            if package == "org.videolan.vlc":
                intent += ("--ez", "from_start", "true")
        start = self.shell("am", "start", "-W", "-n", component, *intent, name="launch.txt")
        if not re.search(r"^Status: ok\s*$", start, re.M):
            raise RuntimeError("Android did not report a successful foreground app start")
        self.focus(package=package, timeout=8)
        if not warmup:
            if uri:
                time.sleep(3)
                self.focus(package=package)
                self.shell("dumpsys", "media_session", name="playback-before.txt")
                video_before = self.video_surfaces(package, "before")
            self.screenshot("start.png")
        if arrows:
            self.shell("dumpsys", "gfxinfo", package, "reset")
            browsing_started = time.monotonic()
        for key in arrows:
            self.focus(package=package)
            self.shell("input", "keyevent", key)
            time.sleep(self.args.gap)
        browsing_ms = round((time.monotonic() - browsing_started) * 1000) if arrows else None
        deadline = time.monotonic() + dwell
        while time.monotonic() < deadline:
            self.focus(package=package)
            time.sleep(min(5, max(0, deadline - time.monotonic())))
        self.focus(package=package)
        metrics = {}
        if not warmup:
            launch = re.search(r"^LaunchState:\s*(\w+)", start, re.M)
            metrics = {
                "launch_state": launch[1] if launch else None,
                "app_launch_ms": counter(start, "TotalTime") or None,
                "launcher_background_kib": self.memory("background", self.args.pkg),
                "app_foreground_kib": self.memory("foreground", package),
                "available_kib": counter(self.shell("cat", "/proc/meminfo", name="system.txt"), "MemAvailable"),
            }
            if arrows:
                metrics["files_walk_ms"] = browsing_ms
                metrics.update({f"files_{field}": value
                                for field, value in self.gfx(package, "files-frames.txt").items()})
                if not metrics["files_frames"]:
                    raise RuntimeError("Files rendered no frames during browsing")
            self.screenshot("end.png")
            if uri:
                self.shell("dumpsys", "media_session", name="playback-after.txt")
                self.shell("dumpsys", "media.codec", name="codecs.txt", optional=True)
                video_after = self.video_surfaces(package, "after")
                advances = [video_after[layer] - time_ns for layer, time_ns in video_before.items()
                            if time_ns and video_after.get(layer, 0) > time_ns]
                screenshots_changed = ((self.target / "start.png").read_bytes()
                                       != (self.target / "end.png").read_bytes())
                if not advances and not screenshots_changed:
                    raise RuntimeError("Video fixture showed neither image changes nor frame progress")
                metrics["video_surface_advance_ms"] = round(max(advances) / 1e6) if advances else None
                metrics["video_screenshots_changed"] = screenshots_changed
        self.shell("dumpsys", "gfxinfo", self.args.pkg, "reset")
        self.focus(package=package)
        started = time.monotonic()
        self.shell("input", "keyevent", 3)
        self.focus(self.home, timeout=12)
        metrics["home_focus_ms"] = round((time.monotonic() - started) * 1000)
        time.sleep(self.args.settle)
        self.focus(self.home)
        if not warmup:
            metrics.update(self.gfx(self.args.pkg))
            metrics["launcher_returned_kib"] = self.memory("returned", self.args.pkg)
            metrics["launcher_restarted"] = launcher_pid != self.pid(self.args.pkg)
        return metrics


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compare", nargs="+", type=Path, help="Compare complete ABBA or BAAB run directories")
    parser.add_argument("--pkg", default="com.mitun.sonora")
    parser.add_argument("--activity", default=".HomeActivity")
    parser.add_argument("--label")
    parser.add_argument("--serial", default=os.getenv("SONORA_TV"))
    parser.add_argument("--adb", default=os.getenv("SONORA_ADB") or shutil.which("adb")
                        or str(Path.home() / "Android/Sdk/platform-tools/adb"))
    parser.add_argument("--out", type=Path, help="New output directory")
    parser.add_argument("--block", help="Pairing label such as ABBA-01")
    parser.add_argument("--side", choices=("A", "B"))
    parser.add_argument("--slot", type=int, choices=range(1, 5))
    parser.add_argument("--profile", choices=("default", "loaded"), default="default")
    parser.add_argument("--layout", help="Catalogue, pins, wallpaper and motion settings")
    parser.add_argument("--workloads", nargs="+", choices=WALKS, default=list(WALKS))
    parser.add_argument("--trials", type=int, default=3)
    parser.add_argument("--gap", type=float, default=0.45)
    parser.add_argument("--settle", type=float, default=6)
    parser.add_argument("--skip-idle", action="store_true", help="Warm navigation only")
    parser.add_argument("--idle-seconds", type=int)
    parser.add_argument("--app", action="append", default=[], help="TV app package for warm open and HOME")
    parser.add_argument("--video", nargs=2, metavar=("COMPONENT", "LOCAL_URI"))
    parser.add_argument("--files", nargs=2, metavar=("COMPONENT", "ARROWS"),
                        help="Fixture open in Files, followed by a string of U/D/L/R inputs")
    parser.add_argument("--files-dir", help="Absolute device path to the Files fixture")
    parser.add_argument("--loaded-smoke", action="store_true", help="One short loaded cycle for setup verification")
    args = parser.parse_args()
    if args.compare:
        try:
            compare(args.compare)
        except (ValueError, OSError, KeyError) as error:
            parser.error(str(error))
        return 0
    if not args.serial:
        parser.error("Specify --serial or SONORA_TV")
    args.label = args.label or args.pkg.rsplit(".", 1)[-1]
    if args.trials < 1 or not 0 <= args.gap <= 60 or not 0 <= args.settle <= 60:
        parser.error("Trials must be positive, and gap and settle must be between 0 and 60 seconds")
    if len(set(args.workloads)) != len(args.workloads) or len(set(args.app)) != len(args.app):
        parser.error("Each navigation workload and foreground app must appear once")
    if any((args.block, args.side, args.slot)) and not all((args.block, args.side, args.slot)):
        parser.error("Specify --block, --side and --slot together")
    if args.profile == "loaded":
        if not args.layout or args.skip_idle:
            parser.error("Loaded profile requires --layout and idle measurement")
        if bool(args.files) != bool(args.files_dir):
            parser.error("Use --files and --files-dir together")
        if args.loaded_smoke and not (args.video or args.files):
            parser.error("Loaded smoke requires a video or Files fixture")
        if not args.loaded_smoke and not (args.video and args.files):
            parser.error("Full loaded profile requires both video and Files fixtures")
    if args.profile != "loaded" and (args.video or args.files or args.files_dir or args.loaded_smoke):
        parser.error("Fixture journeys require --profile loaded")
    if args.video and not args.video[1].startswith("file:///"):
        parser.error("Video must be a local file URI")
    if args.files_dir and (not args.files_dir.startswith("/") or args.files_dir == "/"):
        parser.error("Files fixture needs a dedicated absolute device directory")
    if args.files and not re.fullmatch("[UDLR]+", args.files[1]):
        parser.error("Files arrows must be a non-empty string of U/D/L/R")
    min_idle = 300 if args.profile == "loaded" and not args.loaded_smoke else 15
    if args.idle_seconds is None:
        args.idle_seconds = min_idle if args.profile == "loaded" else 60
    if args.idle_seconds < min_idle or args.idle_seconds % 15:
        parser.error("Idle duration must be a multiple of 15 seconds and meet the profile minimum")

    if args.out:
        directory = args.out.resolve()
        try:
            directory.mkdir(parents=True)
        except OSError as error:
            parser.error(str(error))
    else:
        date = ROOT / "docs" / "perf" / datetime.date.today().isoformat()
        date.mkdir(parents=True, exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix="bench-", dir=date))
    print(f"Raw evidence: {directory}", flush=True)
    with (directory / "run.json").open("w") as stream:
        json.dump(vars(args), stream, indent=2, default=str)

    capture = Capture(args.adb, args.serial, directory, args)
    ready = False
    error_message = None
    try:
        if capture.call("get-state").strip() != "device":
            raise RuntimeError("ADB device is unavailable")
        if capture.resolve("-a", MAIN, "-c", HOME) != capture.home:
            raise RuntimeError("Set the tested launcher as the device's default Home before running")
        capture.focus(capture.home, timeout=6)
        ready = True
        capture.metadata()
        if args.profile == "loaded":
            capture.fixtures()
        settings = None if args.skip_idle else capture.resolve("-a", "android.settings.SETTINGS")
        for trial in range(1, args.trials + 1):
            offset = (trial + (args.slot or 1) - 2) % len(args.workloads)
            for name in args.workloads[offset:] + args.workloads[:offset]:
                capture.sample(f"{name}-{trial:02}", lambda: capture.walk(name, settings))
        if not args.skip_idle:
            for package in args.app:
                component = capture.resolve("-a", MAIN, "-c", LEANBACK, "-p", package)
                if component.split("/")[0] != package or component == capture.home:
                    raise ValueError(f"Expected an external TV app for {package}")
                capture.sample(f"{package}-warmup", lambda: capture.app_journey(component, warmup=True))
                for trial in range(1, args.trials + 1):
                    capture.sample(f"{package}-{trial:02}", lambda: capture.app_journey(component))
            if args.profile == "loaded":
                keys = dict(U=19, D=20, L=21, R=22)
                cycles, dwell = (1, 15) if args.loaded_smoke else (6, 300)
                for trial in range(1, cycles + 1):
                    if args.video:
                        player, uri = args.video
                        capture.sample(f"playback-{trial:02}", lambda: capture.app_journey(
                            component_name(player), uri=uri, dwell=dwell))
                    if args.files:
                        files, arrows = args.files
                        capture.sample(f"files-{trial:02}", lambda: capture.app_journey(
                            component_name(files), arrows=[keys[key] for key in arrows]))
            capture.sample("idle", capture.idle)
        capture.shell("dumpsys", "thermalservice", name="thermal-after.txt", optional=True)
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError, KeyboardInterrupt) as error:
        error_message = str(error) or type(error).__name__
    finally:
        if ready:
            try:
                capture.shell("am", "start", "-n", capture.home, name="restore-home.txt")
                capture.focus(capture.home, timeout=6)
            except (RuntimeError, OSError, subprocess.SubprocessError) as error:
                capture.record("restore-home", {"status": "aborted", "reason": str(error)})
                error_message = error_message or str(error)
    if error_message:
        capture.record("run", {"status": "aborted", "reason": error_message})
        print(f"Benchmark aborted: {error_message}", file=sys.stderr)
        return 1
    capture.record("run", {"status": "ok"})
    return 0


if __name__ == "__main__":
    sys.exit(main())
