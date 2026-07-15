# SPEC — Stickman Animation Improvements (Biomechanics Visualizer)

## Status: Draft — awaiting confirmation before implementation.

(Replaces the shipped VBT colored-marker spec — v0.55.0.)

## 1. Objective

Make the biomechanics stickman **move like a real lifter, cover more lifts, and scrub
better** — without changing its visual style (it stays a clean line-figure). Three
improvements, chosen by the user:

1. **Real ascent** — the way back up is its own movement, not the descent played in
   reverse. Hips rise first, torso lags (the grind).
2. **More lifts** — add **deadlift, overhead press, bench press** alongside squat.
3. **Better controls** — smoother/clearer **scrub** (phase labels + ticks, rep-timeline
   semantics) and better **turntable rotate feel**.

Explicitly out of scope: visual restyling (no fleshed-out silhouette, no muscle shading —
user chose "not about visuals"), full auto-play rep loop (user chose "better scrub only"),
IK solver, real 3D.

## 2. Why the ascent needs a new mechanism (root cause)

`StickmanKinematics.buildNodes` builds a squat pose from `kneeAngleDeg` **only**:

- thigh is derived from the knee angle (2-link hinge),
- torso lean is **solved** from a balance equation so the bar lands over mid-foot,
- `PoseAngles.hipAngleDeg` and `torsoAngleDeg` are currently **not used** by the geometry.

Consequence: a pose is a pure function of `kneeAngleDeg` + ratios. At the same knee angle,
descending and ascending are **geometrically identical** — the ascent *cannot* look
different from the reverse of the descent. Fixing "no real ascent" is therefore not a
tuning task; it needs a new degree of freedom.

**Mechanism: `torsoLeanBiasDeg`.** Add an authored torso-lean bias, applied as a *rotation
about the hip* on top of the solved/authored lean. A rotation about the hip never changes
segment lengths, so rigidity (the invariant every prior sprint protected) is preserved. A
positive bias pitches the torso further forward than balance → the bar drifts slightly
forward of mid-foot, which is exactly what a real grind looks like. Descent keyframes use
bias ≈ 0 (balanced); ascent "sticking-point" keyframes use a positive bias that decays back
to 0 by lockout. This is the single mechanism that produces the grind for **every** lift.

## 3. Scrub becomes a rep timeline

Today the slider is a **depth** control: 0 = standing, 1 = bottom; going "up" = dragging
back down. To show a real ascent, the slider becomes a **full-rep timeline**:

`standing → descent → bottom → ascent (grind) → standing`

- Keyframe lists extend from 4 (descent only) to ~7 (full rep). `StickmanInterpolator` is
  unchanged in shape — it already interpolates across an ordered keyframe list by position;
  it just gets more keyframes and one more angle field to lerp.
- Slider end-labels change from `STANDING / BOTTOM` to `STANDING / STANDING`, with the
  bottom marked mid-track. Add **phase ticks + labels** under the track (e.g.
  `STAND · PARALLEL · BOTTOM · GRIND · LOCK`) — this is the "scrub UX" improvement.
- Deadlift/OHP are concentric-first (bottom = start): their timeline reads
  `start → lockout → lower → start`; ticks labelled per lift.

## 4. Per-lift kinematics

Squat stays exactly as-is (regression-protected). Each new lift is a small, documented
extension of the leg/arm/bar placement — **not** a new engine.

| Lift | Legs | Torso lean | Arms + bar |
|------|------|-----------|-----------|
| **Squat** (unchanged) | knee-driven | solved (balance) + bias | bar on upper back |
| **Deadlift** | knee-driven (shared) | **authored** hinge angle + bias (torso is the defining feature of a hinge, so use `torsoAngleDeg` directly rather than solving) | **arms hang vertically** from each shoulder by a fixed arm-length ratio; bar spans the wrists. Bar tracks under the shoulders — the correct DL bar path — with no extra bar-height field needed. |
| **Overhead press** | near-straight, ~static (high knee angle whole rep) | authored, near-vertical + slight bias | **press-up**: wrists rise from shoulder height (`pressFraction`=0, bar racked) to fully extended overhead (`pressFraction`=1); bar spans wrists. |
| **Bench press** | planted, knees up (static) | **supine** — body rendered horizontal on a bench line | press-up arms (vertical), same `pressFraction` driver as OHP. Largest change (orientation flips); **build last, validate the look on a device before investing further.** |

