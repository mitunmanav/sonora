# Cheaper composition during the dock-to-grid move

The saved Mi TV traces localize most Sonora deadline misses to the vertical
transition. The previous prototype removed the wallpaper early and visibly
changed the transition by design. Test a smaller change that retains the
wallpaper, blur, scrim, animation curve and dock behavior.

The grid backdrop currently draws an opaque blur followed by a solid scrim.
During the fade Android modulates both draws by view alpha. With progress
`a` and scrim opacity `s`, the two draws contribute `a * (1 - s*a)` of
the blur and `s*a` of the scrim. Use their sum as a single draw opacity,
and tint the small blur by the scrim's share of that sum. Allow up to two
8-bit levels of rounding error per RGB channel. Keep the raw blur available
to other views. Avoid screen-sized buffers and keep the same API 23 path.

Unit-test both themes and the fallback. Verify endpoints, in-flight frames,
reversals, saver visibility and Reduce Motion. Use the same source revision,
layout, ART mode and APK signer on both sides of a vertical ABBA run. Track
deadline misses, p99, wallpaper appearance and graphics PSS. The experiment
graduates only if it improves frame timing without a visible or memory
regression. Preserve the previous Home APK and keep this patch independent
of the benchmark changes.
