# SPEC — VBT Marker Colour Advisor ("tell me which marker to use")

## Status: Draft — awaiting confirmation before implementation.

(Builds on the shipped marker-calibration step, v0.60.0.)

## 1. Objective

Right now the user has to GUESS a marker colour, then find out it clashes with their room
(yellow-green matched the green drawer; pale pink matched the background). Flip it: the
calibration screen already has live camera frames, so the app can **read the scene and
tell the user which marker colour to use** — the saturated colour most ABSENT from their
room — and **grade the marker they hold up** against the scene. No separate photo/upload;
it's automatic on the screen they already open.

**Target user:** anyone setting up a VBT recording who doesn't want to trial-and-error
marker colours.

## 2. Decisions (locked via clarifying questions)

1. **Automatic, live on the calibrate screen.** The moment the camera opens (before any
   tap) a banner reads the scene and shows the best marker colour(s) for this spot, plus
   which colours to avoid (already in the scene). Updates as the camera moves.
2. **Recommend AND grade.** After the user taps their marker it's rated GOOD / OK / BAD vs
   the scene (how crowded that colour is), so they get a clear "this clashes, switch"
   verdict — not just an upfront hint. Advisory; never blocks recording.
3. **Fixed palette of nameable colours.** Rank a small fixed palette (blue, purple,
   magenta, red, orange, yellow, green, cyan) by how absent each is from the scene, and
   name the winner(s) — easy to go find that colour on a real object.

## 3. How it works (design)

- The scene is summarised as a **saturated-hue histogram**: count only pixels above a
  saturation AND value floor (so grey walls, black plates, beige floor, shadows don't
  drown the signal), bucketed into hue bins (e.g. 24 bins × 15°).
- **Recommend:** for each palette candidate, measure how crowded its hue neighbourhood is
  in the histogram (fraction of counted pixels within ±tolerance of the candidate hue).
  Rank ascending → the least-present colours are recommended; the most-present are the
  "avoid" list. If the scene has almost no saturated colour at all, fall back to a sensible
  default order (blue, purple first — rarely in an indoor scene).
- **Grade a tapped marker:** take the sampled marker's hue, measure its neighbourhood
  crowdedness the same way → GOOD (empty band) / OK / BAD (crowded band).
- Both run on the downsampled frame the calibration analyzer already decodes — no extra
  decode, cheap per frame. The histogram is smoothed over the last few frames so the
  recommendation doesn't flicker.

## 4. Core features & acceptance criteria

### 4.1 Pure advisor (`MarkerColourAdvisor`, no Android dependency, unit-tested)
- `buildHueHistogram(pixels, width, height, bins, minSaturation, minValue): IntArray` —
  counts sufficiently-saturated/bright pixels into hue bins (uses `MarkerColorMatcher
  .rgbToHsv`, the [0,360) hue convention shared across the pipeline).
- `recommend(histogram): MarkerAdvice` — `MarkerAdvice(recommended: List<MarkerCandidate>,
  avoid: List<MarkerCandidate>)`, ranked. `MarkerCandidate(name, hueDeg)` from a fixed
  palette.
- `grade(histogram, hueDeg): MarkerGrade` — `GOOD | OK | BAD`.
- **AC:** a scene that is mostly green + orange recommends blue/purple and lists green &
  orange under avoid; a hue in a crowded bin grades BAD, a hue in an empty bin grades GOOD;
  a scene with no saturated colour returns the default recommendation without crashing.

### 4.2 Analyzer integration (`MarkerCalibrationAnalyzer`)
- Accumulates a smoothed scene histogram across recent frames; emits `MarkerAdvice` every
  frame in the existing `CalibrationFrameResult`.
- On a tap (when it samples the marker colour), also computes and emits the tapped
  marker's `MarkerGrade`.
- **AC:** the recommendation appears immediately (before any tap); the grade appears right
  after a tap and reflects the sampled colour.

### 4.3 UI (in the existing calibrate view, `BarPathCaptureScreen`)
- A banner above/over the preview: "Best marker here:" + coloured swatches + names for the
  top recommendations, and a small "avoid: …" row of swatches for colours already in the
  scene. Swatch colours drawn from each candidate's representative RGB (Compose Canvas
  circles — no image assets, no hardcoded theme-violating colours in normal UI chrome;
  these are data swatches, allowed).
- After a tap, the lock-status line also shows the marker grade: "Marker: GOOD ✓" /
  "OK" / "BAD — clashes, try blue" (colour: green/amber/red theme tokens).
- Recommendation is advisory — START RECORDING still gates only on the stable lock, exactly
  as today.

## 5. Project structure (new / changed)

```
util/barpath/
  MarkerColourAdvisor.kt          NEW  pure — scene histogram → recommended/avoid + grade
  MarkerCalibrationAnalyzer.kt    EDIT accumulate scene histogram; emit advice + tap grade
presentation/screens/barpath/
  BarPathCaptureScreen.kt         EDIT recommendation banner + swatches + marker grade line
```

`CalibrationFrameResult` gains `advice: MarkerAdvice?` and `markerGrade: MarkerGrade?`.
No Room change, no new dependency, no new permission.

## 6. Code style / constraints
- Compose only. Kotlin only. Metric units unaffected.
- `MarkerColourAdvisor` is pure (no Android imports), unit-tested; the analyzer/Compose
  layer stays the untested-on-device shell — same split as the rest of VBT.
- Reuse `MarkerColorMatcher.rgbToHsv` — do not introduce a second hue convention.

## 7. Testing strategy
- `MarkerColourAdvisorTest`: histogram counts only saturated/bright pixels; a green+orange
  scene recommends blue/purple and avoids green/orange; grade BAD in a crowded band, GOOD
  in an empty band; empty/desaturated scene → safe default, no crash; hue-wrap handled
  (red near 0/360 grades against both ends).
- Build: `.\gradlew testGithubDebugUnitTest` + `.\gradlew assembleGithubDebug` green; no
  `" lb"` in `app/src` Kotlin/strings.
- Device (owed): open calibrate → banner recommends a colour absent from the room → a
  clashing marker grades BAD, a recommended-colour marker grades GOOD and locks cleanly.

## 8. Boundaries

**Always:** keep the advisor pure + unit-tested; keep the recommendation advisory (never a
gate); reuse the shared hue conversion.

**Ask first:** persisting a "preferred marker colour" across sessions (out of scope now —
the scene can change); auto-picking/forcing a colour; any real ML.

**Never:** block recording on a BAD grade or a clash; add a permission; introduce a second
hue/HSV convention.

## 9. Known gaps / deferred
- Recommends from a fixed nameable palette, not an exact swatch (per decision — easier to
  match to a real object).
- Doesn't know what marker objects the user actually owns — it names a colour, not a
  product.
- Not device-validated this session (no emulator); the live banner + grade are themselves
  the on-device check.
