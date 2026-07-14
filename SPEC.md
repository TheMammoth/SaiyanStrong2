# SaiyanStrong — Biomechanics Visualizer: Femur ↔ Torso Angle Coupling

## Status: Draft — ready for implementation.

(Replaces the previous "Turntable View + Perfectly Rigid Proportions" spec in this file — that
shipped as v0.49.0. User tried it and the sliders still felt disconnected: "when i move the femur
length the torso angle should also move when i move the torso slider the femur angle should
change.")

---

## 0. Decisions locked in via clarifying question

- **Coupling direction — two-way, confirmed by the user over the one-way option offered:**
  1. **Femur/shank/foot-length → torso angle (physically real).** Today `torsoAngleDeg` is an
     independent authored/interpolated input, completely decoupled from `LimbRatios`. It becomes
     **derived**: solved from a real balance equation (see 2.1) so the bar lands back over
     mid-foot given however far the thigh/shank/foot ratios push the hip forward — the same
     mechanical fact this app's own archetype content already claims ("Forward torso lean is
     greater than standard" for LONG_FEMUR) but the kinematics never actually enforced.
  2. **Torso ratio → knee/thigh angle (explicitly a stylistic/comfort nudge, not physics).** The
     user picked this over leaving it one-way. There is no real mechanical reason torso *length*
     dictates knee bend at a fixed squat depth — this direction is acknowledged as made up, small,
     and clamped so it can never invert the existing hip-below-knee depth guarantee.

---

## 1. Objective

Make the Custom Proportions sliders (and, since `buildNodes` is one shared function for every
archetype, the 4 fixed archetypes too) feel like one connected body instead of independently
resizable segments. Two coupled changes to `StickmanKinematics.buildNodes`:

1. Stop reading `PoseAngles.torsoAngleDeg` as an independent input. Solve it instead from a
   horizontal-balance equation over the already-built ankle→knee→hip chain, so the torso leans
   exactly enough to bring the bar back over mid-foot. This is a genuine improvement over both
   prior attempts at "bar over mid-foot": v0.48.0 achieved it via a post-hoc *translation* (which
   broke thigh rigidity); v0.49.0 dropped it entirely (perfect rigidity, no balance). Solving for
   a *rotation* instead gets both at once — rotation never changes segment length, so every limb
   stays exactly rigid **and** the bar lands on mid-foot as a real consequence of the geometry,
   not a hack.
2. Add a small, explicitly-labeled "comfort" scaling factor: `torsoRatio`'s deviation from a
   reference value nudges how far the thigh rotates for a given knee angle, so dragging the TORSO
   slider visibly moves the femur too.

---

## 2. Core features & acceptance criteria

### 2.1 Torso angle solved from balance, not authored
- After building the ankle→knee→hip chain exactly as today (shank/thigh lean from knee angle,
  now also comfort-scaled — see 2.2), solve `torsoLean` such that:
  `ankleX + shankDx + thighDx + (torsoRatio + barRiseRatio) × bodyScale × sin(torsoLean) ==
  midFootX` (where `midFootX = ankleX + footLenRatio × bodyScale × 0.5`).
- `sin(torsoLean)` is clamped to `[-1, 1]` before `asin` (an unreachable combination of extreme
  ratios could otherwise be mathematically impossible to balance); the final angle is additionally
  clamped to `[0°, 90°]` (a squat torso lean is never backward or past horizontal, as a defensive
  bound, not something expected to trigger for any slider-reachable ratio combination).
- `PoseAngles.torsoAngleDeg` stays in the model (same precedent as the already-unused
  `hipAngleDeg`) but is no longer fed into this geometry.
- **Acceptance**: for the same knee angle, increasing `thighRatio` (holding other ratios fixed)
  changes the solved torso lean (regression test, not just eyeballed) — matching the literal ask
  ("move femur length, torso angle should also move"). Sanity check already hand-verified before
  writing code: at LONG_FEMUR's BOTTOM angles/ratios the solve yields a noticeably larger torso
  lean than PROPORTIONAL's, consistent with this app's own existing archetype content copy.

### 2.2 Torso ratio nudges thigh lean (comfort factor, not physics)
- `thighLean = shankLean - (180° - kneeAngleDeg) × comfortScale`, where `comfortScale = 1 +
  THIGH_TORSO_COMFORT_GAIN × (torsoRatio - TORSO_REFERENCE_RATIO)`, clamped to a safe band (e.g.
  `0.6..1.4`) so it can never zero out or invert the knee-driven rotation direction.
  `TORSO_REFERENCE_RATIO` is PROPORTIONAL's torso ratio (0.29, the value the rest of the rig was
  originally tuned against) — at that exact ratio, `comfortScale == 1.0` and behavior is
  byte-identical to today.
