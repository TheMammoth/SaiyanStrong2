# SPEC — VBT: incremental per-rep marking (mark a rep, auto-track, repeat)

## Status: Draft — awaiting confirmation before implementation.

Per-rep marking (v0.70.0) is reliable but clunky — marking every rep upfront piles markers on screen.
Fix, per the user: do it **one rep at a time**. Decided via clarifying questions: after tapping a
rep's bottom+top it **auto-tracks that rep**, clears the markers, and you mark the next; only the
**current rep + a "Reps: N" counter** show; **REDO LAST REP** fixes just the most recent rep.

---

## 1. Objective

An incremental, low-clutter set flow: tap bottom → tap top → the rep auto-tracks (you see the dot
follow it) → the markers clear → tap the next rep → … → DONE → velocity numbers for the whole set.
Each rep still uses the reliable bounded two-mark tracker; the only change is *when* you mark and how
much is on screen.

### Acceptance criteria
1. Tapping a rep's bottom then top auto-tracks that one rep (bounded two-mark), shows its dot/trail,
   then clears the two markers and increments a "Reps: N" counter.
2. Only the current rep's markers (0–2) are on screen at a time — never the whole set's markers.
3. REDO LAST REP drops just the most recent tracked rep so it can be re-marked; earlier reps stay.
4. DONE (available once ≥1 rep is tracked) goes to the scale step, then per-rep results for the whole
   set — identical results/velocity-drop chart to v0.70.0.
5. The set survives leaving/returning to the screen (app-scoped, like v0.69.1).
6. `assembleGithubDebug` green; pure logic unit-tested; existing tests green; zero `" lb"`.

---

## 2. Commands
```powershell
.\gradlew testGithubDebugUnitTest
.\gradlew assembleGithubDebug
```
Release per CLAUDE.md `## Release rules` (bump versionCode+versionName BEFORE the final build).

---

## 3. Changes

### A. Runner accumulates the set — `BarPathTrackingRunner`
- Replace the "track the whole set at once" model with per-rep accumulation. The runner holds
  `completedReps: List<CompletedRep>` (`CompletedRep(bottomMark, topMark, samples)`) and survives
  ViewModel clearing (app-scoped). New:
  - `startRep(videoPath, videoWidthPx, videoHeightPx, bottom: PlateMark, top: PlateMark)` — tracks
    that one rep via `BarPathFrameTracker.trackPlateTwoMark`, reporting progress; on completion
    APPENDS a `CompletedRep`. On failure, emits a message and does not append.
  - `redoLast()` — drop the last `CompletedRep`.
  - `clear()` — reset.
- State (`SetTrackState`) exposes `completedReps`, `trackingRep`/`progress`, the video info (for
  restore), and any `failedMessage`. The combined set samples = `completedReps.flatMap { it.samples }`.

### B. Incremental UX — `BarPathCaptureViewModel` + player
- ViewModel: `currentMarks: List<PlateMark>` (0–2, the rep being placed) replaces the whole-set
  `marks`. `onSegmentTap` appends to `currentMarks`; when it reaches 2, it calls `runner.startRep(...)`
  with the pair and clears `currentMarks` (auto-track). The completed-rep count + combined samples
  come from the runner's state (mirrored into uiState: `repCount`, `liveSamples`, `isTracking`,
  `trackingProgress`).
- `onUndoMark` removes the last *current* (un-tracked) mark. `onRedoLastRep` → `runner.redoLast()`.
  `onReMark`/`onRetry`/`loadVideo` → `runner.clear()` + clear `currentMarks`.
- `onDone()` (replaces the old TRACK confirm) is enabled once `repCount >= 1` and no rep is mid-track;
  it just advances to the scale step (`onGetVelocityNumbers`) using the combined samples.
- Player: prompt cycles "Tap rep {N+1} BOTTOM" / "Now tap rep {N+1} TOP"; a "Reps: N" badge; the
  current rep's 0–2 circles only; a small progress overlay while a rep tracks; after a rep tracks,
  its trail shows until the next bottom tap (then clears). Bottom buttons: DONE (≥1 rep) + REDO LAST
  REP (≥1 rep) + UNDO (a placed but un-tracked mark).

### C. Per-rep analysis — `BarPathCaptureViewModel`
- `onConfirmScale`: the rep windows come from the runner's `completedReps`
  (`repWindowsFromCompleted` — each rep's `(min, max)` of its two marks' ms); analyse each over the
  combined samples → `List<RepResult>` exactly as v0.70.0. Best rep drives replay/share; `onSave`
  saves each rep. `repWindowsFromMarks` (v0.70.0) generalised/kept for the pure windowing.

### D. Results UI
- Unchanged from v0.70.0 (`SetResultsSection` + `VelocityDropChart`).

### New/changed pure helpers (unit-tested)
- `repWindowsFromCompleted(reps): List<Pair<Long,Long>>` — the completed reps' bottom/top ms →
  ordered windows (mirrors `repWindowsFromMarks`). Unit-tested.

---

## 4. Code style
- Kotlin/Compose only; windowing pure + unit-tested; tracking/extraction/Compose in the shell. No
  hardcoded colors; metric only. Guard native/IO with `runCatching`.

## 5. Testing strategy
- Pure unit tests: `repWindowsFromCompleted` (per-rep windows, order-normalised, count matches reps).
- The incremental per-rep UX + tracking are the shell — verified on device: tap-tap-track-repeat with
  a clean single-rep dot each time + the "Reps: N" counter + a correct per-rep results list ARE the
  on-device self-check.

## 6. Boundaries
**Always** — each rep still uses the reliable bounded two-mark `trackPlateTwoMark`; keep the app-scoped
runner (now accumulating), track-then-watch progress, two-tap scale, per-rep results UI; bump versions
+ progress log.
**Ask first** — deleting dormant whole-clip/`trackPlateReps`/`RepSegmenter`/TrackerVit code; any Room
schema change; auto-detecting rep boundaries again.
**Never** — reintroduce the fragile whole-clip auto path; show the whole set's markers at once (the
clutter this fixes); `" lb"`; XML/Java; hardcode colors; add a dependency.

## 7. Known risks (honest)
- Auto-track on the 2nd tap means a mis-tapped top tracks a wrong window — mitigated by REDO LAST REP
  (re-mark that rep) and UNDO (before the 2nd tap lands).
- The runner now models a set (list of reps) instead of one pass — a bit more state, but it keeps the
  leave-and-return survival for the whole set.
- Total time still scales with rep count (each rep its own short track), now spread across the set as
  you mark — feels faster than one long pass even if similar total.
- Not device-verified this session — the tap-tap-track-repeat loop + per-rep results are the gate.
