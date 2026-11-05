# Performance research: platform-level leanness for a zero-dependency Android TV launcher

Date: 2026-11-05. Scope: plain-Java/View-Canvas launcher, minSdk 23, target/compile 35, low-end TV boxes (1–2 GB RAM, 1080p). All claims cite primary sources only (developer.android.com, source.android.com, AOSP API diffs). Baseline: PSS ~32–42 MB, ~24 MB window buffers, ~8 MB wallpaper bitmap (HARDWARE on MID), ~13–15 MB graphics.

## 1. HWUI / RenderThread pipeline: what costs CPU vs GPU

- Android UI renders in two phases: **record** `View#draw` on the UI thread (runs `draw(Canvas)` on every invalidated view, including custom-view code), then **draw** on `RenderThread` (native thread, driven by recorded work). Custom `draw()` CPU cost lands on the UI thread; shader/fill/composite cost lands on RenderThread/GPU. — https://developer.android.com/topic/performance/vitals/render
- The same doc warns that some Canvas ops are cheap to record but expensive on RenderThread, and calls out `syncFrameState`/bitmap-upload stalls where UI and RenderThread block each other. — https://developer.android.com/topic/performance/vitals/render
- `Canvas.saveLayer` "is very expensive, incurring more than double rendering cost for contained content" — the docs' explicit replacement is a View hardware layer (`LAYER_TYPE_HARDWARE`) for xfermode/color-filter/alpha. — https://developer.android.com/reference/kotlin/android/graphics/Canvas
- `setLayerType(LAYER_TYPE_HARDWARE)` renders the view into an off-screen hardware texture. It pays off when the view's draw output is complex *and* changes only via cheap properties: "Some animations, such as alpha animations, can then be applied directly onto the layer, which is very efficient for the GPU to do." Costs: texture memory plus an extra composite pass, and the display list still re-records on every `invalidate()`. `LAYER_TYPE_SOFTWARE` forces CPU rendering of that subtree. — https://developer.android.com/develop/ui/views/graphics/hardware-accel
- Hardware acceleration itself is documented to consume **more RAM** than software rendering. — https://developer.android.com/develop/ui/views/graphics/hardware-accel
- The Profile GPU Rendering tool decomposes a frame into: Swap Buffers (CPU waiting on GPU — tall = GPU-bound/overdraw), Command Issue (2D renderer issuing display-list commands — proportional to display-list count), Sync & Upload (bitmap uploads — "a large segment indicates that the app is taking considerable time loading large amounts of graphics"), Draw, Measure/Layout. This is the right instrument for deciding whether remaining cost is CPU recording, GPU fill, or texture upload. — https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering

Implication for the launcher: D-pad focus springs on `translationX/scale/alpha` are already in the cheapest possible bucket (RenderNode property updates, no re-record, no redraw). A hardware layer would only help a *static, expensive* subtree that is never re-recorded; for a mostly-static launcher, `LAYER_TYPE_HARDWARE` on the wallpaper or a full-screen card trades ~one screen-sized texture for little gain — measure before adopting.

## 2. Bitmap memory