- Documented in KDoc, plainly, as a stylistic choice with no biomechanical justification — unlike
  2.1, which is a real balance equation.
- **Acceptance**: for the same knee angle, changing `torsoRatio` measurably changes the resulting
  thigh segment's angle (not just torso). The existing "hip drops below the knee at BOTTOM, for
  every archetype" regression test must still pass with the comfort factor applied to each
  archetype's own torso ratio — re-verify numerically, don't assume the clamp band is automatically
  safe.

### 2.3 Rigidity and yaw are unaffected
- Every segment (shank/thigh/torso/head-neck) stays exactly `ratio × bodyScale` at every frame —
  unchanged from v0.49.0, and actually *reinforced* here, since solving an angle (rather than
  translating nodes) can never break rigidity by construction.
- `StickmanKinematics.applyYaw` (the turntable rotation from v0.49.0) is untouched — it's a
  separate, purely cosmetic post-process downstream of all of this.

---

## 3. Tech stack additions

None. Pure Kotlin, `domain/util/StickmanKinematics.kt` only.

---

## 4. Project structure (changed)

```
app/src/main/java/com/saiyanstrong/domain/util/
└── StickmanKinematics.kt   ← torsoLean solved from balance instead of read from PoseAngles;
                                thighLean gains a torsoRatio-driven comfort scale
```

Test file mirrors it: `StickmanKinematicsTest.kt` gains coupling-direction tests; existing rigidity
and depth tests re-verified (numbers may shift slightly since torso lean values change, but the
*invariants* — exact rigidity, hip below knee at BOTTOM — must still hold).

---

## 5. Code style (extends existing CLAUDE.md rules)

- Stays pure (`domain/util`, zero Android/Compose imports) — same split as everything else in this
  file.
- Constants (`TORSO_REFERENCE_RATIO`, `THIGH_TORSO_COMFORT_GAIN`, clamp bounds) are named,
  top-of-object `private const val`s with a one-line comment on what each does — no magic numbers
  buried inline, matching the existing `ANKLE_X`/`BODY_SCALE` precedent.

---

## 6. Testing strategy

- **Femur → torso coupling**: fixed knee angle, two different `thighRatio` values → solved torso
  lean differs between them (not just "doesn't crash" — an actual numeric comparison).
- **Torso → thigh coupling**: fixed knee angle, two different `torsoRatio` values → resulting
  thigh segment's angle (derivable from its endpoints) differs between them.
- **Rigidity**: existing shank/thigh/torso/head-neck exact-length test must still pass unchanged
  in spirit (rotation-only geometry guarantees it, but re-run for real, don't assume).
- **Depth**: existing "hip below knee at BOTTOM" test re-verified against the comfort-scaled thigh
  lean for all 4 archetypes' real torso ratios — deepen angles further only if a real failure
  turns up, not preemptively.
- **Balance**: new test confirming the bar lands at (or very near) mid-foot again now that it's
  solved rather than dropped — within float tolerance, for a representative set of ratios/angles.
- Full existing suite must stay green (`StickmanInterpolatorTest`, `StickmanRendererTest`
  included) — re-derive any expected values that assumed the old fixed-`torsoAngleDeg` behavior.

---

## 7. Boundaries

**Always do:**
- Keep this a pure rotation-only solve — never reintroduce a translation-based correction (that
  was the specific thing that broke rigidity in v0.48.0).
- Clamp both the comfort scale and the solved angle defensively, and document why each clamp
  exists.
- Run the full test suite + `assembleGithubDebug` before shipping, same release discipline as
  every prior sprint.

**Ask first about:**
- Exact values for `THIGH_TORSO_COMFORT_GAIN` and the clamp bands — pick a first-pass default
  that produces a visible-but-not-absurd effect across the Custom screen's slider ranges; confirm
  after it's tried if it feels off.

**Never do:**
- Don't reintroduce a translation-based bar correction.
- Don't touch `applyYaw`/the turntable feature — unrelated, already correct.
- Don't touch the VBT bar-path feature — unrelated, out of scope.

---

## 8. Notes

This turns out to actually *resolve* the tension flagged in the previous two specs (v0.48.0's
"proportions vs. bar pin" tradeoff, v0.49.0's explicit choice to drop the pin) rather than pick a
side again — solving for a rotation instead of a translation gets exact rigidity **and** a
mechanically real bar-over-mid-foot result at the same time. The femur↔torso coupling was the
missing piece that made this possible; it wasn't available to the earlier specs because nothing
had asked for the ratios and the angles to depend on each other yet.
