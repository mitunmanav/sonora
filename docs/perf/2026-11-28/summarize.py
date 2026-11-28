"""Summarize one launcher run from the unmodified Android dumps."""

import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path


def read_number(text, field):
    match = re.search(rf"^\s*{re.escape(field)}:\s*([0-9]+)", text, re.MULTILINE)
    if not match:
        raise ValueError(f"missing Android counter {field}")
    return int(match[1])


def report_samples(label, values, unit):
    print(f"{label}: median {statistics.median(values):g} {unit}, "
          f"range {min(values):g}–{max(values):g}, n={len(values)}")


for directory in map(Path, sys.argv[1:]):
    print(f"\n{directory.name}")
    print("workload,trial,frames,misses,miss_pct,p50_ms,p95_ms,p99_ms,gpu_p90_ms")
    for workload in ("mixed", "horizontal", "vertical"):
        paths = sorted(directory.glob(f"*-{workload}.frames.txt"),
                       key=lambda path: int(path.name.split("-", 1)[0]))
        for path in paths:
            trial = int(path.name.split("-", 1)[0])
            dump = path.read_text()
            frames = read_number(dump, "Total frames rendered")
            misses = read_number(dump, "Number Frame deadline missed")
            if not 0 <= misses <= frames or frames == 0:
                raise ValueError(f"invalid frame counters for {workload} trial {trial}")
            timing = [read_number(dump, field) for field in (
                "50th percentile", "95th percentile", "99th percentile", "90th gpu percentile"
            )]
            print(workload, trial, frames, misses, f"{100 * misses / frames:.2f}", *timing, sep=",")

    for state in ("idle", "after"):
        values = [read_number(path.read_text(), "TOTAL PSS") / 1024
                  for path in sorted(directory.glob(f"*-mixed.{state}.txt"))]
        if values:
            report_samples(f"Mixed {state} PSS", values, "MiB")
    cold = []
    for path in sorted(directory.glob("*-cold.txt")):
        dump = path.read_text()
        if (not re.search(r"^Status: ok\s*$", dump, re.MULTILINE)
                or re.search(r"^LaunchState: (HOT|WARM)", dump, re.MULTILINE)):
            print(f"Excluded launch sample {path.name}")
            continue
        value = read_number(dump, "TotalTime")
        if value > 0:
            cold.append(value)
    if cold:
        report_samples("Cold first frame", cold, "ms")
    steady = sorted(directory.glob("steady-idle-*.txt"))
    for path in steady:
        if path.stem.endswith(("-before", "-after")):
            print(f"{path.stem}: {read_number(path.read_text(), 'TOTAL PSS') / 1024:.1f} MiB")
        elif path.stem.endswith("-frames"):
            print(f"{path.stem}: {read_number(path.read_text(), 'Total frames rendered')}")
        elif path.stem.endswith("-cpu"):
            samples = [float(line.split()[8]) for line in path.read_text().splitlines()
                       if re.match(r"^\s*\d+\s+u\d+_a\d+\s", line)]
            if len(samples) > 1:
                report_samples("Steady idle CPU", samples[1:], "% of one core")

    app_samples = defaultdict(list)
    for path in sorted((directory / "apps").glob("*.home-focus.txt")):
        name = path.name.removesuffix(".home-focus.txt")
        app, trial = name.rsplit("-", 1)
        frames = path.parent / f"{name}.home-frames.txt"
        if trial == "0" or not path.read_text().strip() or not frames.is_file():
            continue
        start, end = map(float, path.read_text().split())
        app_samples[app, "HOME to focus", "ms"].append(round((end - start) * 1000))
        launch = (path.parent / f"{name}.launch.txt").read_text()
        if re.search(r"^LaunchState: HOT\s*$", launch, re.MULTILINE):
            app_samples[app, "hot app launch", "ms"].append(read_number(launch, "TotalTime"))
        launcher_pids = []
        for file, label, field in (
            ("launcher-background", "launcher behind app", "TOTAL PSS"),
            ("app-foreground", "foreground app", "TOTAL PSS"),
            ("launcher-returned", "launcher after HOME", "TOTAL PSS"),
            ("system-memory", "system available during app", "MemAvailable"),
        ):
            dump = (path.parent / f"{name}.{file}.txt").read_text()
            if file in ("launcher-background", "launcher-returned"):
                match = re.search(r"MEMINFO in pid (\d+)", dump)
                launcher_pids.append(match[1] if match else None)
            if "No process found" in dump:
                print(f"{name} {label}: process was absent")
                continue
            app_samples[app, label, "MiB"].append(read_number(dump, field) / 1024)
        if launcher_pids[0] != launcher_pids[1]:
            print(f"{name}: launcher process changed during return")
        dump = frames.read_text()
        app_samples[app, "HOME return frames", "frames"].append(read_number(dump, "Total frames rendered"))
        app_samples[app, "HOME return misses", "misses"].append(read_number(dump, "Number Frame deadline missed"))
    for (app, label, unit), values in app_samples.items():
        report_samples(f"{app} {label}", values, unit)
