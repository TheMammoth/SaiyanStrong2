# SPEC — VBT Marker Calibration ("train the marker before recording")

## Status: Draft — awaiting confirmation before implementation.

(Replaces the stickman-animation spec — the 4 stickman slices shipped in v0.56.0–v0.59.0.)

## 1. Objective

Different users have different marker colors and different gym lighting, and the current
flow only samples the marker color **after** recording — from a possibly motion-blurred
frame under whatever lighting the lift happened in. That's the root of the "I tap the
marker and it doesn't mark it" failure.

Add a short **calibration step before recording**: the user points the live camera at
their marker, taps it, and the app *learns* that marker's color under *their* lighting —
sampling it across a couple seconds of live frames to build a robust color range, showing
a live green "detection mask" so the user can *see* it isolates the marker cleanly, and
warning if the chosen color also lights up the background. Recording then tracks with that
locked-in profile.

This is **not machine learning** — it's live color enrollment. "Train the marker," not
"train a model."

**Target user:** anyone recording a lift for velocity, in a real gym/room with real
lighting and their own marker (a taped patch / bright sticky note on the sleeve).

## 2. Decisions (locked via clarifying questions)

1. **Per-recording calibration.** A quick "lock your marker" step every time, before the
   record button is live. Always matches the current lighting — no stale saved profile.
   (No DataStore persistence of the profile; it lives for the capture session only.)
2. **Live preview + tap, with a detection mask.** Live camera; tap the marker; the app
   overlays a green mask on *every* matching pixel each frame so the user confirms the
   marker glows and the background stays dark before recording.
3. **Multi-sample color range.** Sample the marker across ~1–2 s of live frames (or a few
   taps) and build a hue/sat/val *range* across that window — robust to glare/shadow
   through the rep — not a single point ± a fixed tolerance.
4. **Background-clash warning.** After sampling, scan the frame for other large blobs of
   the same color; if the marker color also matches the windows/walls/clothing, warn the
   user to move it out of frame or pick a different marker color.

## 3. How it fits the existing flow

Current capture flow (`BarPathCaptureViewModel.CaptureStep`):

```
RECORDING → PLAYER (tap marker post-record, samples color there) → SCALE → RESULTS
```

New flow:

```
CALIBRATE → RECORDING → PLAYER (tap = position/time seed only) → SCALE → RESULTS
                                   ↑ color comes from CALIBRATE, not the tap
```

- **CALIBRATE** is a new first step (live camera). Produces a `MarkerColorProfile` stored
  in `BarPathCaptureUiState.colorProfile`.
- **RECORDING** unchanged except it's entered *after* calibration. The live ImageAnalysis
  loop used for calibration is **unbound before video recording starts** — recording stays
  Preview + VideoCapture only (never the device-fragile 3-stream bind that v0.51.0
  deliberately removed).
- **PLAYER**: `onMarkTap` no longer samples color from the tapped pixel. The tap now only
  sets the **start position seed + start time** (`initialVideoX/Y`, `startMs`); the color
  is the pre-calibrated profile. This removes the "tapped a blurry frame → bad color"
  failure entirely.
- **Gallery import** has no live camera, so it **keeps today's tap-to-sample-color path**
  as a fallback (calibration is skipped; `colorProfile` stays null → `onMarkTap` samples
  on tap as it does now). This is the one branch where post-record color sampling remains.

## 4. Core features & acceptance criteria

### 4.1 Calibration step (live)
- Live camera preview (CameraX Preview + ImageAnalysis; **no** VideoCapture yet).
- Instruction: "Point at your marker and tap it."
- Tapping the marker samples an averaged color patch (reuses `ColorPatchSampler` /
  `sampleMarkerColor` neighborhood averaging).
- **Multi-sample:** after the first tap, the app keeps sampling the region around the
  tapped point over the next ~1–2 s of frames (or additional taps), accumulating HSV
  samples into a range builder.
- **Live mask overlay:** each analyzed frame, matching pixels are drawn as a translucent
  green overlay aligned to the preview, so the user sees exactly what's detected.
- **Clean-lock gate:** the "START RECORDING" button enables only once detection is a single
  dominant blob for N consecutive frames (a stable, isolated marker). Until then it shows
  "aim / tap the marker."
- **AC:** with a well-saturated marker in frame, tapping it produces a green mask that
  covers the marker and little else, and START RECORDING enables within ~2 s.

### 4.2 Multi-sample range model (pure)
- New pure builder `MarkerColorRangeBuilder` (domain/util or util/barpath, pure — no
  Android): accepts a list of sampled `(h,s,v)` and produces a `MarkerColorProfile`:
  - `hueCenter` = circular mean of sampled hues; `hueTolerance` = max circular deviation
    from center + a margin (respecting a sensible min/max band).
  - `minSaturation` = min sampled saturation − margin (floored); `minValue` = min sampled
    value − margin (floored).
  - `satCenter/valCenter` = means; tolerances from spread.
