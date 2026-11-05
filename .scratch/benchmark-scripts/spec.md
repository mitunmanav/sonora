# Benchmark scripts port

Status: needs-triage

Port `bench.py` and `perf-ab.md` from cleanyourtv into `scripts/`, so the
measured performance workflow lives in this repo instead of a sibling project.

## Scope

- `scripts/bench.py` — the frame/jank benchmark runner used for the
  2026-11-05 measurements (DPAD walk, gfxinfo, meminfo dumps).
- `perf-ab.md` — the A/B comparison method for before/after builds.
- Keep `scripts/perf.sh` as the build+install entry point; bench.py is the
  measurement half.
- Raw dumps keep going to `docs/perf/YYYY-MM-DD/` per docs-sync.

## Out of scope

- Changing what is measured. The protocol is already recorded in
  `docs/perf/2026-11-05.md`.

## Open questions

- Where do bench.py/perf-ab.md live in cleanyourtv today — are they current?
