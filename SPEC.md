# SaiyanStrong — VBT: Markerless Template Tracking

## Status: Draft — awaiting confirmation before implementation.

(Replaces the previous "Live Tracked Playback" spec — that shipped as v0.53.0. The live player works
and the user can now see the tracking, and it's inaccurate: the dot lands offset from the tap and
the trace jumps around / jitters / drifts. Root cause, confirmed via the user's answers: they tap
the bar/plate itself with no coloured marker, and the current tracker follows *colour* — which can't
lock onto a bare grey metal bar, so it grabs whatever grey-ish blob is biggest and wanders. Fix:
track a small image PATCH around the tapped point by its appearance, not by colour.)

---

## 0. Decisions locked in via clarifying questions

- **Markerless: track whatever the user taps.** Replace colour-blob tracking (in the capture flow)
  with template/patch tracking: sample a small grayscale patch of the image around the tap and, each
  frame, find where that patch best matches nearby. This tracks a plate edge / bar collar / bolt
  directly — no coloured marker required, matching what the user is actually doing (tapping the bar).
- **Smooth the drawn trace.** The live trail currently draws raw per-frame positions (inherently
  jittery). Apply light smoothing to the drawn path so it reads as a clean line. Velocity numbers
  already smooth separately (Savitzky-Golay in the analyzer); this is purely the visual trail.
- **Fix the tap offset.** The dot must land exactly where the user tapped. Ensure the PlayerView
  renders the video aspect-fit (so the existing letterbox mapping is correct), and the template is
  extracted at exactly the tapped pixel — so the first tracked point equals the tap point.

---

## 1. Objective

Make the bar-path tracking actually follow the bar when the user taps it directly, no coloured
marker needed. Tap a distinctive point on the bar (plate edge, collar, end cap), and the dot sticks
to that point through the lift with a clean traced path — accurate enough to trust the shape of the
bar path and, with the scale step, the velocity numbers.

Target users: unchanged — anyone tapping their own lift video, now able to tap the bar itself.

---

## 2. Root causes → fixes

### 2.1 Colour tracking can't follow a bare bar → markerless template tracking
- **New pure `util/barpath/TemplateMatcher.kt`**: normalized cross-correlation (NCC) matching over a
  grayscale search window. `bestMatch(frameGray, frameW, frameH, template, tW, tH, centerX, centerY,
  searchRadius)` returns the (x, y) of the best template match near the centre plus its NCC score.
  NCC (not raw SSD) so brightness changes between frames don't break the match. Pure, unit-tested.
- **New `BarPathFrameTracker.trackTemplate(...)`** (mirrors `trackMarker`'s frame-extraction +
  streaming + startMs + MAX_SAMPLES cap, which all stay): extracts a grayscale, downscaled patch
  around the tapped point at the mark frame, then for each subsequent frame searches a window around
  the previous position for the best NCC match. **Rejects poor matches** (NCC below a threshold →
  keep the previous position instead of jumping) — this is what stops the "teleports all over the
  place." Emits a `BarPathSample` per frame; streams via `onSample` exactly like the live path today.
- The capture flow (`onMarkTap`) switches from sampling a colour to extracting a template patch and
  calling `trackTemplate`. Everything downstream is unchanged — it still produces a
  `List<BarPathSample>`, so the overlay, `ConcentricDetector`, and `AnalyzeBarPathUseCase` all work
  as-is. Colour tracking (`trackMarker`/`MarkerColorProfile`) stays in the codebase but dormant
  (not called from the flow) — removing it is a separate cleanup.
- **Acceptance**: on a real recording, tapping a distinctive point on the bar makes the dot follow
  that point through the rep without teleporting to unrelated objects; a low-confidence frame holds
  position rather than jumping.

### 2.2 Tap offset → correct, verified mapping
- Set the ExoPlayer `PlayerView` resize mode explicitly to **fit** (aspect-preserving letterbox), so
  `computeFittedVideoRect`/`screenToVideoPx` (which assume fit) match what's actually on screen.
- Extract the template at exactly the tapped pixel: map the tap (video-pixel space, from
  `screenToVideoPx`) into the extracted frame's own pixel space (scale by the frame bitmap's actual
  width/height in case `getFrameAtTime` returns a different size than the metadata dimensions), so
  the patch is centred on what the user tapped.
- **Acceptance**: the first tracked dot appears on the exact point tapped (not offset); drawn back
  via the same mapping, tap-in and dot-out round-trip to the same on-screen location.

