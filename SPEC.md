# SaiyanStrong — VBT Camera Tracking: Fix & Simplify to One Robust Flow

## Status: Draft — awaiting confirmation before implementation.

(Replaces the previous "Femur ↔ Torso Angle Coupling" biomechanics spec — that shipped as
v0.50.0. This spec returns to the Velocity-Based Training camera feature, which the user reports
is broken across the board: wrong/nonsense numbers, crashes, confusing calibration, and tracking
that loses the bar. Direction chosen: collapse to one robust, verifiable flow — fewer features,
ones that actually work — rather than more targeted patches on the accumulated complexity.)

---

## 0. Decisions locked in via clarifying questions

- **Symptoms reported (all of them):** implausible numbers, crashes/errors, calibration
  confusing/stuck, tracking losing the bar. So this is a ground-up reliability pass on the primary
  flow, not a single bug.
- **Marker approach — user deferred to "pick what's most reliable":** chosen approach is a
  **single bright single-color marker** for tracking + a **one-time known-length scale** measured
  by tapping two points on a static reference in the first frame (a standard weight plate is
  ~45 cm across — always present, nothing to buy or attach twice). This is strictly more robust
  than the current dual-marker default: scale is measured once from a still frame instead of
  depending on a second marker being correctly detected in every single frame.
- **Aggressiveness — "simplify to one robust flow + verify":** the offline **record → calibrate →
  analyze** path becomes THE flow. The parallel/experimental paths (dual-marker calibration,
  continuous live uncalibrated session, high-speed capture, live reticle/velocity overlays) are
  removed from the primary user path. Their engine-level code may remain dormant where ripping it
  out is risky, but nothing in the default flow renders or depends on them.

---

## 1. Objective

Make the VBT feature produce trustworthy velocity numbers from a phone recording of a barbell
lift, via the simplest flow that can be made reliable: record a set, tap the marker + a known
reference length, get real m/s / power / bar-path back — and be able to *see* that the tracking
actually followed the bar before trusting the number.

Target users: any SaiyanStrong user who wants velocity feedback and is willing to stick one bright
marker on the bar and film one set roughly side-on.

---

## 2. Root causes & the fix for each

### 2.1 Whole-clip-treated-as-concentric → mean velocity ≈ 0, peak from the descent (BIGGEST)
- Today `AnalyzeBarPathUseCase.execute` is called with `concentricStartMs = samples.first()` and
  `concentricEndMs = samples.last()` — the entire clip. For a full squat (down then up), net
  vertical displacement over the whole clip ≈ 0, so `meanConcentricVelocityMs` ≈ 0, and
  `peakVelocityMs` can come from the eccentric (downward) phase. This alone produces the classic
  "0.00 mean, huge peak" nonsense.
- **Fix**: new pure `ConcentricDetector` (domain/util) that finds the concentric (net-upward)
  window from the tracked height series — from the lowest bar point to the subsequent highest
  point (the ascent). `BarPathCaptureViewModel` passes that window's start/end into the analyzer
  instead of the whole clip. A concentric-only clip yields ≈ the whole clip; a full-rep clip
  yields just the ascent.
- Guard: if the detected window has < N samples (e.g. the clip is too short or motion is flat),
  fall back to the whole clip so a degenerate case degrades gracefully rather than returning empty.
- **Acceptance**: unit tests on synthetic down-then-up height series confirm the detected window
  is the ascent (positive net displacement), and that a monotonic-up series returns ≈ the whole
  clip. A full-rep fixture that previously yielded ~0 mean velocity now yields a plausible positive
  mean.

### 2.2 Dual-marker default → garbage/zero scale when marker B mis-detected
- Today `useDualMarkerMode = true` is the default; `onConfirmDualMarkerCalibration` uses
  `perFramePixelsPerMeter`, and if the reference marker is never found the fallback is
  `firstNotNullOfOrNull { ... } ?: 0.0`, which zeroes the analysis (or worse, uses a bad frame's
  scale). It also demands two *distinct* colors a *precise* known distance apart — the most
  error-prone setup in the feature.