- Bytes-per-pixel table (developer.android.com): `ALPHA_8` = 1 (alpha only, "useful for masks" — cannot display color), `RGB_565` = 2 (no alpha), `ARGB_8888` = 4 (default), `RGBA_F16` = 8 (wide-gamut/HDR), `HARDWARE` = stored in graphics memory (gralloc/DMABuf). Formula: `W × H × bpp`; 1920×1080 ARGB_8888 ≈ 8.3 MB. — https://developer.android.com/topic/performance/memory/guide/bitmaps
- `Bitmap.Config.HARDWARE` (added API 26): "stored only in graphic memory… always immutable… optimal for cases when the only operation with the bitmap is to draw it on a screen." Docs list pros: doesn't use app or native heap; saves the GPU upload copy. Cons: immutable, CPU read-back is very expensive, harder to attribute in memory tooling. — https://developer.android.com/reference/android/graphics/Bitmap.Config , https://developer.android.com/topic/performance/memory/guide/bitmaps
- Since API 26 bitmap pixel data lives on the **native heap** (only a small wrapper on the Java heap); before 26 it was on the Dalvik heap. `getByteCount()` results can't be used for memory accounting since KitKat — use `getAllocationByteCount()`. — https://developer.android.com/topic/performance/graphics/manage-memory , https://developer.android.com/reference/android/graphics/Bitmap
- `BitmapFactory.Options.inBitmap` (API 11) reuses an existing bitmap's allocation when decoding; since KitKat any mutable bitmap works if the decoded byte count ≤ allocated byte count. The reused bitmap must be mutable (incompatible with HARDWARE config). — https://developer.android.com/reference/android/graphics/BitmapFactory.Options , https://developer.android.com/topic/performance/graphics/manage-memory
- `Bitmap.prepareToDraw()` (API 17+, async GPU upload since Nougat): calling it on a decode worker thread before first display "can save time in the first frame it's used." — https://developer.android.com/reference/android/graphics/Bitmap
- `ImageDecoder` (added API 28) returns **immutable HARDWARE bitmaps by default** (`ALLOCATOR_DEFAULT` "will typically result in a `Bitmap.Config.HARDWARE` allocation"); `ALLOCATOR_HARDWARE` forces it; `setMutableRequired(true)` is incompatible with hardware allocation; `setTargetSize()`/`setTargetSampleSize()` downsample during decode. — https://developer.android.com/reference/android/graphics/ImageDecoder
- `recycle()` guidance is legacy: docs recommend it mainly for pre-3.0; modern guidance is pooling/LruCache and recycling only when certain the bitmap is unused. — https://developer.android.com/topic/performance/graphics/manage-memory
- Cache sizing guidance: `LruCache` sized in **bytes** via `sizeOf()` override (e.g., 4 MiB example); factors: rest-of-app memory, images on screen, screen density, bitmap dims/config, access frequency, quality-vs-quantity tradeoff. Soft/WeakReference caches are explicitly not recommended. — https://developer.android.com/topic/performance/graphics/cache-bitmap , https://developer.android.com/reference/android/util/LruCache
- GPU memory note: hardware bitmaps "don't use application or native heap" — the bytes move to graphics/DMABuf accounting, so PSS may not drop even though total device memory pressure improves. Attribution in tools like AHAT is "harder." — https://developer.android.com/topic/performance/memory/guide/bitmaps

Implication: the 8 MB HARDWARE wallpaper is already in the right place. If the LOW tier uses a mutable ARGB_8888 bitmap for effects, `RGB_565` would genuinely halve it (~4 MB saved) — the earlier "no effect" result is consistent with the HARDWARE config being immutable/GPU-side, not with 565 being useless in general. `ALPHA_8` is only for masks/shadows, never for the wallpaper.

## 3. Window / SurfaceFlinger buffers

- Display surfaces use BufferQueues "configured for triple-buffering. **Buffers are allocated on demand, so if the producer generates buffers slowly enough… there might only be two allocated buffers** in the queue." Verify with `dumpsys SurfaceFlinger`. — https://source.android.com/docs/core/graphics/arch-sh
- SurfaceFlinger only accepts buffers between display refreshes (per-VSync), "which minimizes memory usage." — https://source.android.com/docs/core/graphics/surfaceflinger-windowmanager
- `Window.setFormat(int)` can override the window pixel format; `PixelFormat.TRANSLUCENT`/`TRANSPARENT`/`OPAQUE` are *logical* formats where "the system chooses" the real format. — https://developer.android.com/reference/android/view/Window , https://developer.android.com/reference/android/graphics/PixelFormat
- Gralloc picks the actual buffer format from **usage flags** (CPU access, GLES texture, video encoder…); for GPU-only surfaces the driver may choose its own ordering/format (e.g., BGRA). — https://source.android.com/docs/core/graphics/arch-bq-gralloc
- **No documented public API lets an app reduce its window BufferQueue's buffer count or force a smaller format.** Triple buffering is a SurfaceFlinger-side default; the ~24 MB (3 × 8.1 MB RGBA_8888 @1080p) is not app-reducible through documented APIs. Slow producers may only hold 2 buffers — i.e., the count is already demand-driven.

