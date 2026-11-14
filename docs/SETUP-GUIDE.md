# Sonora Setup Guide

## What is Sonora?

Sonora is a lightweight Android TV launcher designed as a clean, fast counterpart to Apple TV/tvOS. It removes ads, tracking, and bloatware while providing a polished, fluid experience.

## Requirements

- Android TV device (Android 7.0+ / API 23+)
- 1 GB RAM minimum (targeted for low-end devices)
- ADB access for installation

## Installation

### Method 1: ADB Installation (Recommended)

1. Enable ADB on your TV:
   - Go to Settings > Device Preferences > About
   - Scroll to "Build" and click 7 times
   - Go back to Settings > Device Preferences > Developer options
   - Enable "USB debugging"

2. Connect your TV to your computer:
   ```bash
   adb connect <TV_IP_ADDRESS>:5555
   ```

3. Install Sonora:
   ```bash
   adb install -r aerial/app/build/outputs/apk/release/app-release.apk
   ```

4. Set Sonora as the launcher:
   - Go to Settings > Device Preferences > Home screen
   - Select "Sonora" as the home launcher

### Method 2: APK Installation

1. Copy `app-release.apk` to a USB drive
2. Plug the USB drive into your TV
3. Use a file manager to locate and install the APK
4. Set Sonora as the home launcher in settings

## First-Time Setup

### Grant Permissions

Sonora asks for minimal permissions:

- **Storage**: For file manager and photo screensaver
- **Usage Access**: For app switcher (optional, shows more recent apps)

To grant usage access:
1. Open Sonora
2. Go to Settings
3. Find "App switcher" section
4. Select "Allow usage access"
5. Enable Sonora in the system usage access settings

### Configure the Home Screen

1. **Arrange Apps**
   - Long-press any app tile
   - Use D-pad to move
   - Press BACK to finish

2. **Add Apps**
   - Go to Settings > Apps
   - Install apps from Play Store
   - They appear on the home grid automatically

3. **Remove Apps**
   - Go to Settings > Apps
   - Select an app and choose "Uninstall"

### Configure the Screensaver

1. Go to Settings > Screen Saver
2. Select "Sonora Dream" as the screen saver
3. Choose photo sources:
   - Device storage
   - Custom folder

4. Adjust timing:
   - When to start (5-60 minutes)
   - Transition speed

### Configure the App Switcher

The app switcher opens when you double-press the HOME button.

**Without usage access**:
- Shows apps you've opened this session
- Closes when Sonora restarts

**With usage access**:
- Shows all recently used apps (last 24 hours)
- Persists across restarts
- More complete recents list

## Key Features

### Double-Press HOME = App Switcher
- Press HOME twice quickly to see recent apps
- Navigate with D-pad
- Press OK to switch to an app
- Long-press OK to force-close an app
- Press BACK to close the switcher

### Control Center
- Press the menu button (if available) or configure in settings
- Quick access to:
  - WiFi
  - Bluetooth
  - Display brightness
  - System settings

### Search
- Press the search button on your remote
- Search installed apps
- No network queries (local only)

### File Manager
- Access device storage
- Manage files
- Copy, move, delete files

## Troubleshooting

### Sonora is not the default launcher
- Go to Settings > Device Preferences > Home screen
- Select "Sonora"

### App switcher shows no apps
- Grant usage access (see above)
- Or launch some apps from Sonora first

### Screensaver is blank
- Add photos to device storage
- Configure photo source in Settings > Screen Saver

### Performance is slow
- Check Device Tier in Settings > Performance
- Disable "Technical" mode if on a low-end device
- Close background apps

### TV button does not open switcher
- Ensure Sonora is the default launcher
- Try pressing twice quickly (within 0.5 seconds)
- Check if the remote sends HOME keycode 3

## Tips

- **Memory**: Sonora uses ~44 MB idle, much less than stock launchers
- **Battery**: No background services, no wake locks
- **Privacy**: No analytics, no tracking, no Google Play Services
- **Updates**: Update via Play Store or install new APK

## Limitations

- No voice search (Google Assistant required)
- No recommendations (requires ML and history tracking)
- No live TV channels (planned for future version)
- No multi-user support (planned for future version)

These are intentional trade-offs for privacy, simplicity, and performance.

## Support

For issues or feature requests, check the project repository or documentation.

---

**Note**: Sonora is designed to replace the stock launcher completely. If you need to return to the stock launcher, go to Settings > Device Preferences > Home screen and select the original launcher.
