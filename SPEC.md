# SaiyanStrong — VBT: Colored-Marker Tracking as the Foundation

## Status: Draft — awaiting confirmation before implementation.

(Replaces the "Markerless Template Tracking" spec — v0.54.0/v0.54.1. After repeated accuracy
failures tracking a bare bar, the user asked what the most common/reliable approach actually is.
Answer: a bright colored marker + color tracking is the standard, most-reliable *video-only* method
— and we already built it. The template/color-blob hand-rolled CV was fragile precisely because it
was doing a hard job without a marker. User committed to using a bright marker. This spec makes the
already-built color tracking the primary path, hardens it, and adds clear marker guidance — the
cheapest, most-likely-to-work step before anything heavier.)

---

## 0. Decisions locked in via clarifying questions

- **Colored marker + color tracking is the foundation.** The user will attach a bright,
  distinctly-colored marker (neon tape / sticker / colored cap) to the plate or collar. We re-enable
  the color tracking already in the codebase (`trackMarker` + `MarkerColorProfile` + blob detection +
  nearest-neighbor, Sprints 26–31) as the live player's tracking method. Template tracking
  (`trackTemplate`) is left dormant, not removed.
- **Validate the reliable method first.** Before adding heavy dependencies (OpenCV) for markerless,
  prove the marker path works — it's what reliable camera bar-path apps require anyway.

---

## 1. Objective

Make bar tracking actually reliable by using it the way it's designed: the user puts a bright marker
on the bar, taps it in the live player, and the dot follows that marker cleanly through the rep.
Everything downstream (streaming live overlay, concentric detection, velocity numbers, replay, save)
already works on the produced samples — this only changes what produces them, back to color.

Target users: unchanged — anyone filming their lift, now with a bright marker on the bar.

---

## 2. What changes

### 2.1 Live player tracks by color again (from the mark point, streaming)
- `BarPathCaptureViewModel.onMarkTap` samples the marker's color from the tapped frame/point (the
  existing `sampleMarkerColor` → `MarkerColorProfile.sample`) and runs `trackMarker` — which already
  supports `startMs` + `onSample` streaming (added v0.53.0) — from the mark point onward, streaming
  into `_liveSamples` exactly like the template path did. So the whole live-player experience (play
  now, dot follows, RE-MARK, GET VELOCITY NUMBERS, smoothed trace) is unchanged; only the tracker
  swaps back to color.
- **Acceptance**: with a bright marker on the bar, tapping it makes the dot follow the marker
  through the full rep (down and up), drawing a clean path — the failure mode from template tracking
  (stuck at the top) is gone.

### 2.2 Seed tracking with the tap position (robust to multiple same-color objects)
- `trackMarker` currently seeds its first frame with the *largest* blob of the matched color. Add an
  optional initial position (the tap point, in video pixels) so the first frame instead picks the
  blob *nearest the tap* (reusing the existing nearest-neighbor `chooseTrackedBlob`). This makes it
  robust when there's more than one object of the marker's color in frame (a second marker, a
  reflection, a same-colored item) — it locks onto the one the user actually tapped.
- **Acceptance**: with two same-colored objects in frame, tracking follows the tapped one, not
  whichever is biggest.

### 2.3 Clear marker guidance
- The player's pre-mark prompt and the tips make it explicit: attach a bright, distinct-colored
  marker and tap it (not the bare bar). Update the "couldn't track" retry message to point at the
  marker (brightness/contrast), not "tap a distinctive point" (which was template-era wording).
- **Acceptance**: a first-time user is told to use a marker before they tap; the guidance matches
  the color method.

### 2.4 Keep the v0.54 wins
- The tap-offset fix (`PlayerView` RESIZE_MODE_FIT + exact tap→pixel mapping) and the smoothed trace
  stay — they help regardless of tracker.

---

## 3. Tech stack additions

None. Reuses existing color tracking, blob detection, streaming, and the live player. No OpenCV, no
new dependencies — that's the whole point of validating the cheap reliable path first.

---

## 4. Project structure (changed)

```
app/src/main/java/com/saiyanstrong/
├── util/barpath/BarPathFrameTracker.kt      ← trackMarker gains an optional initial (seed) position
│                                               so frame 1 picks the blob nearest the tap
├── presentation/screens/barpath/
│   ├── BarPathCaptureViewModel.kt           ← onMarkTap samples color + trackMarker (was trackTemplate)
│   └── BarPathTrackPlaybackContent.kt        ← marker-focused prompt/guidance text
```

`trackTemplate`/`TemplateMatcher` stay (dormant); `screenToVideoPx`/`smoothedPathPoints`/
`currentSampleIndex`/color-tracking tests all stay and keep passing.

---

## 5. Code style (extends existing CLAUDE.md rules)

- Reuse the existing color-tracking pipeline as-is; the only logic change is seeding the first-frame
  blob choice with the tap position (a small, testable addition to the existing nearest-neighbor).
- No hardcoded colors; no new persistence; keep the pure/tested vs Compose-shell split.

---

## 6. Testing strategy

- **Seed selection**: a `chooseTrackedBlob`-level test that, given a seed position, the nearest blob
  to the seed is chosen on the first frame (not the largest) — the existing `chooseTrackedBlob`
  already picks nearest-to-previous, so this verifies the seed is passed through as the initial
  "previous."
- **Existing color-tracking tests stay green** (`BarPathFrameTrackerTest`, `MarkerColorProfileTest`,
  etc.), plus all the live-player/domain tests.
- **On-device (the real validation)**: attach a bright marker, tap it, watch it track the full rep.
  This is the whole point of choosing the reliable method — it should work where the bare-bar
  template tracking didn't. Honest caveat stays until confirmed.
- Full `assembleGithubDebug` + unit-test run before shipping; version bump before build; badging
  verified local + downloaded release asset; explicit file staging.

---

## 7. Boundaries

**Always do:**
- Keep the live-player UX identical; only swap the tracker back to color + add the seed + guidance.
- Keep the tap-offset fix and trace smoothing.
- Reuse the existing streaming/startMs/cap/blob/nearest-neighbor code.

**Ask first about:**
- Whether to *remove* the dormant template/OpenCV-less markerless code (default: leave it — if the
  marker path proves reliable it can be cleaned up; if the user later wants markerless we revisit
  OpenCV as its own effort).
- Tightening the color tolerance defaults (default: leave as-is; a bright marker matches fine — only
  revisit if a real marker still picks up background).

**Never do:**
- Don't add OpenCV / a heavy tracking dependency in this pass — the decision was to validate the
  marker path first.
- Don't reintroduce dual-marker / live-camera-session / high-speed paths.
- Don't touch unrelated features (biomechanics, Coach, updater).

---

## 8. Notes

This is deliberately a *small, high-confidence* change: it uses code we already have, the way the
industry actually does reliable video bar-path tracking (with a marker), and it's the honest "do
the simple reliable thing before the complex thing" move after a run of fragile hand-rolled CV. If
a real bright marker tracks cleanly (it should), the tracking problem is solved and OpenCV/markerless
never needs to be built. If the user later insists on markerless, that becomes its own dependency-
heavy sprint — but we'll know the reliable baseline works first.