Implication: window-buffer memory is essentially fixed cost. The only lever is producing frames less often (already true at rest) or accepting that buffers shrink when idle — nothing to implement.

## 4. Launch / startup

- App-startup guidance: measure with Macrobenchmark; verify each startup operation is critical; defer non-essential resource loads; move long-running work off the startup path. — https://developer.android.com/topic/performance/appstartup/analysis-optimization
- Since Android 12 the system draws a splash screen from the launcher icon + `windowBackground` on cold/warm starts; a custom `windowBackground` theme is overridden by the system splash — i.e., keep the theme background trivial and let the first real frame come from your own view. — https://developer.android.com/develop/ui/views/launch/splash-screen/migrate
- For an always-installed launcher, cold start is rare; the wins are: theme `windowBackground` = solid color (drawn before first frame, no decode), all decode/bake off the main thread (already done), and no work in `Application.onCreate`/`Activity.onCreate` beyond wiring.

## 5. Overdraw, invalidation, view hierarchy vs single custom View

- Overdraw = drawing the same pixel more than once per frame; it "wastes GPU time… fill-rate" especially on low-end GPUs. Tools: Debug GPU Overdraw (color overlay), Profile GPU Rendering. Mitigations: remove fully-covered backgrounds, reduce transparency/layer count. Notably the doc says: "Although low-end devices continue to improve in GPU performance… we recommend instead focusing on optimizing UI thread work." — https://developer.android.com/topic/performance/rendering/overdraw
- The hardware-acceleration page gives a concrete budget: "a good rule of thumb with current hardware is to not draw more than 2.5 times the number of pixels on screen per frame (transparent pixels in a bitmap count!)." — https://developer.android.com/topic/performance/hardware-accel
- View-hierarchy cost is real and documented: measure/layout run per view, per frame when views are added/removed or request resize; deep hierarchies cost more. There is no documented "single custom View is always faster" claim, but eliminating the hierarchy removes per-view measure/layout passes and per-view display-list recording — the docs' own cost model supports it for a mostly-static screen. — https://developer.android.com/topic/performance/rendering/optimizing-view-hierarchies , https://developer.android.com/develop/ui/views/layout/improving-layouts/optimizing-layouts
- `RenderNode` + `RecordingCanvas` (both **added API 29**; `drawRenderNode` hardware-only): lets one custom View keep a tree of display lists that can be property-transformed "without re-recording the display list." `RecordingCanvas` holds strong refs to every Paint/Bitmap drawn until `endRecording()` — bitmaps referenced only by a recorded list can't be GC'd early. — https://developer.android.com/reference/android/graphics/RenderNode , https://developer.android.com/reference/android/graphics/RecordingCanvas , https://developer.android.com/sdk/api_diff/29/changes/pkg_android.graphics
- RenderNode is "used internally for all Views by default" — so the app's existing View-property animations already get RenderNode property updates on all API levels; the API-29+ direct API only adds manual display-list granularity for complex scenes. — https://developer.android.com/reference/android/graphics/RenderNode

Implication: for a full-bleed wallpaper + a row of focusable cards, a single custom `View` that records one static display list (wallpaper + card chrome) and animates card focus via RenderNode/View properties is aligned with every documented cost model. Overdraw should be audited with the Debug GPU Overdraw tool — translucent glass lenses over wallpaper are the main legitimate overdraw source.

## 6. Text rendering on Canvas

- `StaticLayout` doc: use it for laid-out text "unless you are implementing your own widget or custom display object, **or would be tempted to call `Canvas.drawText()` directly**" — i.e., raw `drawText` skips line-breaking/layout caching that widgets get for free. — https://developer.android.com/reference/android/text/StaticLayout
- `PrecomputedText` (API 28): pre-computes character metrics/glyph positions; "computing this on a background thread before your text will be presented can save work on the UI thread"; settable on `StaticLayout` to skip re-layout. — https://developer.android.com/reference/android/text/PrecomputedText
- Custom-drawing guidance: "Creating objects ahead of time is an important optimization. Views are redrawn frequently, and many drawing objects require expensive initialization. Creating drawing objects within your `onDraw()`… can make your UI sluggish" — cache `Paint`, `StaticLayout`, `Path`, `Typeface` across frames. — https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing
- `Paint.equalsForTextMeasurement()` exists for cache-keying equivalent paints. — https://developer.android.com/reference/android/graphics/Paint

