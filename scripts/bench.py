#!/usr/bin/env python3
"""Launcher benchmark runner, ported from cleanyourtv.

Implements the protocol recorded in docs/perf/2026-11-05.md: cold start,
idle memory (system + app), gfxinfo reset, a fixed DPAD walk, then gfxinfo
and meminfo dumps saved raw under docs/perf/<date>/. Compare builds with
two runs and docs/perf/perf-ab.md.

  scripts/bench.py                  measure com.mitun.sonora, label "sonora"
  scripts/bench.py --pkg com.google.android.tvlauncher --label googletv
  scripts/bench.py --skip-idle      walk only (app already warm)

Keys are sent with the same focus guard as scripts/tv.sh: the walk stops if
another app holds the window focus, so a miscount cannot open random apps.
"""
import argparse, datetime, os, re, subprocess, sys, time

TV = os.environ.get("SONORA_TV", "192.168.68.104:5555")
ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WALK = [22, 22, 22, 22, 20, 22, 22, 21, 21, 19, 21, 21]  # the 12-key walk
GAP = 0.45
SETTLE = 6


def sh(*args):
    return subprocess.run([ADB, "-s", TV, *args], capture_output=True, text=True).stdout


def focus_ok(pkg):
    return pkg + "/" in sh("shell", "dumpsys", "window")


def dump(pkg, name, outdir, label):
    data = sh("shell", "dumpsys", "gfxinfo", pkg) if name == "gfxinfo" else \
           sh("shell", "cat", "/proc/meminfo") if name == "meminfo-system" else \
           sh("shell", "dumpsys", "meminfo", pkg)
    path = os.path.join(outdir, f"{label}-{name}.txt")
    with open(path, "w") as f:
        f.write(data)
    return data


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--pkg", default="com.mitun.sonora")
    p.add_argument("--label", default=None)
    p.add_argument("--gap", type=float, default=GAP)
    p.add_argument("--settle", type=float, default=SETTLE)
    p.add_argument("--skip-idle", action="store_true")
    p.add_argument("--activity", default=".HomeActivity",
                   help="component inside --pkg to cold-start")
    a = p.parse_args()
    label = a.label or a.pkg.rsplit(".", 1)[-1]

    date = datetime.date.today().isoformat()
    outdir = os.path.join(ROOT, "docs", "perf", date)
    os.makedirs(outdir, exist_ok=True)

    subprocess.run([ADB, "connect", TV], capture_output=True)
    if sh("shell", "echo", "ok").strip() != "ok":
        sys.exit(f"cannot reach {TV} — check SONORA_TV / adb connect")

    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")  # a dream holds focus
    time.sleep(1)

    if not a.skip_idle:
        dump("", "meminfo-system", outdir, label)  # /proc/meminfo before touch
        dump(a.pkg, "meminfo-app", outdir, label)  # warm/idle PSS

    sh("shell", "am", "force-stop", a.pkg)
    t0 = time.time()
    start = sh("shell", "am", "start", "-W", "-n", a.pkg + "/" + a.activity)
    cold_ms = re.search(r"TotalTime: (\d+)", start)
    print(f"cold start: {cold_ms.group(1) + ' ms' if cold_ms else f'{time.time()-t0:.1f}s (wall)'}")
    time.sleep(a.settle)

    sh("shell", "dumpsys", "gfxinfo", a.pkg, "reset")
    for key in WALK:
        if not focus_ok(a.pkg):
            sys.exit(f"walk stopped, focus left {a.pkg}")
        sh("shell", "input", "keyevent", str(key))
        time.sleep(a.gap)

    gfx = dump(a.pkg, "gfxinfo", outdir, label)
    mem = dump(a.pkg, "meminfo-app-post", outdir, label)

    for pat in (r"Total frames rendered:.*", r"Janky frames:.*",
                r"Number Missed Vsync:.*", r"50th percentile:.*",
                r"95th percentile:.*", r"99th percentile:.*",
                r"Frame deadline missed:.*", r"Slow UI thread:.*"):
        m = re.search(pat, gfx)
        if m: print("  " + m.group(0).strip())
    m = re.search(r"TOTAL PSS:\s*(\d+)", mem)
    if m: print(f"  TOTAL PSS: {int(m.group(1))/1024:.1f} MB")
    print(f"raw dumps -> {os.path.relpath(outdir, ROOT)}/")


if __name__ == "__main__":
    main()