New optional `PoseAngles` fields, all defaulted so existing JSON still decodes:

- `torsoLeanBiasDeg: Float = 0f` — grind bias (all lifts).
- `pressFraction: Float = 0f` — arm extension for OHP/bench (0 racked → 1 locked). Ignored
  by squat/deadlift.

A per-lift branch selects: **torso mode** (solve vs authored), **arm/bar mode** (BACK /
HANGING / PRESS_UP), and **orientation** (upright vs supine). Squat's path is the exact
current code, untouched.

## 5. Turntable rotate feel

Small, self-contained polish in `StickmanCanvas`:

- **Snap-to-angle** on drag end: nearest of front `0°`, three-quarter `±45°`, side `±80°`
  (animated snap, not a jump).
- **Double-tap to reset** to front (`0°`).
- Keep drag sensitivity/clamp (`0.35°/px`, `±80°`) but expose both as named constants for
  easy tuning after a device look.

## 6. Build slices (ordered, each independently shippable + releasable)

1. **Slice 1 — Squat real ascent + scrub timeline + rotate feel.** `torsoLeanBiasDeg`,
   extend `keyframes_squat.json` to a full rep with ascent grind, rep-timeline slider with
   phase ticks/labels, rotate snap/reset. Highest value, fully self-contained. Ships first.
2. **Slice 2 — Deadlift.** Hanging-arm/bar mode, authored-hinge torso, `keyframes_deadlift.json`,
   enable in Lift Selector. (Archetype differences are largest here — high payoff.)
3. **Slice 3 — Overhead press.** Press-up arm mode, `pressFraction`, `keyframes_ohp.json`.
4. **Slice 4 — Bench press.** Supine orientation, `keyframes_bench.json`. Heaviest; confirm
   the look is acceptable on a device before finishing polish.

Each slice: bump `versionCode`+`versionName`, `assembleGithubDebug`, GitHub release per the
project's release rules.

## 7. Project structure (files touched / added)

**Modify**
- `domain/model/PoseAngles.kt` — add `torsoLeanBiasDeg`, `pressFraction` (defaulted).
- `domain/model/BiomechanicsPhase.kt` — add ascent phases (e.g. `ASCENT_HIPRISE`,
  `ASCENT_MID`), + OHP/bench phases.
- `domain/model/LiftType.kt` — add `OVERHEAD_PRESS`, `BENCH`.
- `domain/util/StickmanKinematics.kt` — per-lift torso/arm/bar/orientation branch + bias.
  Watch the 500-line rule: if it grows past ~450, split arm/bar placement into a sibling
  `StickmanArmBar.kt` helper (pure).
- `domain/util/StickmanInterpolator.kt` — lerp the two new angle fields.
- `data/repository/BiomechanicsRepositoryImpl.kt` — load `keyframes_ohp.json`,
  `keyframes_bench.json` (defensive `runCatching`, same as the existing deadlift path).
- `presentation/screens/biomechanics/BiomechanicsVisualizerScreen.kt` — rep-timeline slider,
  phase ticks/labels.
- `presentation/screens/biomechanics/BiomechanicsVisualizerViewModel.kt` — expose phase
  tick metadata; adjust default slider progress if needed; `LiftType.displayName()` for new lifts.
- `presentation/screens/biomechanics/StickmanCanvas.kt` — snap/reset/double-tap.
- Lift Selector screen — enable deadlift/OHP/bench.

**Add (assets)**
- Extend `assets/biomechanics/keyframes_squat.json` (full-rep keyframes + bias).
- `assets/biomechanics/keyframes_deadlift.json`, `keyframes_ohp.json`, `keyframes_bench.json`
  — one entry per archetype (4 each), authored under the existing mechanical-not-prescriptive
  content policy.