- Maps onto the **existing** `MarkerColorProfile` shape — no new profile type, no
  downstream tracker changes. Keeps the pale-adaptive widening from v0.59.2 as the
  single-sample fallback path.
- **AC:** several samples of the same marker under varying brightness yield a profile that
  `matches()` all of them; a clearly different color does not match.

### 4.3 Background-clash check (pure)
- Pure classifier `BackgroundClashDetector`: given the per-frame blob list (from the
  existing `findBlobs`) and the tapped marker's position, returns a verdict —
  `CLEAN` (one dominant blob at the marker) vs `CLASH` (a second large blob elsewhere, or
  matched-pixel fraction over a threshold).
- Surfaced as a non-blocking warning card: "This color also shows up in the background —
  move it out of frame or use a different marker." The user can still proceed.
- **AC:** a frame with only the marker → CLEAN; a frame with the marker plus a large
  same-color background patch → CLASH.

### 4.4 Wiring
- `BarPathCaptureUiState` gains a `CALIBRATE` step + calibration sub-state (sampled color,
  live blob count, clash verdict, lock-ready flag).
- `onMarkTap` gains a branch: if `colorProfile != null` (calibrated), use it and treat the
  tap as position/time seed only; else (gallery import) sample on tap as today.
- The live analysis loop reuses/extends `BarPathLiveAnalyzer` to emit, per frame, the
  matched-blob list and a coarse mask for the overlay.

## 5. Project structure (new / changed)

```
util/barpath/
  MarkerColorRangeBuilder.kt      NEW  pure — list of HSV samples → MarkerColorProfile
  BackgroundClashDetector.kt      NEW  pure — blobs + marker pos → CLEAN | CLASH
  BarPathLiveAnalyzer.kt          EDIT emit matched-blob list + coarse mask for calibrate
  MarkerColorProfile.kt           (unchanged; range builder targets its existing fields)
presentation/screens/barpath/
  BarPathCaptureViewModel.kt      EDIT CALIBRATE step, multi-sample accumulation, clash,
                                       onMarkTap uses calibrated color (seed-only tap)
  BarPathCaptureScreen.kt         EDIT CalibrateStep composable: live preview + mask
                                       overlay + clash warning + START RECORDING gate
  CalibrationMaskOverlay.kt       NEW  Compose overlay drawing the green match mask
```

No Room schema change. No new dependencies (CameraX + Compose already present). No new
permission (CAMERA already declared).

## 6. Code style / constraints
- Compose only (no XML/Views). Kotlin only.
- Pure computational cores (`MarkerColorRangeBuilder`, `BackgroundClashDetector`) live in
  pure files with **no Android imports**, unit-tested; the CameraX/Compose shell is the
  untested-on-device layer, same split as the rest of the VBT feature.
- No hardcoded colors in UI (theme tokens / existing overlay colors).
- Two-stream calibration bind (Preview + ImageAnalysis); recording stays Preview +
  VideoCapture. Never bind all three.

## 7. Testing strategy
- `MarkerColorRangeBuilderTest`: circular hue mean across wrap (350°, 10° → ~0°); range
  covers all input samples; floors applied; single-sample equals the adaptive fallback.
- `BackgroundClashDetectorTest`: one blob → CLEAN; large second blob → CLASH; small
  incidental blob near the marker → still CLEAN.
- Build: `.\gradlew testGithubDebugUnitTest` green, `.\gradlew assembleGithubDebug` green,
  no `" lb"` in `app/src` Kotlin/strings.
- Device (owed, no emulator this session): calibrate → mask isolates marker → record →
  post-record dot follows without re-sampling color.

## 8. Boundaries

**Always:** keep the pure cores Android-free + unit-tested; keep gallery-import's
tap-to-sample fallback working; unbind the analysis stream before video recording.

**Ask first:** persisting a calibrated profile across sessions (explicitly out of scope
now — decision was per-recording); any real ML/on-device model (out of scope — this is
color enrollment, not training a network); changing the SCALE/analysis math.

**Never:** bind 3 camera streams during actual video recording; add a camera/storage
permission (none needed); block recording on the clash warning (it's advisory, not a
gate — only the clean-lock stability gate enables START RECORDING).

## 9. Known gaps / deferred
- Marker-tracking core (blob detection + nearest-neighbor) is unchanged; this makes the
  *color profile* far more reliable, not the tracker's motion logic.
- No cross-session saved profiles (per decision).
- Not device-validated this session (no emulator) — the live mask is itself the on-device
  self-check once the user runs it.
