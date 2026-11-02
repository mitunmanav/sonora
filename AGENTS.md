# Sonora — working rules

## Always explore with CodeGraph first

Before you read, grep or edit anything in this project, **explore it with the
CodeGraph MCP**. One call returns the verbatim source of the symbols you name,
the call paths between them, and a blast-radius list of everything that depends
on them — the three things grep cannot give you.

```
codegraph_explore({
  projectPath: "/home/mitun/aerial",
  query: "HomeActivity buildGrid moveFocus AppOrdering order"
})
```

Shell fallback (same output):

```bash
codegraph explore "HomeActivity buildGrid moveFocus"
```

Rules:

- **Ask CodeGraph before you touch a file.** Do not guess how a file connects
  to the rest of the app; ask, and read the source it returns.
- **Check the blast radius** it prints before you change a shared symbol.
  `Prefs` has 32 callers, `Motion` has 13, `ScreenStack` has 4 — a change to
  any of them is a change to every one of those call sites.
- **Use grep only for what CodeGraph did not return** (string literals in
  resources, files with no symbols).
- **Re-index after every major change, before you read again.** A graph that
  is behind the code will happily show you the old shape of the app, and you
  will then "fix" something that was already changed:

  ```bash
  codegraph sync /home/mitun/aerial   # after each change
  codegraph index /home/mitun/aerial  # full rebuild, when sync is not enough
  ```

  Run it right after a batch of edits, then explore again. Never read a file
  from memory of an earlier call: read it again from the fresh graph.

## The rules the code has to keep

- **Zero dependencies.** No AndroidX, no Compose, no third-party libraries.
  Platform APIs only. That is why `ui/Spring.java` solves the spring by hand.
- **No background work.** No Service except the DreamService, no receivers, no
  alarms, no WorkManager. The LAN server runs on a thread inside a visible
  screen and dies with it.
- **No network** unless the browser or the transfer client is switched on.
  No analytics, no ads, no trackers, ever.
- **`minSdk 23`.** Never raise it.
- **Pure logic lives outside the Activity** and is unit-tested: `Grid`,
  `AppOrdering`, `AppSearch`, `BackPolicy`, `ui/Spring`.
- **Say what a device cannot do** in the UI. Never ship a fake feature.

## Agent skills

### Issue tracker

Issues and specs are local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles use their default strings, recorded as `Status:` lines. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` at the root, ADRs in `docs/adr/`. See `docs/agents/domain.md`.
