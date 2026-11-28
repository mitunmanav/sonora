# Android TV release benchmark

The existing runner checks for the package anywhere in the window dump, can
overwrite prior evidence, and reports unattended Home restarts as cold starts.
The [Mi TV baseline](../../docs/perf/2026-11-28.md) has the working procedures.

- Run mixed, horizontal and vertical D-pad walks from a verified Home activity.
  Capture cumulative frame counters, the frame timing tail, and memory.
- Verify process-cold first frames behind Settings. Capture idle CPU, frame count,
  PID and memory, and time common TV apps from entry through the HOME return.
- Record the APK hashes, build label, device and display, ART, thermal, package
  inventory and per-trial outcomes. Give every run its own directory. Keep
  partial evidence and fail a trial when focus or process invariants break.
- Compare paired ABBA and BAAB blocks by device and workload. A release needs
  ten trials per side and workload on each target device, with explicit gates
  and separate default and loaded profiles. Keep missing counters visible.
- The loaded profile adds offline playback, directory browsing and a five-minute
  soak. Use a known fixture and verify visual endpoints. Treat physical
  button-to-photon, full Perfetto FrameTimeline, and a 1 GB device as distinct
  evidence, since ADB counters and the Mi TV cannot provide those results.
  On the Mi TV the VLC video plane is black in ADB screenshots. Capture
  presentation timestamps from the same player SurfaceView before and after
  the interval, and require either advancing timestamps or changing images.

Keep the historical capture tools and raw evidence. Roll back the tool change
with a single patch without affecting TV state or the Android code.
