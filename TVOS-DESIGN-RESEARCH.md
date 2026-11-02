# Apple TV / tvOS design research for Sonora

Research snapshot: 31 October 2026. This note is based on Apple’s official Human Interface Guidelines, developer documentation, release information, design resources, and Apple TV 4K User Guide. It focuses on guidance that can improve Sonora’s Android TV launcher and Settings UI. Recommendations for Sonora are marked as inferences; Apple’s platform rules do not automatically become Android requirements.

## Findings to apply

### TV, distance, and layout

Apple describes TV use as remote-led and viewed from across a room. The tvOS layout guide specifies a safe area of 60 points at the top and bottom and 80 points at the sides, and asks designers to leave enough space for focus growth. It also warns that Apple TV layouts do not automatically adapt to different display sizes. [Designing for tvOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos/) · [Layout](https://developer.apple.com/design/human-interface-guidelines/layout)

**Sonora inference:** keep controls and text inside a conservative overscan-safe margin; size type for couch distance; check the largest and smallest supported TV layouts and focus scale before shipping. Preserve edge-to-edge background art, but keep interactive content away from cropped edges.

### Remote navigation and focus

tvOS uses directional focus: people move focus with remote directions, then press to activate. Apple recommends standard remote behavior, clear feedback, and Back to the parent screen. Its focus guidance describes visually distinct unfocused, focused, pressed/highlighted, selected, and unavailable states. Focused items lift, illuminate, and animate; rows and collections generally use a whole-row highlight. Focus should not jump without user input, except when a directional navigation target disappears. [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/) · [Remotes](https://developer.apple.com/design/human-interface-guidelines/remotes) · [Adding user-focusable elements to a tvOS app](https://developer.apple.com/documentation/uikit/adding-user-focusable-elements-to-a-tvos-app) · [SwiftUI cookbook for focus (WWDC23)](https://developer.apple.com/videos/play/wwdc2023/10162/)

**Sonora inference:** every actionable Android TV element needs a reachable D-pad focus path, visible focus that survives high contrast, and a separate OK action where moving focus alone would open a page. Test transitions across category rail, details, dialogs, empty states, and Android system settings that may not exist on a device. Restore focus to the originating item after returning from a child page.

### Materials, light, and visual hierarchy

tvOS 26 introduced Liquid Glass; Apple’s current guidance says focused standard buttons and controls take on this material on supported Apple TV 4K hardware. Glass belongs to a distinct control/navigation layer; standard materials remain useful for the content layer. Apple recommends enough opacity for legibility and restraint with color. [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass) · [Materials](https://developer.apple.com/design/human-interface-guidelines/materials) · [Apple’s tvOS 26 design announcement](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/) · [Apple TV redesign announcement](https://www.apple.com/newsroom/2025/06/apple-tv-brings-a-beautiful-redesign-and-enhanced-home-entertainment-experience/)

**Sonora inference:** use a dark content canvas and one floating focus surface. A restrained, high-contrast focused fill is a better Android approximation than stacking translucent cards or adding blur that the Android TV device cannot render cleanly. Keep the setting’s value legible in both focused and unfocused states.

### Motion and reduced motion

Apple says motion should communicate status or feedback, stay brief and precise, avoid distracting repetition, and remain cancellable. Motion must not be the sole way to communicate state; accessibility settings can alter system motion. [Motion](https://developer.apple.com/design/human-interface-guidelines/motion) · [Reduce screen motion on Apple TV 4K](https://support.apple.com/guide/tv/atvb1f949820/tvos)

**Sonora inference:** animate focus scale/elevation and short page transitions only; start from the current visual state so fast D-pad input remains responsive. Respect Android’s animator duration scale and keep a static focus treatment when animations are reduced or disabled.

### Type, wording, and accessibility

Apple lists 29 pt as the default and 23 pt as the minimum custom type size for tvOS, and encourages enlarging text by 200% where possible. Its writing guidance says TV text should be brief and visible from a distance; settings labels should use practical names and a short explanation when the label alone is unclear. Accessibility guidance includes VoiceOver, text size, contrast, reduce motion, and Switch Control. tvOS 27 adds Dynamic Text support across supported apps. [Typography](https://developer.apple.com/design/human-interface-guidelines/typography) · [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) · [Writing](https://developer.apple.com/design/human-interface-guidelines/writing) · [tvOS 27 release information](https://support.apple.com/en-us/106336) · [Apple TV accessibility guide](https://support.apple.com/guide/tv/accessibility-features-at-a-glance-tvos)

**Sonora inference:** use Android’s system sans rather than bundling Apple’s San Francisco; keep helper copy short, expose current values to TalkBack, use state text or a checkmark in addition to color, and test large system font scale, bold text, RTL, and screen-reader focus. Settings labels should describe the enabled effect (for example, “Show the date”), with values announced as “On” or “Off.”

### Icons and Sonora branding

Apple’s tvOS app icons use landscape rectangular layered artwork (800 × 480 px) and parallax, with a safe zone because focus enlargement can crop foreground layers. Apple encourages simple, recognizable art and forbids copying Apple hardware. The tvOS 18 design kit and production templates remain linked from Apple’s design resource page, but they are Apple-platform materials rather than Android assets. [App icons](https://developer.apple.com/design/human-interface-guidelines/app-icons) · [Images](https://developer.apple.com/design/human-interface-guidelines/images) · [Apple Design Resources](https://developer.apple.com/design/resources/)

**Sonora inference:** keep the new mark original, simple, and legible at launcher-icon size; use a vector source for Android density scaling and leave padding around the foreground so Android TV focus growth does not clip it. Do not use Apple logos, UI screenshots, or proprietary Apple artwork as Sonora branding.

### Settings organization and edge cases

Apple’s Settings HIG says to choose useful defaults, minimize settings, avoid duplicating system-wide preferences, keep task-specific controls near the task they affect, and group infrequently changed app preferences in a settings area. Apple says there is no single standard way to present app settings on tvOS; the system Settings app can host an app-specific page for infrequent settings, but that does not prescribe a custom in-app layout. [Settings HIG](https://developer.apple.com/design/human-interface-guidelines/settings) · [Adding a settings interface to your app](https://developer.apple.com/documentation/foundation/adding-a-settings-interface-to-your-app)

The current Apple TV 4K User Guide for tvOS 27 documents separate areas for appearance/screen savers, language/time, video/audio/subtitles, remote, storage, Wi‑Fi, accounts/profiles, app-specific settings, AirPlay/Home, Siri, privacy, restart/reset/update, and accessibility. It repeatedly cautions that options vary by hardware, display, region, and connected devices. For examples: [Apple TV 4K User Guide](https://support.apple.com/guide/tv/welcome-apd42d5243fc/tvos) · [Appearance settings](https://support.apple.com/guide/tv/atvb46af4f11/tvos) · [Screen saver settings](https://support.apple.com/guide/tv/atvbfa999b99/tvos) · [Video settings](https://support.apple.com/guide/tv/atvb553e7afe/tvos) · [Wi‑Fi settings](https://support.apple.com/guide/tv/atvb00f5b993/tvos) · [Accessibility features](https://support.apple.com/guide/tv/atvbaeff85db/tvos)

**Sonora inference / edge-case checklist:**

- Hide or accurately explain controls that the device cannot perform; do not draw a false “Apple TV feature” just to match the reference.
- System Settings intents can resolve differently across Android TV brands and versions; show a clear unavailable state if no matching screen exists, and do not leave focus stranded after an external settings screen returns.
- A setting may become unavailable when the launcher loses permissions, the system service is absent, no network is connected, the screen saver is disabled, or the TV changes configuration. Refresh its displayed value when the page resumes.
- Preserve focus after toggles, picker cancellation, mode changes, returning from a child page, and rebuilding the list. Handle no categories, hidden technical options, long/localized values, font scaling, RTL direction, and overscan.
- Keep dangerous actions and irreversible changes visually distinct; confirm only actions that cannot be readily undone.

## Public-source boundary

Apple publishes strong tvOS guidance for layout, remote focus, materials, motion, accessibility, and platform capabilities, plus user-guide descriptions of Settings categories. It does **not** publish a pixel specification or a complete design kit for the built-in tvOS Settings app’s category landing screen. Public screenshots can inform visual comparison, but treating their exact layout or artwork as a specification would be an inference. Sonora should reproduce the interaction principles and visual character while retaining its own brand and accurately reflecting Android TV capabilities.

## Source coverage

Official Apple pages reviewed: tvOS HIG overview; Layout; Focus and selection; Remotes; Motion; Materials; Color and Dark Mode; Typography; Accessibility; Images; App icons; Settings; Lists and tables; Toggles; Writing; tvOS developer resources and Liquid Glass adoption docs; tvOS 26 design announcements; tvOS 27 release information; and the tvOS 27 Apple TV User Guide sections for settings, remote navigation, screen savers, network, appearance, storage, Siri, privacy, reset/update, and accessibility. The current tvOS guide is the index for the individual settings articles; Apple Support rate-limited some article fetches during this survey, so those pages were not individually re-opened here.
