# A/B benchmarking — method

How to compare two builds (or Sonora vs another launcher) without fooling
yourself. Distilled from cleanyourtv and the 2026-11-04 / 2026-11-05 runs.

## Setup, identical for both sides

1. Reboot the TV. Let it settle 60 s untouched.
2. Same debloated system state every run — only the thing being measured
   may differ. Record package enable/disable changes.
3. Install the perf build (`scripts/deploy.sh` default). Never compare a
   debug build: it runs uncompiled and misleads. After install,
   `cmd package compile -m speed -f <pkg>` (deploy.sh already does this).
4. Record the build's commit hash in the write-up.

## The run

```
python3 scripts/bench.py --label <name>
```

bench.py does the protocol: `/proc/meminfo` + app meminfo, force-stop,
`am start -W` cold start, settle, `gfxinfo reset`, the fixed 12-key DPAD
walk at 0.45 s/key, then gfxinfo + meminfo dumps saved raw to
`docs/perf/<date>/<label>-*.txt`.

Run each side at least twice. If two runs of the same side disagree by
more than ~10%, the measurement is dirty — discard and redo.

## What to compare

- **Deadline missed %** and **p95/p99 frame times** — the honest jank
  numbers.
- **TOTAL PSS** at idle and after the walk, and **MemAvailable** from the
  system dump.
- **Cold-start TotalTime** from `am start -W`.

## What to ignore

- **Janky frames %** counts `adb shell input` latency — it reads high no
  matter what the app does. Use deadline-missed instead.
- The 4950 ms GPU percentile bucket appears regardless of app; ignore it.
- Frame counts differ between launchers (different grids → different
  scroll distances). Rates and percentiles are comparable, totals are not.
- MemAvailable/swap has real boot-to-boot variance; treat small deltas as
  suggestive, not proven.

## Write it up

One `docs/perf/YYYY-MM-DD.md` per session: method, builds, system state,
the table, what the numbers mean, caveats. Raw dumps stay in the dated
directory next to it. See `docs/perf/2026-11-05.md` for the format.
