# Run equivalent loaded journeys and paired release blocks

Status: ready-for-agent

Extend [the spec](../spec.md) with reproducible ordering and per-device
comparisons, offline video playback with actual media progress, a deterministic
Files fixture and endpoint checks, and independent idle and Perfetto evidence.
Keep optical latency and target hardware coverage explicit. No release claim
until the required hardware and fixture runs are attached.

## Outcome

The runner supports paired ABBA and BAAB summaries and verified fixture
checksums. The Mi TV completed an ABBA pilot for the grid transition. Full
loaded blocks, Files access to the fixture, Android 12+ FrameTimeline,
physical 1 GB coverage and optical measurement remain open.

The first playback trial opened VLC and started its hardware decoder, but both
ADB screenshots were black. A separate probe captured increasing presentation
timestamps on the video SurfaceView. Use that evidence for hardware video
planes and reject zero, pending or unchanged timestamps.

The updated playback smoke test verified advancing surface frames and HOME
return on the Mi TV. Shared storage permission remains the prerequisite for
the directory workload on this device.
