# SaiyanStrong — VBT: Live Tracked Playback (play now, mark anywhere)

## Status: Draft — awaiting confirmation before implementation.

(Replaces the previous "Mark-then-Watch Tracked Playback" spec — that shipped as v0.52.0/v0.52.1.
The user wants the tracking to feel live: press play → the video plays immediately and the marker
dot moves with the bar; and to be able to mark the bar partway through the clip, since the lift
doesn't start at the beginning. This removes the up-front "Tracking the marker…" wait from the
watch experience entirely and replaces the two-step mark→watch with a single video player.)

---

## 0. Decisions locked in via clarifying questions

- **Play now, dot follows as it tracks (not literal per-frame real-time).** Decoding one video
  frame is slower than the gap between frames, so genuine frame-locked real-time tracking would
  stutter. Instead: playback starts immediately with no wait; tracking runs in the background and
  streams positions in, so the dot follows the bar and fills in over the first play-through — and
  since the video loops, every loop after the first shows the marker moving fully live. With the
  v0.52.1 speedups this catches up almost instantly on a short clip.
- **One player, mark anywhere.** A single screen plays/loops the video. The user scrubs to where
  the lift is and taps the bar; that samples the marker color from the current frame and starts
  tracking from that point. No separate "mark first on a still, watch later" steps.
- **Track from the mark point onward.** Tracking covers [mark time → end], matching "the lift
  doesn't start at the beginning." Nothing before the mark is tracked.

---

## 1. Objective

Turn the bar-path capture into a single live player: get a video (record or import) → it plays and
loops immediately → scrub to the lift, tap the bar → the marker dot tracks the bar live (filling in
over the first loop, fully live thereafter) with its path highlighted → optionally add plate-scale +
weight for real velocity numbers → save.

Target users: unchanged — anyone verifying tracking on their own lift, now with zero wait and the
freedom to mark wherever the lift actually is.

---

## 2. Locked technical approach (not open questions)

- **Immediate playback + background streaming track.** On tap-to-mark, a background coroutine runs
  the (fast, capped) tracker from the mark time to the end, emitting each tracked position as it's
  found into a StateFlow the overlay reads. Playback never blocks on it.
- **Tap-to-mark samples the current frame.** Tapping pauses playback, reads the frame at the current
  position via one `getFrameAtTime` call, maps the tap from screen space to video-pixel space
  (inverse of the existing `computeFittedVideoRect` letterbox mapping — a new pure helper), samples
  the marker color from a small neighborhood there (existing `MarkerColorProfile.sample`), records
  the mark timestamp, and kicks off tracking. The user presses play to watch the dot move.
- **Reuse for numbers.** The streamed samples are reused by the optional analysis (no re-track), same
  as v0.52.0. Analysis waits for/uses the completed sample list.

---

## 3. Core features & acceptance criteria

### 3.1 Single live player
- After a video is obtained (record or import), the app goes straight to a player screen — no
  separate marking step, no blocking "Tracking…" screen. The video plays and loops immediately.
- Transport: play/pause + a scrub slider (reuse the existing replay transport).
- **Acceptance**: from a fresh video, the player is on screen and playing within the time it takes
  to open ExoPlayer; there is no full-screen PROCESSING gate before it.

### 3.2 Mark anywhere → live dot
- Tapping the video pauses it, samples the marker color from that exact frame/point, marks the
  timestamp, and starts background tracking from there. A dot marks where the color was sampled.
- Pressing play then shows the dot following the bar with a growing single-colour (neon) trail, from
  the mark point onward. On the first loop the dot fills in as tracking catches up; subsequent loops
  are fully live.
- A RE-MARK affordance re-samples from a new tap (clears the old track and re-tracks from the new
  point).
- **Acceptance**: on a real recording, scrubbing to the lift and tapping the bar makes the dot track
  the bar from that point on playback, with no up-front wait; re-tapping elsewhere re-marks.

### 3.3 Optional velocity numbers (unchanged in spirit)
- A GET VELOCITY NUMBERS action leads to the scale step (tap two plate edges + reference length +
  weight for standalone), then analyzes the already-tracked samples over the auto-detected concentric
  window (`ConcentricDetector`) and shows the existing results (numbers + velocity-coloured replay +
  save/share). No re-track.
- **Acceptance**: numbers require only the scale tap + weight; if tracking is still completing when
  requested, analysis uses the completed sample set (waits if necessary), never a partial one.

### 3.4 Both sources
- Recording in-app and gallery import both land on the same live player.

---

## 4. Tech stack additions

