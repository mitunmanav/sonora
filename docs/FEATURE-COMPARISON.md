# Sonora vs Google TV Launcher: Feature Comparison

## Google TV Launcher (com.google.android.tvlauncher v7.7.15)

### Core Features
- **App Grid**: Home screen with installed apps
- **Recommendations**: AI-suggested content based on viewing history
- **Live Channels**: TV inputs and streaming channels
- **App Discovery**: New apps from Play Store
- **Google Assistant Integration**: Voice search and commands
- **User Profiles**: Multiple user accounts
- **Watch History**: Track what has been watched
- **Settings**: Various TV settings (display, sound, network, etc.)

### Permissions and Dependencies
- QUERY_ALL_PACKAGES (full app list access)
- READ/WRITE_EPG_DATA (electronic program guide)
- MEDIA_CONTENT_CONTROL
- Google Play Services integration
- Analytics and ads

### Components
- 30+ activities
- Multiple services for content discovery
- Broadcast receivers for package changes
- TV input framework integration

---

## Sonora (com.mitun.sonora)

### Current Features
- **App Grid**: Home screen with launchable apps
- **App Switcher**: Double-press HOME to see recent apps (glass carousel)
- **Screensaver**: Photo slideshow (currently blank, needs content)
- **Settings**: Simple settings panel
- **Search**: Local app search
- **Browser**: Built-in web browser with ad blocking
- **Files**: File manager
- **Control Center**: Quick settings overlay

### Permissions
- Minimal: only essential permissions
- No Google Play Services
- No analytics or tracking
- No ads

### Design Principles
- Zero dependencies (platform APIs only)
- Lightweight (targets 1 GB devices)
- Fast performance (RenderNode animations)
- Apple TV/tvOS-inspired design
- Honest UI (no fake features)

---

## Feature Gap Analysis

| Feature | Google TV | Sonora | Priority | Notes |
|---------|-----------|--------|----------|-------|
| App Grid | ✅ | ✅ | - | Basic launcher function |
| App Switcher | ✅ | ✅ | - | Just implemented with glass effect |
| Screensaver | ✅ | ⚠️ | High | Exists but needs photo content |
| Recommendations | ✅ | ❌ | Low | Requires ML/history tracking |
| Live Channels | ✅ | ❌ | Medium | TV input framework |
| Voice Search | ✅ | ❌ | Low | Google Assistant dependency |
| App Discovery | ✅ | ❌ | Low | Play Store integration |
| User Profiles | ✅ | ❌ | Low | Multi-user support |
| Watch History | ✅ | ❌ | Low | Usage tracking |
| Settings | ✅ | ✅ | - | Simplified version |
| Network Settings | ✅ | ❌ | High | System settings link |
| Display Settings | ✅ | ❌ | High | System settings link |
| Sound Settings | ✅ | ❌ | High | System settings links |
| Analytics | ✅ | ❌ | - | Privacy feature |
| Ads | ✅ | ❌ | - | Privacy feature |
| Google Services | ✅ | ❌ | - | Debloated |

---

## Implementation Roadmap

### Phase 1: Essential Gaps (Complete Replacement)
1. **Screensaver Content**
   - Load photos from device storage
   - Add clock overlay
   - Settings to customize sources

2. **System Settings Links**
   - Quick access to Display settings
   - Quick access to Sound settings
   - Quick access to Network settings
   - Quick access to Bluetooth settings

3. **Logo and Icon Polish**
   - Sonora logo design
   - Consistent icon styling
   - 1:1 aspect ratio icons

### Phase 2: Enhanced Functionality
4. **TV Input Support**
   - HDMI input switching
   - Live TV channel browsing
   - Input source selection

5. **Package Management**
   - Install/uninstall apps
   - App info details
   - Storage management

6. **Content Discovery**
   - New apps section
   - Play Store integration (optional)

### Phase 3: Advanced Features
7. **User Profiles**
   - Multiple user support
   - Per-user app grid
   - Profile switching

8. **Watch History**
   - Optional usage tracking
   - Resume watching
   - Content recommendations (local ML)

---

## Design Decisions

### What We Keep from Google TV
- Basic launcher functionality
- TV input framework (for Phase 2)
- EPG data access (for live channels)

### What We Remove
- Google Play Services dependency
- Analytics and tracking
- Ads
- Remote configuration
- Over-the-air updates (use Play Store instead)

### What We Add
- Apple TV/tvOS design language
- Faster performance (RenderNode animations)
- Smaller memory footprint
- Privacy-first approach
- Built-in browser and file manager

---

## Performance Targets

- Idle memory: < 50 MB PSS (currently ~44 MB)
- Frame deadline misses: < 1% (currently ~0.9%)
- Cold start: < 1 second
- App switcher animation: smooth (30 fps+)
- Screensaver: < 5 ms per frame (currently met)

---

## Known Limitations

- No voice search (Google Assistant required)
- No recommendations (requires ML and history)
- No live channels (Phase 2)
- No multi-user (Phase 3)
- Usage access optional (app switcher shows less without it)

These are intentional trade-offs for privacy, simplicity, and performance.