**Add (tests)** — see §9.

Keyframe angle/ratio authoring: hand-authored first pass (like the existing squat table),
flagged as tunable after a device look — not claimed as validated.

## 8. Code style

- Kotlin, Compose-only (no XML/custom Views — CLAUDE.md non-negotiable). Metric units only.
- Pure kinematics/interpolation stays Android-free in `domain/util/` (keeps it unit-testable,
  the established split). Compose canvas stays a thin wrapper.
- No hardcoded colors — `MaterialTheme.colorScheme.*` / theme tokens only.
- Files under 500 lines; new numeric constants named + documented for post-device tuning.
- Squat's existing geometry path must remain behavior-identical (bias defaults to 0 → solved
  lean unchanged).

## 9. Testing strategy

Pure-function unit tests (JUnit, no Robolectric — matches this project's test style):

- **Rigidity preserved** — every lift, across the full interpolated rep: shank/thigh/torso/
  head-neck segment lengths stay `ratio × bodyScale` at sampled progress steps (extend the
  existing `StickmanKinematicsTest` rigidity test to the new lifts; bias is a rotation, so it
  must not change any length).
- **Real ascent** — at equal knee angle, an ascent keyframe (bias > 0) yields a
  **measurably more forward torso lean** than the matching descent keyframe (bias 0).
- **Depth still reached** — hip drops below knee at the squat bottom (existing guarantee,
  re-assert with the extended keyframe list).
- **Deadlift** — arms hang ~vertically (wrist.x ≈ shoulder.x); bar rises from setup to
  lockout; torso pitches from hinged to upright.
- **OHP** — `pressFraction` 0→1 moves wrists from shoulder height to fully extended overhead;
  bar stays over the shoulders.
- **Interpolator** — new fields (`torsoLeanBiasDeg`, `pressFraction`) lerp correctly and land
  exactly on keyframe values at segment boundaries.
- **Rotate** — snap picks the nearest detent; double-tap resets to 0° (pure helper).

Build gate per slice: `.\gradlew testGithubDebugUnitTest` green + `.\gradlew assembleGithubDebug`
successful + `grep -r " lb" app/src` returns nothing.

## 10. Boundaries

**Always**
- Preserve squat's exact current geometry (bias = 0 path unchanged; regression tests prove it).
- Keep kinematics/interpolation pure and Android-free; keep it unit-tested.
- Author lift content under the mechanical-not-prescriptive policy (observational, never
  "you should"); mark new archetype angle/ratio tables as first-pass, tunable after device.
- Follow release rules: version bump before build, `assembleGithubDebug`, badging-verify.

**Ask first**
- Before building **Slice 4 (bench)** — the supine orientation is the biggest change and its
  educational payoff is the lowest of the four; confirm the rendered look is acceptable on a
  device before finishing it, or agree to simplify it.
- Before any change that would alter squat's on-screen motion at bias 0 (i.e. any change to
  the existing solved-torso path).

**Never**
- Restyle the figure into a fleshed-out silhouette or add visual shading (user chose line-figure).
- Add an IK solver, real 3D projection, or a physics sim.
- Commit secrets/keystore files. No `Co-Authored-By` / "Generated with Claude" commit trailer
  (project rule — `.claude/settings.json` has no `attribution.commit`).
- Break the metric-units-only or Compose-only rules.

## 11. Acceptance criteria (Slice 1, the first ship)

- Scrubbing the squat slider plays standing → bottom → **a visibly different ascent** (torso
  more pitched forward through the sticking point) → standing.
- Slider shows phase ticks/labels; bottom is marked mid-track.
- Turntable snaps to front/¾/side on release and resets on double-tap.
- All pure tests green; squat bias-0 geometry unchanged; build clean; no `" lb"`.
- **Known gap (standing for this feature):** not visually validated on a device this session —
  angle/bias values are a first pass, tuned after the user's real look.
