# SPEC — VBT tracking rebuilt on OpenCV TrackerVit (tap the plate, no marker)

## Status: Draft — awaiting confirmation before implementation.

Replaces the entire hand-rolled colour/blob/NCC/template tracking stack with a mature,
DNN-based single-object tracker from OpenCV. Decided via clarifying questions: **add
OpenCV**, **tap the plate (no colored marker)**, **keep the record-then-analyze flow**.

---

## 1. Objective

Make bar-path tracking actually work. Across ~15 sprints we hand-rolled pixel CV in
Kotlin — HSV colour blobs (grabbed same-colour background), then NCC template matching
(lost lock on motion blur). Every real-footage test failed. The apps that work
([Metric](https://metric.coach), [Qwik VBT](https://apps.apple.com/us/app/qwik-vbt-velocity-bar-tracker/id1660094818),
open-source [barbellcv](https://github.com/tlancon/barbellcv)) are all built on **OpenCV**
and have the user **tap the plate** — no colored marker.

**Target user:** a lifter who records or uploads a phone video of a lift, taps the weight
plate once, and watches a dot track the plate cleanly through the rep, then optionally gets
velocity numbers. No colored sticker, no lighting/colour calibration, no colour advisor.

**The core change:** swap our tracker for OpenCV's `TrackerVit` (a Vision-Transformer DNN
tracker in the official `video` module). Tap → init tracker with a bounding box around the
plate → per frame `tracker.update(mat) → Rect` → the Rect centre is the bar position →
feed into the existing pipeline (`ConcentricDetector`, `SavitzkyGolayFilter`,
`AnalyzeBarPathUseCase`). This is the "correct" version of the template matcher we tried
to build by hand.

### Why TrackerVit specifically (verified this session)
- **In the official Maven Central AAR.** `org.opencv:opencv` ships the main `video`
  module, which contains `TrackerVit`, `TrackerNano`, `TrackerMIL`, `TrackerGOTURN`,
  `TrackerDaSiamRPN`. **CSRT and KCF are NOT** — they live in `opencv_contrib` and would
  need a painful NDK source build. Confirmed against the 4.x `org.opencv.video` javadoc.
- **DNN tracker → far more blur/appearance-change robust** than classical CSRT, and newer.
- **Tiny model:** `object_tracking_vittrack_2023sep.onnx` = **715 KB** (int8 variant 271 KB),
  **Apache-2.0** (confirmed in the model's own README) → safe to bundle in `assets/`.
- **Clean Gradle dep**, no OpenCV Manager (self-contained native libs since 4.9.0,
  `OpenCVLoader.initLocal()`).

### Acceptance criteria
1. `.\gradlew assembleGithubDebug` builds clean with the OpenCV dependency; app launches.
2. On a real recorded/uploaded lift, tapping the plate makes the tracked dot **follow the
   plate down and up through the whole rep in a clean line** (the on-device self-check —
   the whole point). No teleporting to background, survives normal motion blur.
3. Velocity numbers come out plausible (mean concentric velocity non-zero and in a sane
   0.1–1.5 m/s band for a working rep), using the existing `ConcentricDetector` window.
4. Zero `" lb"` anywhere; all existing unit tests still green; new pure-logic tests pass.
5. The colour-marker path (calibration, advisor, `MarkerColorProfile`, blob tracking) is
   left **dormant, not deleted** this slice (reliability first; purge later).

---

## 2. Commands

```powershell
# Build (PowerShell — the rtk Bash hook rewrites ./gradlew and hangs)
.\gradlew assembleGithubDebug

# Unit tests (pure logic only — OpenCV native can't run in JUnit)
.\gradlew testGithubDebugUnitTest

# Verify version + no "lb"
# aapt dump badging <apk> | Select-String versionCode
# grep for " lb" across app/src must return zero
```

Release per CLAUDE.md `## Release rules`: bump `versionCode`+`versionName` in
`app/build.gradle.kts` **before** the final build; `gh release create` + `upload --clobber`
the `github`-flavor debug APK.

---

## 3. Project structure & scope

### New dependency (version catalog only — never hardcode in build.gradle.kts)
```toml
# gradle/libs.versions.toml
opencv = "4.11.0"   # pin the latest verified 4.x on Maven Central at implementation time
[libraries]
opencv = { module = "org.opencv:opencv", version.ref = "opencv" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.opencv)
```
- **APK size:** the AAR bundles native `.so` for arm64-v8a / armeabi-v7a / x86 / x86_64
  (~30–40 MB across all ABIs). Mitigation: `ndk { abiFilters += listOf("arm64-v8a",
  "armeabi-v7a") }` (drop x86/x86_64 — no real phone needs them) roughly halves it. Real,
  accepted tradeoff — this is the cost of tracking that works.
- Third-party AAR is fine under "Kotlin only, no Java" — that rule governs *our* source,
  not a library.

### New files
```
app/src/main/assets/vittrack/object_tracking_vittrack_2023sep.onnx   ← 715KB, Apache-2.0
app/src/main/java/com/saiyanstrong/util/barpath/OpenCvInitializer.kt ← one-time initLocal() guard
app/src/main/java/com/saiyanstrong/util/barpath/VitBarTracker.kt     ← wraps TrackerVit: init(box) + update(bitmap)->Rect?
app/src/test/java/com/saiyanstrong/util/barpath/VitBarTrackerSupportTest.kt ← pure helpers only
```

### Changed files
- `BarPathFrameTracker.kt` — new `trackWithVit(videoPath, startMs, initBoxVideoPx, onSample, onProgress)`:
  same frame-extraction loop / `startMs` / `MAX_SAMPLES` cap / `onSample` streaming as
  `trackMarker`, but per frame: `Bitmap → Mat (Utils.bitmapToMat)` → `vitTracker.update(mat)`
  → `Rect` → centre → `BarPathSample` in **video px**. On `update` returning failure/low
  confidence, **hold** the last position (never teleport) — same guard philosophy as today.
  Emits samples the existing pipeline already consumes unchanged.
- `BarPathCaptureViewModel.kt` — `onMarkTap(videoX, videoY, atMs)` seeds an **init box**
  (a square ~plate-sized around the tap, e.g. `min(w,h)*0.18`) and calls `trackWithVit`
  instead of `trackMarker`. No colour sampling. `calibratedProfile` path unused here.
- `BarPathCaptureScreen.kt` — the live player prompt becomes "scrub to the lift, then tap
  the **plate**"; retry copy points at tapping a clearly-visible plate. The colour
  calibration UI (`CalibrationOverlay`/`CalibrationControls`/`CalibrationAdviceBanner`)
  is **not shown** in this flow (left in the file, dormant).
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — dep + abiFilters + version bump.

### Optional (flag, don't build unless it falls out cleanly): auto-scale from the plate box
The tracked `Rect` width ≈ the plate's on-screen diameter. A standard competition plate is
**0.45 m**. So `pixelsPerMeter ≈ rectWidthPx / 0.45` — a free scale with **no two-tap step**.
Pure helper `plateScalePpm(rectWidthPx, plateDiameterM = 0.45)`, unit-tested. If it reads
well on device, it replaces the two-plate-edge tap; keep the manual two-tap as fallback for
non-standard plates. **Decision deferred to a real-device look** — ship tap-tracking first.

---

## 4. Code style

- Kotlin only, Jetpack Compose only, Clean Architecture, StateFlow, Hilt — unchanged.
- **Pure-core / thin-Android-shell split** (the project's established pattern): OpenCV calls
  (`TrackerVit`, `Utils.bitmapToMat`, `initLocal`) live in the untestable shell alongside
  CameraX/ExoPlayer. Anything pure (init-box geometry from a tap, Rect→centre, plate-scale,
  bounds clamping) is a top-level `internal fun` and **is** unit-tested.
- No hardcoded colors; metric units everywhere (`_kg`, m/s, meters).
- Guard every native boundary in `runCatching`/try-catch — a tracker or decode failure
  routes to the existing ERROR surface, never crashes (same discipline as v0.42.1/v0.51.x).

---

## 5. Testing strategy

- **Pure unit tests (JUnit, no Robolectric):** init-box-from-tap geometry (correct size,
  clamped to frame bounds when the tap is near an edge), `Rect.center()` → sample, plate
  scale math, hold-on-failure sample continuity. These are the only genuinely testable
  pieces — the tracker itself needs the OpenCV native runtime.
- **OpenCV/`TrackerVit`/CameraX/ExoPlayer** stay in the unit-untestable shell, verified only
  on device — consistent with every prior VBT sprint.
- **The real acceptance test is on-device and self-verifying:** the tracked dot visibly
  following the plate in the live player *is* the proof the tracker works. A jagged or stuck
  dot is an obvious fail signal before any number is trusted.

---

## 6. Boundaries

**Always**
- Version catalog for the OpenCV version; `abiFilters` to arm64+armv7 to contain APK size.
- Bundle the ONNX from OpenCV's model zoo (Apache-2.0) in `assets/`; attribute it in the
  progress-log entry.
- Guard `OpenCVLoader.initLocal()` and every native call; fail soft to the ERROR screen.
- Keep `ConcentricDetector` (the v0.51.0 fix for the whole-clip mean≈0 bug) in the path.
- Update CLAUDE.md progress log + bump both versions on release.

**Ask first**
- Deleting the dormant colour/marker/live-session/template files (a separate cleanup slice).
- Adding a *live* on-preview tracking overlay (the fragile 3-stream CameraX path — deferred).
- Switching the two-tap scale to auto-plate-scale as the default (needs a device look first).
- Any second dependency (e.g. an ONNX runtime) — TrackerVit runs inside OpenCV's own DNN
  module, so **no** extra runtime is needed; flag if that assumption breaks at build time.

**Never**
- Rebuild OpenCV contrib from source / ship CSRT via an NDK build (defeats the whole point
  — the clean Maven AAR + TrackerVit is why this is viable).
- Commit secrets/keystores. Introduce any `" lb"`. Use XML layouts or Java. Hardcode colors.
- Bundle a non-Apache/non-permissively-licensed model.

---

## 7. Known risks (honest)

- **APK size** grows ~15–20 MB (arm64+armv7 native libs). Real, accepted.
- **TrackerVit robustness** on a fast, blurred plate is far better than our NCC but not
  magic; a very blurry / occluded plate can still lose lock (mitigated by hold-on-failure).
  Unknown until real footage — but this is the method the working apps use.
- **Native init on old devices** — `initLocal()` must succeed; guarded.
- **Not device-verified this session** (no emulator) — same standing caveat as all VBT work.
  The on-device dot-follows-plate check is the acceptance gate.
