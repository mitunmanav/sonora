# Contributing

Sonora is written and maintained by one person. Bug reports and device reports are welcome as issues; pull requests
are not merged. If you have a fix, describe it in an issue and it will be made in a commit of the maintainer's own.

## A useful bug report

- The TV or box, its Android version and its RAM (Settings → Device Preferences → About).
- What you pressed, what you expected and what happened.
- For a crash: `adb logcat -d | grep -A 20 'FATAL EXCEPTION'`.
- For a slow screen: the tier Sonora picked (Settings → Mode → Technical, then Performance).

## Building

Linux or WSL, JDK 17. The commands are in the [README](README.md#build-from-source). Read [`AGENTS.md`](AGENTS.md) for
the rules the code keeps: no dependencies, no background work, no outbound network, `minSdk 23`, and a low-memory
answer for every costly effect.
