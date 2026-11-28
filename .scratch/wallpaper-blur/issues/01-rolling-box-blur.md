# Reuse channel sums during wallpaper thumbnail blur

Status: ready-for-human

Implement the rolling-window kernel in [the spec](../spec.md) and connect it
to the existing `BackdropView.soften` call. Compare each output pixel against
the original clamp and rounding behavior. Keep the change independent of
the experimental grid compositing patch so either can be reverted directly.

## Outcome

Implemented and verified pixel equality in WSL. The Mi TV ART microbenchmark
reduced median blur time from 7.87 ms to 1.46 ms for the 96×54 thumbnail.
The [performance report](../../../docs/perf/2026-11-28.md#wallpaper-thumbnail-blur)
records the method and the scope of the measurement.
