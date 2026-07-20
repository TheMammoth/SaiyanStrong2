# SPEC — VBT tracking polish: movable start box, track-then-watch, smoother trail

## Status: Draft — awaiting confirmation before implementation.

Builds on v0.62.0 (OpenCV TrackerVit, tap the plate). First real-footage test: it works —
the dot follows the plate, plausible numbers (peak 0.66 m/s, ROM 0.57 m on an OHP). Three
issues to fix, all confirmed via clarifying questions.

---

## 1. Objective

Take the working TrackerVit flow from "works" to "accurate and forgiving":

1. **Movable + resizable start box.** Today a tap immediately starts tracking from that exact
   point — an inaccurate tap seeds the tracker off the plate. Instead: a tap drops an
   **adjustable box** on the paused frame; the user **drags it onto the plate and pinches to
   size it to the plate**, then presses TRACK. A tight, well-placed box is also the single
   biggest accuracy lever for TrackerVit (it tracks the region it's initialized on).
2. **Track fully first, then watch.** Today tracking streams in the background while the video
   loops, so the path only fills in after replaying 2–3×. Instead: pressing TRACK runs the
   whole tracking pass once behind a **determinate progress bar**, then plays the **complete**
   path — accurate on the first watch, no replay-to-catch-up.
3. **Smoother displayed trail.** The drawn path is jagged (tracker jitter). Smooth the DISPLAYED
   trail more — both the live player overlay and the velocity-coloured replay overlay (the
   replay currently draws raw positions with no smoothing at all). The velocity NUMBERS keep
   their existing Savitzky-Golay smoothing in analysis — this is display-only.

### Acceptance criteria
1. Tapping the plate drops a box that can be dragged and pinch-resized on the paused frame; a
   TRACK button confirms and runs tracking from that box.
2. After TRACK, a progress bar runs to 100%, then the video plays with the full path already
   drawn — no second loop needed to see the complete trail.
3. Both the live trail and the replay path read as clean lines, not jagged.
4. `assembleGithubDebug` green; new pure helpers unit-tested; all existing tests green; zero `" lb"`.

---

## 2. Commands
```powershell
.\gradlew testGithubDebugUnitTest
.\gradlew assembleGithubDebug
```
Release per CLAUDE.md `## Release rules` (bump versionCode+versionName BEFORE the final build).

---

## 3. Changes

### A. Adjustable init box — `BarPathTrackPlaybackContent.kt`
- New local state `pendingBox: PendingBox?` where `PendingBox` = box centre + side in
  **video-pixel** space (so it survives container resize and maps straight to the tracker).
- When not yet tracking:
  - A **tap** (`detectTapGestures`) pauses the player and drops/repositions `pendingBox`
    centred on the tap, default side = `min(w,h) × 0.18` (today's fixed fraction, now a start
    value the user adjusts).
  - Once a box exists, `detectTransformGestures` on the same area **pans** (drag → move centre)
    and **zooms** (pinch → scale side, clamped to a sane min/max), both in video-px via
    `computeFittedVideoRect`.
  - Overlay: a neon rounded-rect outline + centre crosshair + corner ticks over the paused
    frame, drawn from `pendingBox` through the existing fitted-rect mapping.
  - A **TRACK THIS PLATE** `SaiyanButton` (shown while `pendingBox != null` and not tracking)
    calls the new `onConfirmTrack(centerXVideo, centerYVideo, sideVideo, atMs)`.
- `onMarkTap` is replaced by this place-then-confirm flow. RE-MARK clears `pendingBox` and the
  tracked path back to the placing state.
- Coordinate mapping reuses `computeFittedVideoRect` / `screenToVideoPx` unchanged (the proven
  letterbox math — same mapping that already lands the dot where tapped).

### B. Track-then-watch — `BarPathCaptureViewModel.kt` + player
- New `onConfirmTrack(centerX, centerY, sidePx, atMs)`:
  - Build a `BarInitBox` from the user's centre+side via a new pure
    `VitBarTrackerSupport.initBox(centerX, centerY, sidePx, frameW, frameH)` (clamps the box
    fully inside the frame; min side floor).
  - Set `isTracking = true`, `trackingProgress = 0f`, `markMs = atMs`.
  - Launch `trackWithVit(..., startMs = atMs, onProgress = { progress → update trackingProgress })`
    to completion (no reliance on live streaming for display now). On done: set `trackedSamples`,
    `isTracking = false`; the player seeks to `markMs` and plays the complete path.
- `BarPathCaptureUiState` gains `isTracking: Boolean = false` (`trackingProgress` already exists).
- Player: while `isTracking`, pause playback and show a **determinate** progress overlay
  ("Tracking the plate… NN%") over the frame; hide the tap/transform handlers. When it clears,
  auto-play from `markMs`.
- The old immediate-streaming `onMarkTap` path is removed from this flow (kept in the ViewModel
  only if still referenced by the gallery/other path — otherwise dropped). `onSample` streaming
  is no longer needed for display; `trackWithVit`'s `onProgress` drives the bar.

### C. Smoother trail — display only
- Live player (`TrackTrailOverlay`): bump `smoothedPathPoints` window `5 → 9`.
- Replay (`BarPathReplayContent`): it currently draws **raw** `TrackedFrame.xPx/yPx` for the
  ghost, the velocity-coloured progress line, and the cursor. Add a pure
  `smoothedFramePoints(frames, window = 9): List<Offset-source pairs>` (position-only moving
  average, index-aligned) and draw all three layers from it. Peak/sticking/start/end indices
  stay velocity-based (unchanged) — only the drawn positions are smoothed.

### New/changed pure helpers (unit-tested)
- `VitBarTrackerSupport.initBox(centerX, centerY, sidePx, frameW, frameH): BarInitBox?` — explicit
  user-chosen side; clamp inside frame; min-side floor; null for a degenerate frame.
- `smoothedFramePoints(...)` (in `BarPathReplayContent.kt`, mirrors `smoothedPathPoints`).
- `smoothedPathPoints` window default `5 → 9` (existing tests still hold; add a window-N case).

---

## 4. Code style
- Kotlin/Compose only; pure helpers Android-free and unit-tested; TrackerVit/CameraX/ExoPlayer in
  the untestable shell. No hardcoded colors (theme tokens). Metric only.

## 5. Testing strategy
- Pure unit tests: `initBox` (placement, clamp-inside-frame at edges, min side, degenerate null),
  `smoothedFramePoints` (short series unchanged, interior averaged), `smoothedPathPoints` window.
- The box UI / progress / TrackerVit are the untestable shell — verified on device. The live box +
  progress + finished path ARE the on-device self-check.

## 6. Boundaries
**Always** — keep record-then-analyze; reuse `computeFittedVideoRect`/`screenToVideoPx`; keep the
analysis SG smoothing untouched (display smoothing is separate); bump versions + progress log.
**Ask first** — auto-plate-scale from the box width (deferred lever); deleting the dormant
colour/marker files; any live-camera-preview change.
**Never** — touch the velocity math; introduce `" lb"`; XML/Java; hardcode colors; add a dependency
(all of this is Compose + the existing OpenCV path).

## 7. Known risks
- `detectTapGestures` + `detectTransformGestures` coexistence (place vs adjust) needs care — a
  tap must place, a drag must move, a pinch must resize, without fighting each other.
- Box → tracker init-box mapping is the same fitted-rect math already proven to land the dot on
  the tap; the resize just changes the side.
- Full-pass tracking time is bounded by `MAX_SAMPLES = 150` but is still a real wait on a long
  clip — the progress bar makes it honest; a frame-downscale speed-up is a deferred lever if it's
  still too slow.
- Not device-verified this session — the on-device flow is the acceptance gate.