- **Fix**: remove dual-marker mode from the flow. Single-marker + tap-two-reference-points becomes
  the only calibration path. The `useDualMarkerMode` branch, the marker-B tap handling, and the
  reference-distance field are removed from the calibration UI and the ViewModel's confirm logic.
- **Acceptance**: the calibration screen offers exactly one path; there is no way to reach a
  zeroed-scale result from a mis-detected second marker because there is no second marker.

### 2.3 Crashes / errors
- **Fix**: wrap every native/IO boundary in the flow — `extractFirstFrame`, `trackMarker`,
  `videoDimensions`, `analyzeBarPathUseCase.execute/trackFrames`, gallery import — in defensive
  `runCatching`, routing any failure to `CaptureStep.ERROR` with a specific, human message instead
  of crashing. High-speed capture stays off by default (already true since v0.42.1); its toggle is
  removed from the simplified recording step. Confirm no path can call the analyzer with fewer
  than 2 samples (already guarded) and that a null/failed frame extraction is handled everywhere.
- **Acceptance**: forcing each failure (unreadable video, no marker found, zero-length clip)
  surfaces a distinct error message and returns to a usable state; none crash.

### 2.4 Calibration confusing/stuck
- **Fix**: one linear, numbered flow with unmistakable prompts:
  1. "Tap the marker on the bar" (samples color).
  2. "Tap each end of one weight plate" (two points) — with the hint that a standard plate is
     ~45 cm; the reference-length field is prefilled to 45 and editable.
  3. (standalone entry only) "Weight lifted (kg)".
  4. ANALYZE.
- The v0.28.0 calibration-scroll fix stays; the screen must remain fully scrollable with the
  ANALYZE button always reachable. Remove the dual/manual mode toggle entirely (one mode now).
- **Acceptance**: a first-time user can complete calibration without guessing what to tap; every
  required input has a visible prompt and validation message if missing.

### 2.5 Tracking loses the bar / latches onto the wrong thing
- Blob detection + nearest-neighbor tracking already exist (Sprint 28) and stay. The reliability
  additions here are (a) removing false-positive-prone complexity, (b) making tracking *visible*
  so the user can judge it, and (c) clearer guidance:
  - **Show the tracked path on the RESULTS screen** over the calibration frame — full polyline +
    start(green)/end(red) dots — so a jumpy/jagged path is immediately obvious before the user
    trusts the numbers. (A tracked-path preview already exists from earlier sprints; ensure it is
    actually shown in the simplified results, statically, not only inside the optional replay.)
  - **Recording tips** (kept, tightened): one bright marker that contrasts with the background,
    good lighting, film roughly side-on and perpendicular, keep the whole bar path in frame.
- **Acceptance**: after analysis the user sees the path the tracker followed; obviously-bad
  tracking is visually distinguishable from a clean vertical-ish path without reading any number.

---

## 3. Simplifications (remove from the primary path)

- Dual-marker calibration mode (see 2.2).
- Continuous live uncalibrated rep session (`currentRepSummary`/`liveSessionReps`/`LiveTrailOverlay`/
  `LiveSessionUi`) — removed from the default recording step. It only ever produced *relative*
  (non-m/s) numbers and is a major source of "confusing." Engine code may remain dormant; the
  primary flow neither renders nor depends on it.
- Live reticle / live velocity readout / START REP / tap-to-color-during-recording overlays on the
  recording step — removed. Color is sampled during calibration on the first frame, which is more
  reliable than sampling a live moving frame anyway.
- High-speed (120fps) capture toggle — removed from the recording step (stays off).
- The recording step becomes: camera preview + RECORD/STOP + IMPORT FROM GALLERY (standalone) +
  dismissible tips. Nothing else.

These are removals from the *user-facing default flow*. Deleting the underlying files entirely is
a follow-up cleanup, explicitly out of scope for this pass unless it falls out cleanly — the goal
here is a reliable path, not a code purge.

---

## 4. Tech stack additions

None. Pure Kotlin for `ConcentricDetector` (domain/util) + edits to existing
`AnalyzeBarPathUseCase` wiring, `BarPathCaptureViewModel`, `BarPathCaptureScreen`. No new deps, no
schema change (saved `BarPathAnalysis`/`bar_path_metrics` shape is unchanged — this changes *what
window* feeds the same fields, not the fields).

