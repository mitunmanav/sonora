# Fuse the grid blur and scrim into a single draw

Status: wontfix

Implement the pixel-equivalent composition in [the spec](../spec.md) as
an independent candidate. Compare the pure weights against two source-over
draws and build baseline and candidate in WSL. Validate on the Mi TV with
the upgraded benchmark. Restore and hash-check the original APK after A/B.

## Outcome

Built and measured four trials per side in an ABBA block on the Mi TV.
The fused draw missed 15 of 1,817 deadlines versus 13 of 1,815 and used
about 0.5 MiB more PSS after navigation. The runtime change was rolled back
and the original installed APK was restored and hash-checked.
[Performance report](../../../docs/perf/2026-11-28.md#grid-backdrop-ab) ·
[Reproducible patch](../experiments/01-one-backdrop-draw.patch)
