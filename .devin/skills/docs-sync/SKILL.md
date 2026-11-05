---
name: docs-sync
description: Keep Sonora's documentation in sync after code, layout or requirement changes. Use at the end of any session that changed files, and whenever asked to update docs.
---

# docs-sync

Sonora's docs are a system, not a pile. Each file has one job; update the
one that owns the fact you changed. Never rewrite history — append.

## Which file owns what

| File | Owns | Rule |
|---|---|---|
| `README.md` | Human overview: modules, build/test commands, doc map | Update when modules, build files or top-level layout change |
| `AGENTS.md` | Agent working rules | Update when a rule, layout path or convention changes |
| `GLOSSARY.md` | Domain vocabulary | Add a term when a new domain concept appears; never invent synonyms |
| `docs/plans/PLAN.md` | Running session log | **Append** a numbered `## N. Session:` section; never edit old sections |
| `docs/plans/REQUIREMENTS-*.md` | Dated owner requirements | Update the `## Status` table when a requirement's state changes |
| `docs/handoffs/HANDOFF-*.md` | Dated session snapshots | Write a new dated file for a new handoff; add a dated `>` note at the top of the previous one pointing forward |
| `docs/perf/YYYY-MM-DD.md` | Measured numbers | One file per measurement date; always record device, method and what was NOT measured |
| `docs/research/` | Reference teardowns | Append findings; keep "measured, not copied" notes |
| `docs/agents/` | Tracker/domain conventions | Rarely changes |

## Checklist before finishing

1. `git status` — anything uncommitted that should be committed or explained?
2. New/changed/removed modules or scripts → `README.md` + `AGENTS.md` layout.
3. New domain noun → `GLOSSARY.md`.
4. Work done → append a `PLAN.md` session section: what changed, what was
   verified (TV? tests? build?), what is deliberately different, what is
   still open.
5. Owner requirement touched → status table in the requirements file.
6. Numbers measured → `docs/perf/` with method and device.
7. Dead code found → remove it and say so in the session entry.
8. Handoff older than the newest commit → add the forward-pointer note.

## Style

- Match the existing voice: short declarative sentences, say what a device
  cannot do, no marketing.
- Dates are `YYYY-MM-DD`. Commits are referenced by short hash.
- If a doc and the code disagree, the code wins — fix the doc.