---

## 5. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── domain/util/
│   └── ConcentricDetector.kt              ← NEW: pure concentric-window detection
├── domain/usecase/
│   └── AnalyzeBarPathUseCase.kt           ← unchanged internally; called with detected window
├── presentation/screens/barpath/
│   ├── BarPathCaptureViewModel.kt         ← single-marker only; concentric window; crash hardening;
│   │                                         remove dual-marker + live-session orchestration
│   └── BarPathCaptureScreen.kt            ← one linear calibration; stripped recording step;
│                                             tracked-path shown on results
```

Test files mirror the new/changed pure logic (`ConcentricDetectorTest`, and any
`AnalyzeBarPathUseCase` window-selection coverage), per this project's "pure core, untested Compose
shell" split.

---

## 6. Code style (extends existing CLAUDE.md rules)

- `ConcentricDetector` is pure `domain/util` (zero Android imports), same home/shape as
  `SavitzkyGolayFilter`/`ScaleCorrection`.
- No hardcoded colors; reuse existing theme tokens and existing composables where possible.
- Error messages are specific and actionable ("Couldn't track the marker — make sure it's bright
  and well-lit against the background"), never a bare "error".
- Named constants for any thresholds (min concentric samples, etc.) with a one-line rationale,
  matching existing `MIN_MARKER_PIXELS`-style precedent.

---

## 7. Testing strategy

- **ConcentricDetector**: synthetic height series — pure down-then-up (returns the ascent),
  monotonic up (returns ~whole), flat/degenerate (falls back to whole clip), noisy-but-clearly-up.
- **Analyzer window selection**: a full-rep sample fixture that previously produced ≈0 mean
  velocity now produces a plausible positive mean when fed the detected window.
- **Existing suite stays green**: `AnalyzeBarPathUseCaseTest`, `BarPathFrameTrackerTest`,
  `MarkerColorProfileTest`, etc. — re-run; the analyzer's internal math is unchanged so these must
  pass untouched.
- **On-device verification (the user's part, honestly flagged)**: real-footage validation still
  requires a physical recording — this session has no device. The tracked-path preview on results
  is specifically to make that verification a glance for the user. Provide a short on-device test
  checklist (record a single squat with a bright marker, tap marker + plate ends, confirm the path
  follows the bar and the mean velocity is in a sane 0.1–1.5 m/s range).
- Full `assembleGithubDebug` + full unit-test run before shipping, same release discipline as every
  prior sprint (version bump before build, badging verification local + downloaded release asset,
  explicit file staging).

---

## 8. Boundaries

**Always do:**
- Keep all new analysis logic pure and unit-tested.
- Keep the physics engine (`AnalyzeBarPathUseCase` internals) intact — only its *input window*
  changes.
- Surface every failure as a clear ERROR state; never crash the capture flow.
- Preserve the existing saved-data shape and the ExerciseDetail "BAR SPEED" chart / rep-card
  consumers (they read the same `BarPathAnalysis` fields).

**Ask first about:**
- Whether to *delete* the now-dormant dual-marker / live-session / high-speed files vs. leave them
  dormant (default: leave dormant this pass, cleanup later).
- The default reference-length prompt (45 cm plate) if a different standard is preferred.

**Never do:**
- Don't reintroduce dual-marker or the uncalibrated live session into the default flow.
- Don't claim real-footage verification that didn't happen — the on-device check is the user's,
  and the KNOWN GAP stays flagged.
- Don't touch unrelated features (biomechanics visualizer, Coach mode, updater).

---

## 9. Notes

This is deliberately a *subtractive* sprint: the VBT feature accumulated ~17 files and many
overlapping tracking/calibration mechanisms across a dozen sprints, none verified end-to-end
against real footage. The single highest-value change (concentric-window detection) is small and
pure; the rest is removing fragility and making tracking visible so the user can trust — or
distrust — a result at a glance. Real-device validation remains the open item it has always been,
but the flow the user has to validate is now one path instead of four.
