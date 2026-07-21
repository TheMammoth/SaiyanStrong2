# SPEC — VBT: per-rep marking for reliable multi-rep sets

## Status: Draft — awaiting confirmation before implementation.

The v0.69.0 whole-clip auto multi-rep is fragile (no top anchor for reps 2+, same-colour distractors
over a long range, coarse sampling) — real footage showed it drifting onto a rack plate / stalling
at overhead. The bounded two-mark single rep (v0.68.0) is reliable. Decided via clarifying questions:
**mark each rep** (bottom + top), track each with the proven two-mark path, stitch into a set. No
physical marker.

---

## 1. Objective

Make multi-rep as reliable as the single rep by reusing the single-rep tracker per rep. For a set,
the user marks the bottom and top of **each** rep; every rep is tracked with `trackPlateTwoMark`
(bounded `[bottom, top]`, anchored at both ends — the thing that works), then the reps are combined
into a set with per-rep velocities + the velocity-drop chart. Auto whole-clip tracking is dropped as
the multi-rep path (kept dormant).

### Acceptance criteria
1. The player collects marks in pairs: "rep 1 bottom → rep 1 top → rep 2 bottom → rep 2 top → …",
   guided by the prompt; every selection circle is shown; TRACK enables once ≥1 complete rep (an even
   number ≥2 of marks) exists.
2. Each rep tracks as reliably as the single-rep two-mark path (bounded + both-ends anchored) — no
   drift onto rack plates / stalling at the top that the whole-clip path showed.
3. Results list each rep's mean/peak velocity + ROM with the velocity-drop chart; a single rep (2
   marks) looks like today.
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

### A. Per-rep tracking — `BarPathFrameTracker`
- New `trackPlateReps(videoPath, reps: List<RepMarks>, onProgress, onSample): List<BarPathSample>` —
  for each rep (a bottom+top mark pair) runs the existing `trackPlateTwoMark` over that rep's
  `[bottom, top]` window, concatenating the per-rep samples in time order (each rep independently
  bounded + both-ends anchored = the reliable path). Progress spans all reps. `RepMarks` =
  `(bottomTapX/Y/Ms, topTapX/Y/Ms)` (primitives, no presentation types in the tracker).
- Reuses `trackPlateTwoMark`/`segmentAtTime`/`detectPlateAlong` unchanged. `trackPlateWholeClip`
  (v0.69.0) is left dormant.

### B. Multi-mark UX — `BarPathTrackPlaybackContent` + ViewModel
- ViewModel state: `marks: List<PlateMark>` replaces `markA`/`markB`. `onSegmentTap` appends a mark
  (each tap = one mark). RE-MARK clears all; a dedicated UNDO removes the last mark (so a mis-tap
  doesn't force a full restart).
- Prompt (player): even count → "Tap rep {n+1} BOTTOM (or TRACK)"; odd count → "Tap rep {n} TOP".
  All selection circles drawn (bottoms one tint, tops another so pairs read). TRACK shows when
  `marks.size >= 2 && marks.size % 2 == 0`.
- `onConfirmTrack` builds `reps = marks.chunked(2).map { it[0] to it[1] }` and starts the app-scoped
  runner (v0.69.1) with the rep list; the runner runs `trackPlateReps`.
- `TrackRequest`/`TrackState` carry the rep-mark list (for the leave-and-return restore).

### C. Per-rep analysis — `BarPathCaptureViewModel`
- `onConfirmScale`: the rep windows are known from the marks (`reps.map { it.first.atMs to
  it.second.atMs }`, each ordered) — no `RepSegmenter` guessing needed. Analyse each window over the
  combined samples → `List<RepResult>` (as today). `RepSegmenter` stays only as the fallback for a
  legacy single-window path (kept, not used here). Best rep still drives the replay/share.

### D. Results UI
- Unchanged from v0.69.0: `SetResultsSection` (per-rep list) + `VelocityDropChart` when ≥2 reps.

### New/changed pure helpers (unit-tested)
- `repWindowsFromMarks(reps): List<Pair<Long,Long>>` — pairs → ordered (bottomMs, topMs) windows
  (each min/max so tap order within a pair doesn't matter). Unit-tested.
- (Tracking/extraction stay in the untestable shell.)

---

## 4. Code style
- Kotlin/Compose only; window/pairing logic pure + unit-tested; tracking/extraction/Compose in the
  shell. No hardcoded colors; metric only. Guard native/IO with `runCatching`.

## 5. Testing strategy
- Pure unit tests: `repWindowsFromMarks` (pairs → windows, tap-order-within-pair normalised, ordered).
- Per-rep tracking + the multi-mark UX are the shell — verified on device: each rep's circle pair +
  a clean per-rep path (as reliable as the single rep) ARE the on-device self-check.

## 6. Boundaries
**Always** — reuse `trackPlateTwoMark` per rep (the proven path); keep the app-scoped runner (v0.69.1),
track-then-watch, two-tap scale, per-rep results UI; bump versions + progress log.
**Ask first** — deleting the dormant whole-clip/`RepSegmenter`/TrackerVit code; any Room schema change;
more than one video's worth of set.
**Never** — reintroduce the fragile whole-clip auto path as the default; `" lb"`; XML/Java; hardcode
colors; add a dependency.

## 7. Known risks (honest)
- More taps per set (2 per rep) — the deliberate trade for reliability (the user chose this over the
  fragile auto path).
- Time scales with rep count (each rep is its own bounded track) — but each is short, and the
  app-scoped runner means the wait is interruptible-safe.
- A mis-tapped mark needs UNDO/RE-MARK; boundaries come from the taps, so a sloppy bottom/top tap
  shifts that rep's window (the user controls it — more predictable than auto-segmentation).
- Not device-verified this session — per-rep circle pairs + clean per-rep paths are the gate.
