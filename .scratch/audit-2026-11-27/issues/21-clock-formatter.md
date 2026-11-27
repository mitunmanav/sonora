# 21. A date formatter per clock tick
Status: resolved
Type: task
Spec: D4

`GlassPillView` and `ControlCenter` keep their formatters and rebuild them only when the pattern or locale changes.

## Answer

ui/ClockText keeps one formatter per pattern and language; GlassPillView, ControlCenter and the saver overlay (which built one every frame) use it.