### 2.3 Jittery trace → smoothing
- **New pure `smoothedPathPoints(samples, window)`** (moving average over the position series),
  used by the overlay to draw a clean trail + dot. Applied to display only; the raw samples still
  feed the analysis (which does its own SG smoothing).
- **Acceptance**: the drawn trail reads as a smooth line rather than a scatter of jittery points,
  without materially lagging the true path.

---

## 3. Tech stack additions

None. Pure Kotlin for `TemplateMatcher` + `smoothedPathPoints`; `BarPathFrameTracker` extended;
grayscale conversion via the existing bulk `getPixels()`. No new dependencies.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── util/barpath/
│   ├── TemplateMatcher.kt              ← NEW: pure NCC template matching (bestMatch)
│   └── BarPathFrameTracker.kt          ← + trackTemplate (grayscale patch + NCC search + streaming,
│                                          reuses startMs/onSample/MAX_SAMPLES) + gray-patch extract
├── presentation/screens/barpath/
│   ├── BarPathCaptureViewModel.kt      ← onMarkTap extracts a template patch (not a colour) and
│   │                                       calls trackTemplate; rest of the flow unchanged
│   └── BarPathTrackPlaybackContent.kt  ← PlayerView resize=fit; smoothed trail via smoothedPathPoints
```

New pure helpers get unit tests (`TemplateMatcherTest`, and a smoothing test alongside the existing
`BarPathTrackPlaybackContentTest`). `screenToVideoPx`/`currentSampleIndex`/`computeFittedVideoRect`
stay and keep their tests.

---

## 5. Code style (extends existing CLAUDE.md rules)

- Matching + smoothing math stays pure (`util/barpath`, no Android/Compose), unit-tested; the
  Compose/ExoPlayer shell stays untested — same split as the rest of this feature.
- Named constants for patch size, search radius, downscale, and the NCC accept threshold, each with a
  one-line rationale (matching the existing `MAX_SAMPLES`/`MIN_MARKER_PIXELS` precedent), so they're
  easy to tune after a real-footage look.
- No new persistence; no hardcoded colours.

---

## 6. Testing strategy

- **`TemplateMatcher.bestMatch`**: synthetic grayscale images — a template placed at a known offset
  is found there (exact); a shifted template is found at the shifted location within the search
  radius; a brightness-scaled copy still matches (NCC invariance); a pure-noise search returns a low
  score (so the reject-threshold works). 
- **`smoothedPathPoints`**: a jittery series is smoothed toward its trend; endpoints handled; a
  short series returns unchanged.
- **`trackTemplate` behaviour**: covered indirectly (the pure matcher + the shared frame-extraction
  it reuses are each tested); the extraction loop itself needs a device, same as `trackMarker`.
- **Existing suite green**: all current barpath/domain tests must still pass.
- **On-device (the real check)**: tap the bar → dot lands on it and follows through the rep; the
  trace is a clean line; low-confidence frames hold rather than jump. This is the self-verifying
  step — the honest unverified-on-device caveat stays until the user confirms.
- Full `assembleGithubDebug` + unit-test run before shipping; version bump before build; badging
  verified local + downloaded release asset; explicit file staging.

---

## 7. Boundaries

**Always do:**
- Keep matching/smoothing pure and tested; reuse the existing streaming/startMs/cap infrastructure.
- Reject low-confidence matches (hold position) rather than letting the point jump — this is the
  core of "not all over the place."
- Keep the tap→pixel mapping exact so the dot lands where tapped.
- Preserve the analysis/results/velocity-replay/save path (it consumes `BarPathSample` regardless of
  how tracking produced it).

**Ask first about:**
- Whether to periodically update the template as it tracks (handles appearance change but can drift)
  — default: keep the original template (no update) this pass, simplest and driftless; revisit if it
  loses lock on real footage.
- Removing the now-dormant colour-tracking code (default: leave it, cleanup later).

**Never do:**
- Don't require a coloured marker (the whole point is tracking the bar directly).
- Don't smooth so hard the trace stops reflecting the real path.
- Don't reintroduce dual-marker / live-camera-session / high-speed paths.
- Don't touch unrelated features (biomechanics, Coach, updater).

---

## 8. Notes

This finally addresses the accuracy at its root: the feature was always colour-tracking, which needs
a bright marker the user didn't have. Template tracking follows whatever distinctive point they tap,
which is what "tap the bar" has meant all along. It won't be flawless — template tracking can lose
lock on a featureless patch, heavy motion blur, or big appearance change — so the accept-threshold
(hold-on-low-confidence) and the tap-a-distinctive-point guidance matter. But it's the correct
technique for markerless point tracking, and the live player makes its quality immediately visible.