## 7. Memory: trim levels that actually fire on targetSdk 35

- **Important change:** "Beginning with Android 14, the system no longer delivers notifications for the other, legacy constants" — only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` remain; the others "were formally deprecated in Android 15." The LMK doc confirms: "Android doesn't have any APIs for detecting native memory pressure events… it deprecated all of them other than `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND`." — https://developer.android.com/topic/performance/memory/manage-app-memory , https://developer.android.com/topic/performance/issues/lmk
- Handle levels with `>=` comparisons, not equality, since new intermediate values may appear. — https://developer.android.com/reference/android/content/ComponentCallbacks2
- Guidance: `UI_HIDDEN` = release UI-only allocations (bitmap caches, video buffers); `BACKGROUND` = release anything rebuildable. For a HOME app, `UI_HIDDEN` is the reliable "another activity covers the launcher" signal — the running-state `TRIM_MEMORY_RUNNING_*` levels are moot on API 34+.
- Docs do **not** carve out launchers: there is no "HOME never gets trimmed" guarantee. Practical policy consistent with the docs: keep the wallpaper bitmap (rebuild is not "quick," and HOME re-entry must be instant — releasing it would cause a visible decode hitch on every app exit), release screensaver bitmaps and any image caches on `UI_HIDDEN`.
- Image-cache sizing: byte-sized `LruCache`; the cache-bitmap page lists the sizing factors (see §2). — https://developer.android.com/topic/performance/graphics/cache-bitmap

## 8. API-level wins available via `Build.VERSION` checks (23 → 35)

| API | Addition | Relevance |
|---|---|---|
| 26 | `Bitmap.Config.HARDWARE` (always immutable); bitmap pixels move to native heap; `Bitmap.reconfigure()/setWidth()/setHeight()/setConfig()`; `SurfaceHolder.lockHardwareCanvas()` | Already using HARDWARE on MID; on API 26+ `reconfigure` lets a pooled bitmap resize without realloc |
| 26 | `Window.setColorMode(COLOR_MODE_WIDE_COLOR_GAMUT)` / `ActivityInfo` color modes | Better wallpaper color fidelity, **but** the wide-color doc warns the window "uses more memory and GPU processing for screen composition" — not free on 1 GB boxes. HDR (`COLOR_MODE_HDR`) is explicitly "strongly recommended to avoid… until API 34." — https://developer.android.com/training/wide-color-gamut , https://developer.android.com/reference/android/content/pm/ActivityInfo |
| 28 | `ImageDecoder` (default HARDWARE output, `ALLOCATOR_HARDWARE`, decode-time `setTargetSize`); `HardwareBuffer`; `Bitmap.getHardwareBuffer()` | Preferred decode path on API 28+; gives hardware bitmaps + downsampling in one call |
| 29 | `RenderNode`, `RecordingCanvas`, `HardwareRenderer` | Manual display-list granularity: record once, transform cheaply, `drawRenderNode` — only useful if the current single-View approach shows recording cost in traces |
| 31 | `RenderEffect.createBlurEffect` + `View.setRenderEffect` | Platform blur for glass/translucency on API 31+; could replace hand-baked lenses on newer boxes — but adds a per-frame effect cost; evaluate per `DeviceTier`. — https://developer.android.com/guide/topics/renderscript/migrate (RenderEffect snippet) |
| 34+ | Only `TRIM_MEMORY_UI_HIDDEN`/`BACKGROUND` delivered | Simplifies trim handling (§7) |

Note: there is **no framework spring animation** — `SpringAnimation`/`SpringForce` live in AndroidX `dynamicanimation`, which zero-dependency rules out; the hand-rolled spring solver remains the correct choice. Framework `android.animation` (API 11+) offers `ValueAnimator`/`ObjectAnimator`/`AnimatorSet` only. — https://developer.android.com/reference/android/animation/ValueAnimator

## 9. Android TV / leanback specifics

- No launcher-performance-specific guidance exists on developer.android.com. TV docs cover leanback theming/layouts, D-pad focus, overscan, and `CATEGORY_LEANBACK_LAUNCHER` manifest requirements — not rendering/memory performance for launchers. The general App-quality rendering/performance docs (§1–§7) are the applicable corpus. — https://developer.android.com/training/tv/get-started/create , https://developer.android.com/docs/quality-guidelines/tv-app-quality , https://developer.android.com/training/tv/playback/leanback/layouts

## What is realistically achievable

**Real wins (docs-backed):**
1. **Simplify trim handling**: on target 35 only `UI_HIDDEN`/`BACKGROUND` matter; keep wallpaper, drop caches/screensaver on `UI_HIDDEN`. Zero-risk, small RAM hygiene win.
2. **Text**: move any `Canvas.drawText`-heavy labels to cached `StaticLayout` (and `PrecomputedText` built off-thread on API 28+). Real UI-thread CPU savings if text is redrawn per frame.
3. **Hierarchy flattening**: a single custom View (or one View per focusable card) removes per-view measure/layout/display-list overhead — supported by the documented per-view cost model, though the magnitude is workload-dependent; verify with systrace/`dumpsys gfxinfo`.
4. **API-gated decode**: `ImageDecoder` on API 28+ for hardware bitmaps + decode-time downsampling; `Bitmap.prepareToDraw()` on decode threads; `inBitmap` pooling for mutable decode targets on API 26+.
5. **Audit overdraw** with Debug GPU Overdraw; keep the 2.5× pixel budget in mind — glass-over-wallpaper is the main legitimate cost.
6. **Measurement first**: Profile GPU Rendering histogram will show whether remaining frame cost is Draw (record), Command Issue, Sync & Upload (bitmap re-uploads), or Swap Buffers (GPU/fill). This decides between CPU-side and GPU-side work.

**Myths / things the docs contradict or don't support:**
- *"Request RGB_565 window format to shrink the 24 MB of SurfaceFlinger buffers"* — unsupported. `TRANSLUCENT`/`OPAQUE` are logical formats; "the system chooses"; Gralloc picks from usage flags; no documented app control over window buffer count/format. The 3-buffer triple-buffered queue is SurfaceFlinger-side and demand-allocated.
- *"`setLayerType(LAYER_TYPE_HARDWARE)` makes expensive drawing free"* — it relocates cost to GPU texture memory + a composite pass, and the display list still re-records on every invalidate. Only wins for static complex content animated purely by properties.
- *"`Canvas.saveLayer` is cheap"* — doc: "very expensive, more than double rendering cost."
- *"Hardware bitmaps reduce PSS"* — they move bytes to graphics/DMABuf accounting; app/native heap drops but total graphics memory doesn't. Attribution in standard tools is explicitly "harder."
- *"`onTrimMemory` RUNNING_*/MODERATE/COMPLETE levels still fire"* — not delivered since Android 14, formally deprecated in 15.
- *"Wide color gamut is free quality"* — doc explicitly warns of extra window memory and GPU composition cost; HDR deferred to API 34+.
- *"Disabling hardware acceleration saves RAM"* — HW accel does consume more RAM, but disabling it is incompatible with modern canvas features and isn't a supported path for this design.
- *"Release the wallpaper on trim"* — technically possible but contradicts the "release what can be rebuilt quickly" guidance in spirit: HOME re-entry is latency-critical for a launcher; 8 MB is cheap insurance.
- *"A single custom View is categorically faster"* — the docs document per-view measure/layout costs but never claim one View always wins; the honest statement is it removes a documented cost class, not all cost.

**Bottom line:** the remaining ~24 MB window buffers and ~8 MB HARDWARE wallpaper are near the platform floor for a 1080p HW-accelerated window. The genuinely available headroom is CPU-side (text layout caching, display-list granularity on API 29+, decode path on API 28+) and hygiene (trim handling, overdraw audit) — likely single-digit MB of PSS and a few ms of UI-thread time per invalidated frame, not a step-change in memory.
