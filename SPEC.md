# SaiyanStrong — Best-Effort VBT: Tap-to-Calibrate Color + Path Preview + Tips

## Status: BUILT (v0.26.0) — see CLAUDE.md "Sprint 29".

(Replaces the previous "Standalone Bar Path Analysis" spec in this file — that shipped as
v0.25.0/v0.25.1; see CLAUDE.md "Sprint 28" and the marker-tracking fix entry. This spec is the
answer to "build me the best VBT option possible" — the highest-leverage improvements available
to the existing camera + colored-marker approach, no new hardware.)

---

## 0. Decisions locked in via clarifying questions

- **Tap-to-calibrate marker color**: yes. Replaces `MarkerColorMatcher`'s fixed, guessed
  hue/saturation/value thresholds with a color sampled from the user's actual marker in their
  actual lighting/background, every time they record. This is the single highest-leverage fix
  available without new hardware — it directly targets the exact failure mode that broke the
  first real test (background objects sharing the fixed threshold's hue range).
- **Path preview**: yes. After tracking, draw the tracked path as a line over the first frame
  so the user can visually confirm tracking followed the real bar before trusting the numbers,
  rather than being asked to trust an opaque number.
- **Tips**: a small, dismissible card on the `RECORDING` step (marker color, calibration
  object, camera placement) — dismissal persists via DataStore (same pattern as the existing
  update-banner dismiss), so it stays out of the way once acknowledged but isn't buried behind
  a tap the first several times.

---

## 1. Objective

The marker-tracking pipeline works end-to-end now (Sprint 28's blob-detection fix), but a fixed
guessed color threshold and "trust the numbers with no way to verify" are the two biggest
remaining trust gaps. Close both, plus reduce the "why is this pink and not that pink" learning
curve with in-app guidance — all within the existing record/import → calibrate → analyze
architecture, no new hardware, no live/real-time tracking (that's a different architecture,
explicitly out of scope here, same as noted in the prior sprint's KNOWN GAP).

---

## 2. Core features & acceptance criteria

### 2.1 Tap-to-calibrate marker color
- New `domain` (well, `util/barpath`) type `MarkerColorProfile(hueCenter: Double, hueTolerance:
  Double, minSaturation: Double, minValue: Double)` with `fun matches(r: Int, g: Int, b: Int):
  Boolean` — reuses `MarkerColorMatcher.rgbToHsv` (unchanged, still pure/tested) for the
  conversion. Hue comparison is circular (`min(|a-b|, 360-|a-b|)`) since hue wraps at 360°.
- `MarkerColorProfile.sample(r, g, b)`: builds a profile centered on the sampled hue, with
  saturation/value floors set *below* the sample (documented approximation: sample −0.25,
  floored at 0.2) so real-world lighting variation across the clip doesn't fall outside the
  matched range — same "explicit approximation, not silent" style as `RpeChart`.
  `MarkerColorProfile.default()` keeps the old fixed magenta range as a fallback constant (used
  only if sampling somehow fails — the flow always requires a real tap, this is defensive, not
  a normal path).
- Calibration screen becomes a 3-tap sequence, not 2: **first tap = the marker itself** (sampled
  as the average RGB of a small pixel neighborhood around the tap, not a single noisy pixel),
  then the existing two reference-length taps. Dynamic instruction text tracks which tap is
  next. The marker-sample dot renders in `PowerAmber` (distinct from the existing `MarkerGreen`
  reference-point dots). The existing "RESET POINTS" button clears all three taps together —
  one "start over on this frame" action, not three separate resets.
- `BarPathCaptureViewModel.onConfirmCalibration` requires a non-null color profile (sampled from
  the marker tap) in addition to the two reference points before proceeding; error message
  mirrors the existing "tap two points" one.
- `BarPathFrameTracker.trackMarker`/`findMarkerCentroid` take a `MarkerColorProfile` parameter
  and call `profile.matches(...)` instead of the old hardcoded `MarkerColorMatcher.matchesRgb`.
  Blob detection + nearest-neighbor tracking (Sprint 28 fix) is unchanged — this only changes
  *what counts as a match*, not how matches are grouped/tracked.
- **Acceptance**: recording the same clip with a marker color the fixed threshold used to miss
  or a background color it used to falsely match should track correctly once the real color is
  sampled, because the match range is now centered on reality instead of a guess.

### 2.2 Tracked path preview
- `BarPathCaptureUiState` gains `trackedSamples: List<BarPathSample> = emptyList()`, populated
  in `onConfirmCalibration` right after `barPathFrameTracker.trackMarker(...)` succeeds (the
  same samples already used to compute `BarPathAnalysis` — no extra tracking pass).
- `ResultsStep` gains a "TRACKED PATH" section above the stat rows: the stored
  `calibrationFrame` bitmap with a `Canvas` polyline overlay connecting `trackedSamples` in
  pixel order (same box-sizing/scale pattern `CalibrationStep`'s tap-point dots already use —
  `onSizeChanged` + a scale factor from frame space to displayed box space). Start point marked
  distinctly (small green dot) from the end point (small red dot) so direction is visible at a
  glance; the line itself in a semi-transparent `NeonGreen`.
- **Acceptance**: a clean track (Sprint 28's fixed footage, hopefully) shows a smooth line
  roughly following the bar's real path. A noisy/bad track visibly shows a jagged or jumping
  line — the point is exactly to make bad tracking visually obvious before the user trusts the
  numbers or taps SAVE TO SET.

### 2.3 Dismissible usage tips
- `UserPreferencesDataStore` gains `barPathTipsDismissed` (boolean key, default `false`) +
  get/set, same pattern as `restTimerSoundsEnabled`/`lastDismissedUpdateVersion`.
  `UserRepository`/`UserRepositoryImpl` expose `getBarPathTipsDismissed()`/
  `setBarPathTipsDismissed(Boolean)`.
- `BarPathCaptureViewModel` exposes `tipsDismissed: StateFlow<Boolean>` and `onDismissTips()`.
- `RecordingStep` shows a small dismissible card (when `!tipsDismissed`) above the
  record/import controls: marker color vs. background contrast, calibration object choice
  (rigid, same plane as bar travel), camera placement (stationary, perpendicular, full ROM in
  frame, no backlight). An "✕" dismiss button calls `onDismissTips()` — persists via DataStore,
  so once dismissed it stays dismissed across future recordings (same UX language as the
  existing update banner's dismiss).
- **Acceptance**: first time on the RECORDING step, the tips card shows. After dismissing once,
  it never reappears (until/unless a future "reset tips" affordance is added — not in scope
  here, no such affordance exists for the update banner either).

---

## 3. Tech stack additions

None. Pure Kotlin (`MarkerColorProfile`, matching `MarkerColorMatcher`'s existing style) +
existing Canvas/Compose patterns already used for the calibration tap-point overlay + existing
DataStore dismiss-flag pattern. No new dependencies.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── data/datastore/UserPreferencesDataStore.kt   ← + barPathTipsDismissed
├── data/repository/UserRepositoryImpl.kt        ← + get/setBarPathTipsDismissed
├── domain/repository/UserRepository.kt          ← + get/setBarPathTipsDismissed
├── util/barpath/
│   ├── MarkerColorMatcher.kt                    ← unchanged (rgbToHsv still reused)
│   ├── MarkerColorProfile.kt                    ← new: sample(), default(), matches()
│   └── BarPathFrameTracker.kt                   ← trackMarker/findMarkerCentroid take a profile
└── presentation/screens/barpath/
    ├── BarPathCaptureViewModel.kt                ← markerSamplePoint/colorProfile state,
    │                                                trackedSamples, tips dismiss wiring
    └── BarPathCaptureScreen.kt                   ← 3-tap CalibrationStep, TRACKED PATH section
                                                     in ResultsStep, tips card in RecordingStep
```

No Room schema change, no navigation change.

---

## 5. Code style (extends existing CLAUDE.md rules)

- `MarkerColorProfile` follows `RpeChart`'s "explicit, documented approximation" precedent for
  the sample→tolerance conversion — the −0.25/floor-0.2 numbers are a stated first pass, not
  hidden magic constants.
- Dismiss-and-remember follows the exact existing `lastDismissedUpdateVersion` precedent — no
  new dismiss-flag pattern invented.
- The path-preview overlay reuses `CalibrationStep`'s existing frame-to-box scaling approach
  verbatim rather than introducing a second coordinate-mapping implementation.

---

## 6. Testing strategy

- `MarkerColorProfile`: pure, no Android dependency — unit tests for `sample()` building a
  sensible profile from a known RGB, `matches()` accepting the sampled color and a close
  variant, rejecting a clearly different hue, and the circular hue-distance wraparound (e.g.
  hue 350° vs 10° should be "close", not "340° apart"). `findBlobs`/`chooseTrackedBlob` from
  Sprint 28 are already pure and stay that way — this only changes what `Boolean` gets passed in.
- Path preview and tips card are UI-only, verified the same way the rest of this screen has
  been (`assembleGithubDebug` compiling clean, manual reasoning) — no device this session either
  unless the user tests again.

---

## 7. Boundaries

**Always do:**
- Keep `MarkerColorMatcher.rgbToHsv` and `MarkerColorMatcher.default()`-equivalent fallback
  intact — nothing about the existing pure color-math gets removed, only extended.
- Keep the Sprint 28 blob-detection + nearest-neighbor tracking fix completely unchanged; this
  spec only changes the match predicate fed into it.

**Ask first about:**
- Nothing anticipated — this is an accuracy/trust improvement on already-built, already-tested
  infrastructure, not new algorithmic risk.

**Never do:**
- Never silently fall back to the old fixed threshold without telling the user — if marker
  sampling somehow fails (e.g., `calibrationFrame` null), show an error, don't silently degrade
  to guessed thresholds and produce numbers the user has no reason to distrust.

---

## 8. Notes

This is the "make the free option as good as it can be" pass, not a pivot to different hardware
— NFC and AirTags were ruled out as technically infeasible for this (no public API for
high-frequency positional data), and a physical linear position transducer / wearable IMU are
real alternatives but require the user to buy separate hardware, which is out of scope for an
app feature. Real-time/live tracking during recording remains explicitly out of scope, same as
the prior sprint's note — this spec targets accuracy and trust of the existing
record-then-analyze pipeline, not a different pipeline.
