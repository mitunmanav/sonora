# Reduce wallpaper thumbnail blur work

Home, the Aerials picker and Tools call `BackdropView.soften` off the main
thread. Every cache miss computes three 5×5 box-blur passes over the 96×54
thumbnail. Neighboring windows recalculate the same channel sums.

Keep the 96×54 crop, clamped edges, opaque output, three passes and the same
integer division after each full 5×5 window. Maintain rolling column and
window sums so neighboring pixels reuse their taps. Use only a small
width-sized scratch buffer beyond the existing pixel arrays. Keep wallpaper
decoding, cache policy and the visual effect intact.

Verify pixel equality with the existing loop across deterministic inputs
and edge sizes. Measure the old and new blur kernels with identical input
and repeated timings in WSL. Report the speedup for the kernel specifically.
The wallpaper decode and first usable Home may have different bottlenecks.
