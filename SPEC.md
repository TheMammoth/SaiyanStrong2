# SPEC — VBT: tap-to-segment the plate + drift-free re-detection tracking

## Status: Draft — awaiting confirmation before implementation.

A response to repeated drift failures (the tracker climbing off the plate onto the lifter's body
on a deadlift). Decided via clarifying questions: **one-tap magic-wand selection** of the plate,
a **re-detect-each-frame** tracking engine (drift-free), **plate only**.

---

## 1. Objective

Stop the drift at its root by changing the tracking *paradigm*. Today's engine (OpenCV TrackerVit)
*follows a region* and accumulates drift — over a rep it can slide from the plate onto the body,
which is the same colour-agnostic failure we've been patching. The new paradigm: **re-detect the
actual plate in every frame independently**, so drift cannot accumulate — each frame finds the real
plate fresh. This is closer to how commercial phone-VBT apps work (detect the plate per frame).

Two pieces:
1. **One-tap plate segmentation ("magic wand").** The user taps the plate on the mark frame; a
   flood-fill grows outward over the plate's colour to outline the whole connected plate region.
   From that region we get a *precise, region-sampled* colour model (robust — averaged over the
   real plate, not a single pixel or a guessed marker) plus the plate's size and centre. The outline
   is shown so the user sees exactly what got selected (re-tap to redo).
2. **Drift-free re-detection tracking.** Each frame, find the blobs matching that plate colour model
   and pick the one that is the plate (nearest to the previous position AND the right size). The
   emitted point is the re-found plate's centre — it cannot gradually climb onto the body, because
   the body is not the plate's colour and re-detection has no memory to drift.

**Why this beats the earlier colour tracking (v0.51–0.55) that underperformed:** those failed on a
single-pixel/pale-marker colour sample and no size gate. Here the colour model is sampled over the
*whole segmented plate* (robust range) and blob choice is size-gated, so a small same-colour
distractor or a huge colour merge is rejected. It hinges on the plate being a reasonably distinct
colour (the user's blue bumper is — see risks).

**Target user:** a lifter who taps their plate once, sees it outlined, hits TRACK, and gets a clean
drift-free path.

### Acceptance criteria
1. Tapping the plate outlines the connected plate region (visible overlay); re-tap redoes it.
2. On the user's deadlift clip, the dot **stays on the plate through the whole rep** (or holds when
   the plate is occluded) and does **not** climb onto the body.
3. If no plate is found in a frame (occlusion/blur), the dot holds the last position; when the plate
   reappears it re-detects — no permanent loss, no drift.
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

### A. Plate segmentation — new pure `PlateSegmenter.kt` (`util/barpath/`)
- `segment(pixels: IntArray, w, h, tapX, tapY, tolerance): PlateSelection?` — BFS flood-fill from the
  tap over pixels within [tolerance] colour distance of the growing region's mean (HSV-based, reusing
  `MarkerColorMatcher.rgbToHsv`). Returns `PlateSelection`: the mask/centroid, bounding box, an
  estimated diameter, and a `MarkerColorProfile` built from the region's pixels (via the existing
  `MarkerColorRangeBuilder`). Null if the region is too small (a mis-tap on a tiny feature).
- Pure (operates on an ARGB `IntArray`); fully unit-testable with synthetic grids.

### B. Re-detection tracking — `BarPathFrameTracker`
- New `trackPlateByRedetection(videoPath, tapVideoX, tapVideoY, startMs, onProgress, onSample)`:
  - Mark frame: `scaledArgb` → `PlateSegmenter.segment` at the tap → the plate colour model +
    expected diameter + first centre. If segmentation fails, return empty (caller shows a retry).
  - Each frame: build the colour match mask for the model, `findBlobs`, then a new
    `choosePlateBlob(blobs, previousCentre, expectedDiameter)` — picks the blob that best combines
    nearness to the previous centre and closeness to the expected size, **rejecting** blobs far
    outside a size band (a tiny same-colour speck or a merged giant). Emit its centre; hold the last
    position when nothing qualifies (occlusion). Reuses the existing `startMs`, `MAX_SAMPLES` cap,
    `onProgress`, `onSample`.
