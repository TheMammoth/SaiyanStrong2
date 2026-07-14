# SaiyanStrong — VBT: Mark-then-Watch Tracked Playback

## Status: Draft — awaiting confirmation before implementation.

(Replaces the previous "VBT Fix & Simplify" spec — that shipped as v0.51.0/v0.51.1. This builds the
user's requested flow: upload (or record) a video → tap to mark the bar's marker color → play the
video and watch the marker tracked in real time with its path highlighted. Most of the *rendering*
already exists in `BarPathReplayContent` [Sprint 38]; this spec restructures the flow so that
visual, tracked playback is what you get right after marking the bar — not something buried behind
scale calibration, weight entry, and analysis.)

---

## 0. Decisions locked in via clarifying questions

- **Watch first, numbers optional.** After marking the marker color, the app tracks the marker
  across all frames and immediately plays the video with the tracked overlay — **no scale tap and
  no weight required just to watch**. A "GET VELOCITY NUMBERS" action on the playback screen adds
  the plate-scale + weight step only if the user wants real m/s. This removes all friction from the
  core "see it track" experience and makes it the primary way to verify tracking works.
- **Single-color trail + marker dot.** As the video plays, a bright dot sits on the tracked marker
  and leaves a growing single-color (neon green) trail behind it. No velocity coloring in this mode
  — there's no scale yet, so a speed-colored trail would imply a real reading it doesn't have. (The
  existing velocity-colored replay stays available *after* numbers are computed.)
- **Applies to both recorded and uploaded videos.** Once a video exists from either source, the
  mark → track → watch flow is identical. The video's origin doesn't matter once the file is on disk.

---

## 1. Objective

Let a user confirm, at a glance, that bar tracking actually followed the bar — by watching it happen
over their own footage — before caring about any number. Flow:

1. Get a video (record in-app, or import from gallery — unchanged).
2. See the first frame; **tap the marker on the bar** to sample its color (existing mechanism).
3. The app tracks the marker across every frame (off-screen, using color only — no scale needed).
4. The video plays with a **dot on the tracked marker + a growing trail** of where it's been,
   synced to playback. Scrub/replay freely.
5. Optionally tap **GET VELOCITY NUMBERS** → tap two edges of a plate for scale + enter weight →
   the existing analysis runs (reusing the already-tracked samples) → results, with the
   velocity-colored replay now available.

Target users: anyone who wants to see the tracking work on their own lift, with the least possible
setup — and, as a bonus, this is the tool that finally makes the long-standing "is tracking
trustworthy?" question answerable by the user directly.

---

## 2. How it works technically (locked decisions, not open questions)

- **Pre-track, then synced overlay — not literal per-frame analysis during playback.** Tracking all
  frames up front (fast, off the main thread, the existing `BarPathFrameTracker.trackMarker`) and
  then playing the video with the pre-computed path overlaid + a cursor that follows the current
  playback position looks identical to "live tracking" but is smooth and robust. True frame-by-frame
  analysis synchronized to playback would stutter and would make the dot vanish on any frame that
  fails to track. This is the same architecture the existing replay already uses.
- **No scale needed to watch.** `trackMarker` returns pixel positions (`List<BarPathSample>` with
  `xPx`/`yPx`); scale (`pixelsPerMeter`) is only consumed by `AnalyzeBarPathUseCase`. The playback
  overlay maps pixel positions into the letterboxed video rect via the existing pure
  `computeFittedVideoRect` helper — exactly how the current replay maps its path.
- **Track once, reuse.** The samples tracked for playback are reused by the later analysis, so
  choosing GET VELOCITY NUMBERS does not re-track the video.

---

## 3. Core features & acceptance criteria

### 3.1 Marking step (color only)
- After a video is obtained, show its first frame. Tapping the bar samples the marker color
  (existing `sampleMarkerColor` → `MarkerColorProfile.sample`, small-neighborhood average). A
  visible dot marks where the color was sampled. A "RE-MARK" affordance clears it.
- A single primary action: **TRACK & PLAY**, enabled once a color is sampled.
- **Acceptance**: from a fresh video, one tap + TRACK & PLAY reaches the playback screen; no scale
  or weight input is present or required at this stage.

### 3.2 Tracked playback (the core deliverable)
- Plays the video (ExoPlayer/PlayerView, already a dependency) with an overlay: a white dot ringed
  in neon green sitting on the tracked marker at the current playback moment, and a growing neon
  trail of the path up to that moment. Loops; has play/pause + a scrub slider (reuse the replay's
  transport).
- The dot follows the marker as the video plays; scrubbing moves the dot to match.
- If tracking clearly failed (dot not on the bar / jumping), the user can go BACK and re-mark, or
  RE-MARK in place — this screen *is* the verification tool.
- **Acceptance**: on a real recording with a marked bar, the dot visibly tracks the bar through the
  lift and the trail traces the bar path, synced to playback — with zero velocity/scale/weight setup.

