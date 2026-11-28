# Clean Your TV source review

Reviewed the local checkout of Clean Your TV 3.6 at
`be6087056a1c7d4f08f3f1cc778d92374d86ef27`, the source version identified in the
[Mi TV comparison](../perf/2026-11-28.md).

- Clean Your TV gives its grid stable item IDs, fixes its outer size, and turns
  off the item animator. Card focus changes animate properties and temporarily
  layer the card on the ordinary motion path. The reduced-motion path skips the
  temporary layer. Sonora already animates RenderNode properties. Trial a
  temporary tile layer only if a trace shows a tile redraw bottleneck and the
  added graphics memory fits LOW. The current Mi TV evidence points to the
  wallpaper transition. [Grid configuration](https://github.com/Gathin23/cleanyourtv/blob/be6087056a1c7d4f08f3f1cc778d92374d86ef27/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java#L624-L681)
  · [Focus animation](https://github.com/Gathin23/cleanyourtv/blob/be6087056a1c7d4f08f3f1cc778d92374d86ef27/launcher/app/src/main/java/com/gathin/tvhome/AppAdapter.java#L960-L1002)
- Its ambient light stretches a 480×270 alpha texture and tints it on focus.
  Sonora already stretches a 96×54 blur. The applicable idea is to draw its
  tint with the texture in the grid rather than blend a separate full-screen
  rectangle for the scrim. The [grid composition spec](../../.scratch/grid-backdrop/spec.md)
  keeps the wallpaper crossfade and records the GPU and memory tradeoff.
  [Ambient texture and tint](https://github.com/Gathin23/cleanyourtv/blob/be6087056a1c7d4f08f3f1cc778d92374d86ef27/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java#L2142-L2196)
- Clean Your TV keeps artwork across a normal app switch. When a memory trim
  qualifies, it clears tile art, detaches the adapter, clears the recycled
  views and releases wallpaper video resources. Sonora already drops wallpaper
  on LOW when hidden. Measure returns after repeated foreground pressure before
  changing retention, with process restarts and memory recorded together.
  [Memory trim](https://github.com/Gathin23/cleanyourtv/blob/be6087056a1c7d4f08f3f1cc778d92374d86ef27/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java#L1363-L1382)