- `choosePlateBlob(...)` is a pure top-level `internal fun` (like `chooseTrackedBlob`) — unit-tested.
- The `trackWithVit` path (TrackerVit + colour-anchored guard) is left **dormant** (kept, not
  deleted) — the re-detection path becomes the flow's primary tracker.

### C. Selection UX — `BarPathTrackPlaybackContent` + ViewModel
- The placing step becomes **tap-to-segment** instead of drag/pinch a box:
  - Tap (paused frame) → ViewModel segments the plate at that point → exposes the selection outline;
    an overlay draws the plate outline + centre. The **magnifier loupe** (v0.64.0) stays, to place
    the tap precisely.
  - A **TRACK** button confirms → runs `trackPlateByRedetection` with the track-then-watch progress
    bar (v0.63.0 flow reused unchanged), then plays the complete path.
  - RE-MARK clears the selection.
  - Optional (build only if it falls out cleanly): a small −/＋ to grow/shrink the flood tolerance if
    the auto-outline grabbed too much/little. Otherwise re-tap.
- ViewModel: `onConfirmTrack` (or a new `onSegmentTap`/`onConfirmTrack`) carries the tap point;
  `placementFrame` (already added) feeds both the loupe and the segmentation. New UiState field for
  the selection outline (mask bbox / polygon points) to draw.
- The drag/pinch box code is retired from this flow (kept in git history); the movable-box helpers
  (`VitBarTrackerSupport.initBox`) go dormant with the TrackerVit path.

### New/changed pure helpers (unit-tested)
- `PlateSegmenter.segment(...)` — flood-fill region + model (region grows, stops at colour edge,
  min-size guard, edge-safe).
- `choosePlateBlob(blobs, previousCentre, expectedDiameter)` — nearest + size-matched, size-band
  rejection.

---

## 4. Code style
- Kotlin/Compose only; segmentation + blob choice pure/Android-free/unit-tested; frame extraction,
  Bitmap, and Compose overlays in the untestable shell. No hardcoded colors; metric only. Guard every
  native/IO boundary (`runCatching`).

## 5. Testing strategy
- Pure unit tests: `segment` (grows over a same-colour disc, stops at the boundary, min-size null,
  edge-clamped tap), `choosePlateBlob` (nearest wins, size-mismatched distractor rejected, empty →
  null). Segmentation model colour sanity via the existing `MarkerColorRangeBuilder` tests.
- The per-frame extraction, the outline overlay, and playback are the shell — verified on device.
  The visible plate outline + the dot re-finding the plate ARE the on-device self-check.

## 6. Boundaries
**Always** — keep record-then-analyze + the track-then-watch progress flow + two-tap scale +
analysis math untouched; reuse `findBlobs`/`MarkerColorRangeBuilder`/`scaledArgb`/`computeFittedVideoRect`;
bump versions + progress log.
**Ask first** — deleting the now-dormant TrackerVit / box / colour-guard code (a later cleanup);
adding a tolerance slider as more than a small extra; any live-camera change.
**Never** — claim this makes tracking universal (it depends on a distinct plate colour); track
"plate + bar" (rejected — worse); `" lb"`; XML/Java; hardcode colors; add a dependency (all reuses
the existing OpenCV frame path + our colour code).

## 7. Known risks (honest)
- **Depends on a distinct plate colour.** Re-detection finds the plate by colour, so it shines when
  the plate colour is fairly unique in frame (the user's blue bumper is) and struggles if the gym is
  full of that colour. This is the trade for drift-free tracking; TrackerVit stays dormant as a
  fallback option if a future clip needs it.
- **Motion blur** on fast reps can thin the colour blob → a frame or two of hold, then re-detect
  (better than drifting).
- **Segmentation leak** if the tap lands on a low-contrast part (chrome hub bleeding into the floor)
  — mitigated by the min-region model + re-tap; guidance: tap the coloured rim.
- Not device-verified this session — the outline + drift-free dot are the acceptance gate.