None. Reuses ExoPlayer/media3, `BarPathFrameTracker` (extended), `AnalyzeBarPathUseCase`,
`ConcentricDetector`, `computeFittedVideoRect`. New: a pure screen→video coordinate helper +
streaming/`startMs` support on the tracker.

---

## 5. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── util/barpath/BarPathFrameTracker.kt        ← trackMarker gains startMs (track from a mark time)
│                                                  + onSample streaming callback (emit each position)
├── presentation/screens/barpath/
│   ├── BarPathCaptureViewModel.kt             ← single PLAYER step; onMarkTap(videoX,videoY,ms)
│   │                                             samples color off-thread + starts streaming track;
│   │                                             _liveSamples StateFlow; markMs; trackingComplete;
│   │                                             onGetVelocityNumbers → SCALE; onConfirmScale reuses
│   │                                             samples. MARKING + track-PROCESSING steps removed.
│   ├── BarPathTrackPlaybackContent.kt         ← becomes the live PLAYER: video + tap-to-mark
│   │                                             (pause+report tap) + transport + streaming dot/trail
│   │                                             overlay + RE-MARK + GET VELOCITY NUMBERS
│   └── BarPathCaptureScreen.kt                ← PLAYER replaces MarkingStep in the flow; ScaleStep +
│                                                 ResultsStep unchanged; velocity replay unchanged
```

New pure helper `screenToVideoPx(tapX, tapY, containerW, containerH, videoW, videoH)` (inverse of
`computeFittedVideoRect`, returns null for taps in the letterbox margin) + unit tests. Existing
`currentSampleIndex` stays.

---

## 6. Code style (extends existing CLAUDE.md rules)

- Coordinate mapping (both directions) stays in pure, unit-tested helpers; ExoPlayer/Compose shell
  untested, same split as today.
- Streaming samples live in a dedicated StateFlow (not copied into the whole UiState per sample) to
  keep per-frame overlay updates cheap.
- No hardcoded colours; single-colour trail (no fake velocity colouring without a scale).
- No new persistence — everything here is in-memory for the screen's lifetime, like the current
  ephemeral replay.

---

## 7. Testing strategy

- **`screenToVideoPx`**: pure tests — a tap at the video rect's centre maps to the video centre; a
  tap in the letterbox margin returns null; corners map to (0,0)/(w,h); round-trips against
  `computeFittedVideoRect`.
- **`currentSampleIndex`**: existing tests stay green (reused).
- **Tracker `startMs`**: a unit-style check that tracking from a start time yields samples whose
  timestamps are all ≥ startMs (against a fixture path is not possible without a device, so this is
  covered by the pure interval/window logic where feasible; the streaming callback is verified by
  the ViewModel wiring, not a UI test).
- **Existing suite green**: `ConcentricDetectorTest`, `AnalyzeBarPathUseCaseTest`,
  `BarPathFrameTrackerTest`, `BarPathTrackPlaybackContentTest`, replay helper tests.
- **On-device (the whole point)**: press play → dot moves; mark partway → dot tracks from there.
  This is the self-verifying tracking check; the honest unverified-coordinate-mapping caveat stays.
- Full `assembleGithubDebug` + unit-test run before shipping; version bump before build; badging
  verified local + downloaded release asset; explicit file staging.

---

## 8. Boundaries

**Always do:**
- Keep playback non-blocking — tracking runs in the background, the video never waits on it.
- Keep coordinate math pure/tested; reuse `computeFittedVideoRect`.
- Reuse the streamed samples for analysis; never re-track for numbers.
- Preserve the results / velocity replay / save / share exactly as they are.

**Ask first about:**
- Whether tapping should auto-pause (default: yes — sampling the exact displayed frame is more
  reliable) vs. sample while playing.

**Never do:**
- Don't attempt genuine frame-locked real-time decode (stutters; the chosen approach is
  play-now-plus-background-track).
- Don't colour the live trail by speed (no scale in this mode).
- Don't reintroduce dual-marker / live-camera-session / high-speed paths.
- Don't touch unrelated features (biomechanics, Coach, updater).

---

## 9. Notes

This supersedes the two-step mark→watch (v0.52.0): the up-front "Tracking the marker…" wait is gone
from the watch experience, marking moves into the player where you can place it wherever the lift
is, and tracking streams in so the dot is live (immediately on loop). The velocity-numbers path is
unchanged. The marker-tracking core (blob + nearest-neighbour) is still the same code a real
recording must validate — and this makes that validation a fully live, play-and-watch check.
