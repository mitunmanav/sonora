# 04. Docs name paths that do not exist
Status: resolved
Type: task
Spec: A5

- `AGENTS.md`: the codegraph line names another machine's path; say "the repo root" and that it is optional where codegraph is not installed.
- `docs/ARCHITECTURE.md`: drop `.scratch/benchmark-scripts/`, list `bench.py` and `perf-ab.md`.
- Move `.devin/skills/docs-sync/` to `.agents/skills/docs-sync/` beside the other skills; update both references.

Accept: every path a doc names exists.

## Answer

AGENTS.md codegraph line names the repo root; ARCHITECTURE lists bench.py and perf-ab.md and the real .scratch efforts; docs-sync moved to .agents/skills/.
