# SPEC — VBT: multi-rep support (track a whole set, per-rep velocities)

## Status: Draft — awaiting confirmation before implementation.

Tracking is solved (v0.68.0: clean path, sensible numbers). The remaining limit is "one rep per
recording" — a multi-rep clip only gets the first rep. Decided via clarifying questions: mark the
**first rep** (bottom + top) to lock the colour, then **auto-track the whole video and split it into
reps**; results show a **per-rep list + a velocity-drop chart**; reps are gated by a **minimum ROM
threshold** (auto).

---

## 1. Objective

Turn the single-rep tool into a **set** tool: record a whole set, mark once, and get each rep's
velocity — because the point of VBT is watching velocity fall across a set (fatigue → when to stop).

Three pieces:
1. **Whole-clip tracking.** The two first-rep marks still build the combined colour model (both
   lighting conditions); tracking then covers the ENTIRE video (forward from the mark to the end +
   backward from the mark to the start, merged + gap-filled), not just between the two marks.
2. **Automatic rep segmentation.** From the tracked vertical path, split the clip into concentric
   (lifting) phases — each bottom→top ascent whose range exceeds a fraction of the biggest rep's
   range (so small bounces / re-grips don't count as reps).
3. **Per-rep results + velocity-drop chart.** Run the existing analysis per rep window; show a list
   (Rep 1, 2, 3… — mean/peak velocity + ROM each) and a chart of mean velocity across the set.

### Acceptance criteria
1. A multi-rep clip, marked once on the first rep, tracks all reps (the replay path covers the whole
   set) and the results screen lists each rep with its mean/peak velocity + ROM.
2. Small bounces/re-grips are not counted as reps (ROM gate); a single-rep clip yields exactly one rep.
3. The velocity-drop chart shows mean velocity per rep across the set.
4. `assembleGithubDebug` green; the rep-segmentation logic is pure + unit-tested; existing tests green;
   zero `" lb"`.

---

## 2. Commands
```powershell
.\gradlew testGithubDebugUnitTest
.\gradlew assembleGithubDebug
```
Release per CLAUDE.md `## Release rules` (bump versionCode+versionName BEFORE the final build).

---

## 3. Changes

### A. Whole-clip tracking — `BarPathFrameTracker`
- New `trackPlateWholeClip(videoPath, tapAX, tapAY, atAMs, tapBX, tapBY, atBMs, onProgress, onSample)`
  — same combined colour model as `trackPlateTwoMark`, but the range is the WHOLE clip `[0, end]`:
  forward pass from the bottom mark's time to `end`, backward pass from the bottom mark's time to `0`,
  seeded at the bottom mark's position; `mergeDetections` + `fillGaps` as before. Reuses
  `segmentAtTime`/`detectPlateAlong`/`scaledArgb`. Raise the sample cap for this path
  (`MAX_SAMPLES_MULTIREP = 300`) so several reps keep enough temporal resolution for velocity.
  `trackPlateTwoMark` stays for the single-rep path (unused by the flow once multi-rep is default,
  kept as reference).

### B. Rep segmentation — new pure `RepSegmenter.kt` (`domain/util/`)
- `segment(samples, minRomFraction = 0.5): List<Pair<Long, Long>>` — from the vertical (yPx) series,
  a zigzag/hysteresis turning-point detector finds alternating bottoms (max yPx) and tops (min yPx)
  using a deadband (fraction of the clip's total ROM, so noise doesn't create turning points); each
  bottom→top ascent whose vertical range ≥ `minRomFraction × biggestRepRange` is a rep window
  `(bottomMs, topMs)`. Returns them in time order. Empty/one-rep clips return the single concentric
  (falls back to `ConcentricDetector`'s behaviour). Pure, no Android/IO — unit-tested with synthetic
  multi-rep series (3 clean reps → 3 windows; a small bounce between reps ignored; a single rep → 1).

### C. Per-rep analysis — `BarPathCaptureViewModel`
- `onConfirmScale` (the GET VELOCITY step, where pixels-per-meter + weight are known):
  `RepSegmenter.segment(samples)` → for each window run `analyzeBarPathUseCase.execute(samples, ppm,
  mass, repStart, repEnd)` → a `List<RepResult>` (`RepResult(index, analysis, trackedFrames)`).
  If segmentation yields nothing, fall back to the current single `ConcentricDetector` window (one
  rep). New `BarPathCaptureUiState.repResults: List<RepResult>`; keep `analysis`/`trackedFrames` set
  to the BEST rep (highest mean velocity) so the existing replay/share still work unchanged.

### D. Results UI — `BarPathCaptureScreen` RESULTS step
- New `SetResultsSection`: a per-rep list (Rep N · mean X m/s · peak Y m/s · ROM Z cm) + a
  `VelocityDropChart` (Canvas bar/line of mean velocity per rep — the set's velocity curve). The
  existing hero/replay/share for the best rep stays above/below it; when there's only one rep it
  looks like today. `WeightFormatter`/theme tokens only.
- Persistence: `onSave` writes EACH rep as a bar-path metric (`saveFreestandingBarPathMetrics` per
  rep for the standalone flow; for a set-linked recording, save the best rep to that set — one set
  log, one representative metric) so the ExerciseDetail velocity chart still works. (No Room schema
  change — reuses the existing per-exercise metric rows.)

### New/changed pure helpers (unit-tested)
- `RepSegmenter.segment(...)` — multi-rep windows, ROM-gated, hysteresis turning points.
- `VelocityDropChart` data prep (mean-velocity-per-rep list) is a trivial map; the Canvas is shell.

---

## 4. Code style
- Kotlin/Compose only; `RepSegmenter` pure + unit-tested; tracking/extraction/Compose in the shell.
  No hardcoded colors; metric only (m/s, cm, kg). Guard native/IO with `runCatching`.

## 5. Testing strategy
- Pure unit tests: `RepSegmenter` (3 reps → 3 windows in order; a sub-threshold bounce ignored; a
  single rep → 1 window; flat/too-few samples → the whole-clip fallback). Reuses
  `AnalyzeBarPathUseCase`'s existing tests per window.
- Whole-clip extraction, the results UI, and the chart are the shell — verified on device: all reps'
  dots tracked in the replay + a sensible per-rep list ARE the on-device self-check.

## 6. Boundaries
**Always** — keep record→mark→track→scale→analyze flow + track-then-watch + the two-mark colour
model; reuse `AnalyzeBarPathUseCase`/`detectPlateAlong`/`mergeDetections`/`fillGaps`; `ConcentricDetector`
stays the single-rep fallback; bump versions + progress log.
**Ask first** — a Room schema change for a real per-set/per-rep table (this sprint reuses existing
metric rows); deleting dormant single-rep/TrackerVit code; letting the user hand-edit rep boundaries.
**Never** — count a sub-threshold bounce as a rep; `" lb"`; XML/Java; hardcode colors; add a
dependency (reuses the existing frame path + colour code + Canvas).

## 7. Known risks (honest)
- Whole-clip tracking is a longer extraction (more frames over the whole video + the backward pass);
  bounded by `MAX_SAMPLES_MULTIREP`, under the progress bar — a real but acceptable time cost.
- Rep segmentation is heuristic: a paused/soft rep or an unusual tempo could merge or split a rep;
  the ROM gate + hysteresis handle the common cases, thresholds are first-pass and tunable. (The
  chosen "auto, no manual edit" scope means a mis-count needs a re-track, not a fix — flagged; a
  "delete a wrong rep" affordance is the obvious follow-up if it's needed.)
- Per-rep velocity still depends on the one plate scale (two-tap) applied to the whole set — correct
  as long as the camera doesn't move during the set (it shouldn't).
- Not device-verified this session — the tracked multi-rep replay + per-rep list are the gate.
