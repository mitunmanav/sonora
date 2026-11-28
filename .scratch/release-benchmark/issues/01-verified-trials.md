# Capture verified and replayable benchmark trials

Status: ready-for-human

Implement the guarded capture suite from [the spec](../spec.md) in
`scripts/bench.py`. Keep the existing package, label, settle, gap and activity
options. Verify exact foreground Home, cold process absence, bounded waits,
sample evidence and a rerun's output isolation. Test parsing against malformed
input and captured Mi TV data before the live smoke test.

## Outcome

Implemented the capture suite and verified cold launch, three navigation
walks, Files and VLC entry, HOME returns and idle on the Mi TV. Regression
tests cover the exact focused window and the formatting of Android's frame
and CPU output. Failed trials keep their partial evidence.
