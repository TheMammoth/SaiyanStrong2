# SPEC — VBT: two-mark plate tracking (bottom + top) with bidirectional fill

## Status: Draft — awaiting confirmation before implementation.

Builds on v0.67.0 (tap-to-segment + drift-free re-detection). That fixed the body-climb; the dot now
rides the plate. The remaining issue: near lockout the plate is backlit/blurred and the colour match
drops for a few frames ("misses at the top"), while the bottom (where the user marks) tracks
perfectly. Decided via clarifying questions: **always mark two points** (bottom + top), **combine
both colour samples + track bidirectionally and fill misses**.

Note confirmed to the user: the app already pre-analyses the whole clip (the TRACK progress bar is a
full pass), so the misses are detection misses, not live lag — no change needed there.

---

## 1. Objective

Two anchors instead of one:
1. **Combined colour model.** Marking the plate at the bottom AND the top samples the plate in *both*
   lighting conditions (the top is backlit). The union of both samples builds one
   `MarkerColorProfile` that matches the plate across the whole ROM → far fewer misses at the top.
2. **Bidirectional fill.** Track **forward from the earlier mark** and **backward from the later
   mark** over the between-marks range; for each frame, take whichever pass actually detected the
   plate (a miss in one pass is usually covered by the other, since the top mark's backward pass is
   confident exactly where the bottom's forward pass is weakest). Any frame still missed by both is
   linearly interpolated from its neighbours. Result: a complete path from bottom to top.

**Target user:** marks the plate at the bottom and the top of the lift, hits TRACK, and gets a clean
gap-free path through the whole rep.

### Acceptance criteria
1. The flow requires two marks: tap the plate at the bottom, then at the top (guided prompts); both
   selections shown as circles; TRACK enabled only once both are set.
2. On the user's deadlift clip, the dot stays on the plate through the **top/lockout** (the misses
   are filled), not just the bottom.
3. If one mark's segmentation fails, it falls back to single-mark tracking from the good mark rather
   than erroring.
4. `assembleGithubDebug` green; new pure logic unit-tested; existing tests green; zero `" lb"`.

---

## 2. Commands
```powershell
.\gradlew testGithubDebugUnitTest
.\gradlew assembleGithubDebug
```
Release per CLAUDE.md `## Release rules` (bump versionCode+versionName BEFORE the final build).

---

## 3. Changes

### A. Two-mark tracking — `BarPathFrameTracker`
- New `trackPlateTwoMark(videoPath, tapAX, tapAY, atAMs, tapBX, tapBY, atBMs, onProgress, onSample)`:
  - Segment the plate on each mark frame (`PlateSegmenter`), collecting *both* regions' HSV samples;
    build ONE combined `MarkerColorProfile` (`MarkerColorRangeBuilder.build(samplesA + samplesB)`);
    expected diameter = mean of the two. If one segmentation fails, use the other's model + anchor
    (single-mark fallback); if both fail, return empty.
  - Range `[lo, hi]` = the two mark times sorted; timestamp grid over it (same `MAX_SAMPLES` cap).
  - **Forward pass** seeded at the earlier mark's position, **backward pass** seeded at the later
    mark's position — each a per-frame colour re-detection (`findBlobs` + `choosePlateBlob`) that
    returns the detected centre or **null on a miss** (no internal hold, so misses are explicit).
  - **Merge** (pure `mergeDetections`): per timestamp prefer the forward detection, else the
    backward detection, else null. **Fill** (pure `fillGaps`): linearly interpolate remaining nulls
    between the nearest detected neighbours; clamp leading/trailing nulls to the nearest detection.
  - Emit the merged, gap-filled samples (full-res), streaming via `onSample`, progress via
    `onProgress` across both passes.
- Refactor the per-frame detect step out of `trackPlateByRedetection` into a shared
  `detectPlateAlong(videoPath, profile, expectedDiameter, seedCentre, timestamps)` that drives a
  given ordered timestamp list (ascending forward / descending backward) and returns a
  `Map<Long, Pair<Double,Double>?>` (downscaled-space centre or null). `trackPlateByRedetection`
  (single-mark) stays as the fallback path.
- New pure `mergeDetections(fwd, bwd)` and `fillGaps(points)` — top-level `internal fun`s, unit-tested.

### B. UX — `BarPathTrackPlaybackContent` + ViewModel
- The placing step now collects **two marks**:
  - Prompt guides: "Tap the plate at the BOTTOM" → tap → segment → circle A. Then "Now scrub to the
    TOP and tap the plate" → tap → segment → circle B.
  - Both selection circles drawn (A and B); the loupe centres on the latest tap.
  - Once both are set, another tap re-does the second (top) mark; a RE-MARK clears both.
  - TRACK shows only when both marks exist → `onConfirmTrack` runs `trackPlateTwoMark`, reusing the
    track-then-watch progress bar + finished-path playback unchanged.
- ViewModel state: `markA: PlateMark?`, `markB: PlateMark?` (`PlateMark` = tap point + atMs +
  `PlateSelectionUi`) replace the single `plateSelection`/`markMs`/`markerSamplePoint`. `onSegmentTap`
  fills A, then B, then replaces B. `onConfirmTrack()` requires both. `onReMark` clears both.
  `isMarked` unchanged (`trackedSamples>=2 && !isTracking`).

### New/changed pure helpers (unit-tested)
- `mergeDetections(fwd: List<P?>, bwd: List<P?>): List<P?>` — elementwise `fwd ?: bwd`.
- `fillGaps(points: List<P?>): List<P>` — linear interpolation over nulls, ends clamped; all-null → empty.
- (`detectPlateAlong` uses `findBlobs`/`choosePlateBlob`, already tested.)

---

## 4. Code style
- Kotlin/Compose only; merge/fill/segmentation pure + unit-tested; frame extraction, Bitmap, Compose
  overlays in the shell. No hardcoded colors; metric only. Guard native/IO with `runCatching`.

## 5. Testing strategy
- Pure unit tests: `mergeDetections` (prefers fwd, falls back to bwd, both-null→null), `fillGaps`
  (interpolates a middle gap, clamps leading/trailing nulls, all-null→empty, no-gap unchanged).
- Two-mark extraction/merge on real frames is the shell — verified on device: the plate outline at
  both marks + a gap-free dot through the top ARE the on-device self-check.

## 6. Boundaries
**Always** — keep record-then-analyze + track-then-watch + two-tap scale + analysis math untouched;
reuse `PlateSegmenter`/`findBlobs`/`choosePlateBlob`/`MarkerColorRangeBuilder`/`scaledArgb`; single-mark
path stays as fallback; bump versions + progress log.
**Ask first** — more than two marks; deleting dormant TrackerVit/box code; any live-camera change.
**Never** — claim it's universal (still colour-dependent); `" lb"`; XML/Java; hardcode colors; add a
dependency (reuses the existing frame path + colour code).

## 7. Known risks (honest)
- Still colour-dependent (a distinct plate colour). Two lighting samples widen the model, which also
  slightly raises the chance of matching a same-colour distractor — mitigated by `choosePlateBlob`'s
  size + nearest gates and the two anchors.
- The backward pass doubles frame extraction over `[lo, hi]` (another set of `getFrameAtTime` seeks)
  — bounded by `MAX_SAMPLES`, shown under the same progress bar; a real but acceptable time cost.
- Interpolated gaps are straight lines — fine for a few missed frames, would smear a long occlusion
  (rare between two good marks).
- Not device-verified this session — the two circles + a gap-free top are the acceptance gate.