### 3.3 Optional velocity numbers
- A **GET VELOCITY NUMBERS** action on the playback screen leads to the scale step: tap two edges of
  a plate (a plate is ~45 cm), reference-length field (prefilled 45), and weight (standalone entry
  only). Then ANALYZE runs `AnalyzeBarPathUseCase` over the auto-detected concentric window
  (`ConcentricDetector`, v0.51.0) using the already-tracked samples, and shows the existing results
  screen — from which the existing velocity-colored replay, share, and save all work as today.
- **Acceptance**: velocity numbers require exactly the scale tap + weight (no re-recording,
  re-marking, or re-tracking); skipping this step still lets the user watch tracked playback fully.

### 3.4 Both sources
- Recording in-app and importing from gallery both land on the same marking → playback flow. The
  recorded-video path no longer goes straight to the old calibrate-everything-first screen.

---

## 4. Tech stack additions

None. Reuses ExoPlayer/media3 (already a dependency, used by `BarPathReplayContent`),
`BarPathFrameTracker`, `AnalyzeBarPathUseCase`, `ConcentricDetector`, and the pure
`computeFittedVideoRect` helper. New code is a lightweight playback composable + flow restructuring.

---

## 5. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/presentation/screens/barpath/
├── BarPathCaptureViewModel.kt      ← new steps (MARKING → PLAYBACK → optional SCALE → RESULTS);
│                                       track-once-reuse; color-only marking split from scale
├── BarPathCaptureScreen.kt         ← MarkingStep (frame + marker tap + TRACK & PLAY),
│                                       PlaybackStep (hosts the new overlay + GET VELOCITY NUMBERS),
│                                       ScaleStep (the old scale-tap + weight, now optional/after)
├── BarPathTrackPlaybackContent.kt  ← NEW: ExoPlayer + single-color trail/dot overlay (no velocity),
│                                       reuses computeFittedVideoRect
└── BarPathReplayContent.kt         ← unchanged; still the velocity-colored replay reached after
                                        GET VELOCITY NUMBERS. computeFittedVideoRect made reusable.
```

A pure helper `currentSampleIndex(samples, playbackMs)` (which tracked sample a playback time maps
to) is extracted and unit-tested, mirroring the existing `computeFittedVideoRect`/`velocityColorArgb`
pure-helper precedent.

---

## 6. Code style (extends existing CLAUDE.md rules)

- Overlay math (pixel→fitted-rect mapping, current-index selection) stays in pure helpers with unit
  tests; the Compose/ExoPlayer shell stays untested (same split as `BarPathReplayContent`).
- No hardcoded colors outside the existing theme tokens (NeonGreen/etc.).
- No new persistence — tracked samples/playback are in-memory for the screen's lifetime, exactly as
  the current ephemeral replay already is.
- Reuse `computeFittedVideoRect` rather than duplicating the letterbox math.

---

## 7. Testing strategy

- **`currentSampleIndex`**: pure unit tests — playback before the first sample, between samples
  (picks the latest sample at/under the time), after the last, empty list.
- **`computeFittedVideoRect`**: existing tests stay green (reused, not changed).
- **Track-once-reuse**: verify (via the ViewModel's logic) that reaching results after playback does
  not invoke `trackMarker` a second time — a targeted assertion on the flow, not a full UI test.
- **Existing suite green**: `ConcentricDetectorTest`, `AnalyzeBarPathUseCaseTest`,
  `BarPathFrameTrackerTest`, replay helper tests — all must still pass.
- **On-device verification (the user's, and now the whole point)**: this feature *is* the tracking
  verification tool. Honest flag stays — the coordinate mapping and ExoPlayer sync are unverified on
  a device — but watching the dot track the bar is exactly the check that closes that gap.
- Full `assembleGithubDebug` + full unit-test run before shipping, same release discipline as every
  prior sprint (version bump before build, badging verification local + downloaded release asset,
  explicit file staging).

---

## 8. Boundaries

**Always do:**
- Keep tracking off the main thread; keep the overlay math pure and tested.
- Reuse the already-tracked samples for analysis — never re-track when getting numbers.
- Keep the velocity-colored replay and results/save/share exactly as they are, reached after
  GET VELOCITY NUMBERS.

**Ask first about:**
- Whether to drop the old "calibrate everything first" ordering entirely vs. keep a shortcut to it
  (default: the new marking→playback flow replaces it; scale becomes an after-the-fact optional step).

**Never do:**
- Don't color the watch-mode trail by speed (implies a real reading with no scale).
- Don't reintroduce the removed dual-marker / live-session / high-speed paths.
- Don't do literal per-frame analysis during playback (stutters; use pre-track + synced overlay).
- Don't touch unrelated features (biomechanics, Coach mode, updater).

---

## 9. Notes

The rendering the user asked for (video + marker cursor + highlighted path, synced) already exists —
this sprint's real work is **flow**: make that visual playback the immediate reward for marking the
bar, drop the scale/weight prerequisite for merely watching, and reuse one tracking pass for both the
watch overlay and the optional numbers. It also, almost incidentally, turns the app's biggest
open risk (is marker tracking trustworthy on real footage?) into something the user can now see and
judge for themselves in two taps.
