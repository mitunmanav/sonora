# Android TV benchmark

Run from WSL with an authorized ADB client. Set `SONORA_TV` to the device serial
and set `SONORA_ADB` when reusing the authorized Windows `adb.exe`. Start with
the launcher being tested as the default Home and in focus. Use a dedicated test
window. The runner opens Settings and the requested apps, records screenshots,
and returns to the tested Home. It performs no installs or preference changes.

```sh
python3 scripts/bench.py --label son-baseline --trials 3 \
  --app com.mitun.files --app org.videolan.vlc --out /tmp/son-baseline
```

Each run gets a new directory. It contains the options, installed APK hashes,
package and ART details, device and display state, thermal dumps, and JSON lines
for individual results or failures. Screenshot evidence brackets navigation
and foreground app workloads. ADB sends D-pad arrows from a device-side loop
that verifies the exact focused activity before each key and logs key uptime.
Raw frame dump tails can be shorter than cumulative Android frame counts.

The default profile measures process-cold Android first frame, mixed and
single-axis navigation, system memory and launcher PSS, and a one-minute idle
window. Each `--app` adds a prewarmed TV app entry and an actual HOME return
with approximate window-focus timing, app and launcher PSS, launcher PID
survival and return frame data. Missing counters are JSON null. Null and zero
are different results. Use `--skip-idle --workloads vertical` for a warm
transition pilot. Files and VLC entry timings start from a common intent.

## Paired release runs

1. Compile both packages with ART `speed`. Compare perf or release APKs.
   For an in-place swap, preserve and hash the original APK, check signing
   certificates, and verify the installed checksum and default Home after
   every switch. Keep the same display, apps, layout, wallpaper and motion
   settings when comparing two builds. Describe unavoidable launcher
   differences using `--layout`.
2. Decide the allowed regressions in frame time, memory, startup and app
   returns before collecting data. Use a physical 1 GB TV, the Mi TV and an
   Android 12 or newer TV. Run ABBA and BAAB across matched conditions.
   Use `--block ABBA-01 --side A --slot 1` through slot 4. Three trials per
   slot across both orders give 12 trials per side for each navigation
   workload, per device and profile. Navigation workload order rotates with
   slot and trial. Keep aborted evidence and rerun a complete matched block
   if a slot becomes unusable.
3. Use `python3 scripts/bench.py --compare DIR1 DIR2 DIR3 DIR4` to produce CSV.
   Each block reports median, range and available sample count for each side.
   The difference is B minus A using slot medians, which gives each slot
   equal weight. Compare per-device results and review screenshots for path
   differences before interpreting numeric deltas.
4. Record the protocol, device, raw evidence and result in
   `docs/perf/YYYY-MM-DD.md`. Include all declared metrics alongside any
   regressions, missing data and aborted samples.

## Loaded profile and physical evidence

Use the same installed app catalogue and fixture across slots. Record the
fixture tree, media checksum, start position and expected final selection.
Use a local MP4 longer than five minutes with visible frame changes. Match
network state and stage the Files fixture at the same initial focus.
Pass the video activity and local URI to `--video COMPONENT LOCAL_URI`, and
the Files activity and arrow recipe to `--files COMPONENT ARROWS`. Pass the
fixture directory as `--files-dir DEVICE_PATH` to hash its contents. The video
is hashed too. VLC receives the `from_start` intent extra.
Choose a Files recipe that visits the required rows and returns focus to its
starting item so the next cycle starts at the same selection.
Use `--profile loaded` and describe the device setup with `--layout`.
The runner captures six five-minute foreground video intervals interleaved
with Files browsing and Home returns, then checks idle for five minutes.
Use `--loaded-smoke` for one 15-second cycle and a short idle check. Smoke
runs can exercise either fixture independently. The Files fixture needs a
test device on which Files can already read the staged directory.
Review the screenshots alongside media session and surface frame data.
The runner accepts advancing timestamps on the same player SurfaceView
when the hardware video plane is black in ADB screenshots. Files must show
the expected fixture and selection. Report unverified fixtures as such.

Use independently verified task-equivalent journeys for real tile selection
and scrolling through the same app catalogue. On Android 12 and later, capture
separate Perfetto FrameTimeline runs to attribute misses. Use a synchronized
high-speed camera for button-to-photon and time to a usable grid. Treat these
as separate measurements from Android first frame and ADB window-focus time.
[Perfetto FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline)
· [Android benchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
