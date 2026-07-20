# SPEC — VBT: track the bar-end/sleeve hub (small, tilt-robust) + magnifier placement

## Status: Draft — awaiting confirmation before implementation.

Builds on v0.63.0 (movable/resizable start box, track-then-watch). This sprint is mostly
**guidance + small-target tuning**, not an algorithm change — TrackerVit already tracks whatever
box the user places. Decided via clarifying questions: recommend the **bar end / sleeve hub**,
add a **magnifier loupe** for precise placement, and start the box **smaller (hub-sized)**.

---

## 1. Objective

The user asked whether to track the plate or the bar centre, reasoning a smaller target distorts
less under camera tilt. The honest CV answer: the whole bar is rigid, so any fixed point gives the
same velocity; the tracked-point choice affects *lock reliability*, not the number much. The
**sleeve hub** (the bright chrome bar-end poking through the plate centre — visible in the user's
footage) is a strong target: small, round (so its **centre is tilt-invariant** — a circle projects
to an ellipse but the centre stays the centre), high-contrast, rigidly on the bar. The literal
"centre of the bar" is occluded behind the neck on a squat/OHP, so the hub is what "small central
bar target" actually means.

The movable box already lets the user place the tracker anywhere — so the work is: **guide them to
the hub, make a small target easy to place accurately, and start the box hub-sized.**

**Target user:** a lifter filming side-on, sometimes with a slightly tilted camera, who wants the
most accurate/robust tracking without fiddling.

### Acceptance criteria
1. The player guidance recommends tapping the bar end / sleeve hub (plate offered as fallback).
2. Tapping drops a **hub-sized** box (~0.07×shorter side) by default; pinch still scales up to a plate.
3. While placing, a **magnifier loupe** shows a zoomed view of the frame under the box centre with a
   crosshair, so a small hub can be centred precisely.
4. Tracking a small hub still produces a clean path + plausible numbers (the two-tap plate SCALE
   step is unchanged — scale reference is independent of what's tracked).
5. `assembleGithubDebug` green; new pure helper unit-tested; existing tests green; zero `" lb"`.

---

## 2. Commands
```powershell
.\gradlew testGithubDebugUnitTest
.\gradlew assembleGithubDebug
```
Release per CLAUDE.md `## Release rules` (bump versionCode+versionName BEFORE the final build).

---

## 3. Changes

### A. Guidance / copy — `BarPathTrackPlaybackContent.kt`, tips
- Player prompt (unmarked): "Scrub to the lift, then tap the **bar end / sleeve hub** (the bright
  centre) — or a plate". Placing prompt unchanged ("Drag the box onto it, pinch to size, then TRACK").
- Retry/failure copy broadened away from "plate" to "the hub, a collar, or a plate".

### B. Hub-sized default box — `BarPathTrackPlaybackContent.kt`
- Default box side fraction `0.18 → 0.07` of the shorter video side (a hub/collar). Pinch range
  unchanged (`24px .. shorterSide`), so the user can still grow it to a full plate.

### C. Magnifier loupe — `BarPathTrackPlaybackContent.kt` + ViewModel
- While placing (box exists, not tracking/marked), show a circular **loupe** (~110 dp) pinned to a
  top corner: a zoomed (~3×) crop of the paused frame centred on the box centre, with a crosshair
  at the exact centre — so a small hub can be landed precisely (directly fixes "when I tap I'm not
  accurate").
- The loupe needs the paused frame's pixels. New `BarPathCaptureUiState.placementFrame: Bitmap?`;
  new `BarPathCaptureViewModel.onPlaceFrame(atMs)` extracts the frame via
  `barPathFrameTracker.extractFrameAt` (off-thread) and stores it. The player calls `onPlaceFrame`
  when it drops/repositions the box (paused); the loupe draws from `placementFrame.asImageBitmap()`.
  Cleared on RE-MARK and on leaving the player.
- Loupe source-rect math is a pure helper `loupeSource(centerX, centerY, bitmapW, bitmapH, loupePx,
  zoom): LoupeSrc` (src offset+size clamped inside the bitmap) — unit-tested. The Compose `drawImage`
  (src→dst) is the untestable shell.

### D. Scale / correction — unchanged
- The two-tap plate SCALE step is independent of the tracked target (you track the hub, you still
  tap a plate's edges for the metre scale) — no change. `apparentDiameterPx` depth-drift correction
  still works from the box width (small but ~constant → correction ≈ 1). Auto-plate-scale stays
  deferred (it would only apply when the box IS a plate).

### New/changed pure helper (unit-tested)
- `loupeSource(...)` in `BarPathTrackPlaybackContent.kt` — clamps the zoom window inside the bitmap
  (near an edge it shifts, doesn't read out of bounds); returns integer src offset + size.

---

## 4. Code style
- Kotlin/Compose only; pure helper Android-free + unit-tested; frame extraction / `drawImage` in the
  untestable shell. No hardcoded colors (theme tokens). Metric only.

## 5. Testing strategy
- Pure unit tests: `loupeSource` (centre window, edge clamp both axes, zoom size, degenerate bitmap).
- The loupe rendering, box gestures, and TrackerVit are the shell — verified on device. The loupe +
  the tracked path following the hub ARE the on-device self-check.

## 6. Boundaries
**Always** — keep record-then-analyze; reuse `computeFittedVideoRect`/`extractFrameAt`; keep the
two-tap scale + analysis math untouched; bump versions + progress log; guard frame extraction
(`runCatching`).
**Ask first** — auto-scale from a plate box; deleting dormant colour/marker files; any live-camera
change.
**Never** — claim tracking the hub changes the physics/velocity math (it doesn't — same rigid bar);
`" lb"`; XML/Java; hardcode colors; add a dependency (all Compose + existing OpenCV/retriever path).

## 7. Known risks
- Small target = more motion-blur / lost-lock risk on fast reps than a big plate; mitigated by the
  hub's high contrast + the existing hold-on-low-confidence guard, but a very fast/blurry hub may
  still need the user to grow the box or track the plate instead — that fallback is why plate stays
  offered.
- Loupe adds a frame extraction on placement (~tens of ms, off-thread) — a brief delay before it
  appears; acceptable.
- Not device-verified this session — the on-device loupe + hub-track path is the acceptance gate.
