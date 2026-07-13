# SaiyanStrong — Biomechanics Visualizer: Real Squat Mechanics + Custom Proportions

## Status: Draft — ready for implementation.

(Replaces the previous "Best-Effort VBT" spec in this file — that shipped as v0.26.0; see
CLAUDE.md "Sprint 29" onward. This spec is the follow-up to the Biomechanics Visualizer's Phase 1
MVP (v0.47.0) and the angle-based-kinematics bug fix (v0.47.1) — the user tried v0.47.1 and asked
for two more things: physically-correct squat mechanics, and live in-app sliders per limb.)

---

## 0. Decisions locked in via clarifying questions

- **Bar-over-mid-foot fix**: the user's answer combined a new requirement with the question —
  "the animation should move till the hip joint is lower than the knee joint while keeping the
  bar on the middle of the foot." Read together with no pushback on the simpler option offered,
  this is implemented as a **horizontal-shift correction** on top of today's existing
  angle-driven forward kinematics (build the rig normally, then translate every node in x so the
  bar sits above mid-foot) — not a full inverse-kinematics solve. Cheap, keeps the existing
  angle-driven chain and its tests intact, and is sufficient to satisfy the stated requirement
  when combined with a depth check (below).
- **Foot size input**: normalized ratio slider (`footLenRatio`, fraction of body height) — same
  as every other limb, no real-world units/calibration step.
- **Slider scope**: **permanent end-user feature**, not a hidden dev-tuning screen. Every lifter
  gets a 5th "Custom" option alongside the 4 fixed archetypes, with live sliders for their own
  proportions, persisted across sessions.

---

## 1. Objective

Two problems, one root fix each:

1. **The squat still doesn't move like a squat.** `StickmanKinematics.buildNodes` drives 3
   independent angles (hip, knee, torso) with no constraint between them. A real barbell squat
   has one: **the bar stays directly above mid-foot for the entire rep**, regardless of torso
   lean — that's what makes a squat a squat. Nothing today enforces that. It also needs to
   visibly reach depth: **the hip must drop below the knee** at the bottom of the lift, which
   today's angle table doesn't guarantee (needs verifying, not assuming).
2. **Tuning requires editing JSON and rebuilding the app.** Every lifter should be able to dial
   in their own proportions via sliders (femur/thigh, shin/shank, torso, shoulder width, hip
   width, foot length) and see the stickman update live, as a permanent alternative to the 4
   fixed archetypes — not a one-off authoring tool.

Target users: any SaiyanStrong user opening the "Body" tab — both people picking a rough
archetype and people who want to model their own exact proportions.

---

## 2. Core features & acceptance criteria

### 2.1 Bar-over-mid-foot constraint
- After the existing angle-driven FK builds a candidate rig, apply a **horizontal shift** to
  every node so the `BAR` node's x exactly equals mid-foot x (today `ANKLE_X = 0.5f`, a fixed
  centered constant — stays fixed as *where the feet plant*, but becomes the real target the
  shift solves toward rather than an incidental byproduct of the arm-rig math).
- This is a correction pass bolted onto today's FK, not a new solver — the leg/torso chain's
  internal angles and relative shape are unchanged; the whole rig just translates in x.
- **Acceptance**: at every interpolated progress value (0.0–1.0, not just the 4 keyframes), for
  every archetype including CUSTOM, `barX ≈ midFootX` within float tolerance. Regression test
  sweeps progress the same way the v0.47.1 rigid-limb-length test did.

### 2.2 Depth requirement (hip below knee at BOTTOM)
- At the `BOTTOM` keyframe, for every archetype (and CUSTOM's shared angle template — see 2.4),
  `hip.y > knee.y` (y grows downward in this rig, so greater y = physically lower). May require
  deepening the current BOTTOM-phase hip/knee angles from what shipped in v0.47.0/v0.47.1 —
  verify numerically against the real FK output, don't assume the existing table already passes.
- **Acceptance**: unit test per archetype (+ CUSTOM template) asserting `hip.y > knee.y` at
  BOTTOM, with a comment explaining the physical meaning, not a bare numeric assertion.

### 2.3 Foot length as a normalized ratio
- `LimbRatios.footLenRatio` already exists and stays as-is (fraction of body height, no units).
  No behavior change here beyond it becoming one of the 6 CUSTOM sliders (2.4).

### 2.4 Custom Proportions — permanent end-user feature
- New `Archetype.CUSTOM` enum value, a 5th option alongside the 4 fixed archetypes.
- New screen: live-updating stickman + one slider each for **femur (thigh) ratio, shin (shank)
  ratio, torso ratio, shoulder width (half) ratio, hip width (half) ratio, foot length ratio** —
  six sliders. `kneeHalfRatio`/`ankleHalfRatio`/`barRiseRatio`/`gripHalfRatio` stay fixed
  constants (rig/rendering detail, not something a lifter recognizes about their own body).
- Dragging a slider updates the stickman immediately — pure local computation
  (`StickmanKinematics.buildNodes`), no debounce needed.
- Scrub slider still works in CUSTOM mode: pose angles come from a shared template (reuse
  PROPORTIONAL's angle table — sagittal joint angles are a movement-pattern choice, not a body
  proportion, so the user isn't asked to set their own hip/knee/torso angles here).
- **Persistence**: custom ratios saved to DataStore (new `customLimbRatiosJson` key, encoded via
  the `kotlinx.serialization.json.Json` instance already used elsewhere in this codebase) and
  restored on next open. Selecting CUSTOM as the active archetype persists the same way
  `selectedArchetypeName` already does.
- **Acceptance**: Body tab → "Custom" → drag each of the 6 sliders → stickman updates live at
  every step → close and reopen the app → custom archetype and its exact ratios are unchanged.

---

## 3. Tech stack additions

None. Pure Kotlin (`domain/model/LimbRatios.kt`, `domain/util/StickmanKinematics.kt` extended)
+ existing Compose Canvas/Slider patterns already used in `BiomechanicsVisualizerScreen` + the
existing DataStore/UserRepository get/set pattern. No new dependencies.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── domain/model/
│   └── Archetype.kt                          ← + CUSTOM
├── domain/util/
│   └── StickmanKinematics.kt                 ← + horizontal-shift bar-over-midfoot correction
├── domain/repository/
│   └── BiomechanicsRepository.kt             ← CUSTOM resolves ratios from UserRepository,
│                                                 angles from the PROPORTIONAL template
├── domain/repository/UserRepository.kt +
├── data/repository/UserRepositoryImpl.kt     ← + get/setCustomLimbRatios (mirrors
│                                                 getSelectedArchetype precedent exactly)
├── data/datastore/UserPreferencesDataStore.kt ← + customLimbRatiosJson key
├── presentation/screens/biomechanics/
│   ├── CustomProportionsScreen.kt            ← NEW: 6 sliders + live StickmanCanvas + scrub
│   ├── CustomProportionsViewModel.kt         ← NEW
│   └── ArchetypeSelectionScreen.kt           ← + 5th "Custom" card
└── presentation/navigation/
    ├── Screen.kt                             ← + CustomProportions route
    └── NavGraph.kt                           ← wire it
```

Test files mirror each changed pure-logic file, per this project's "pure core, untested Compose
shell" split (see `StickmanKinematicsTest.kt`, `StickmanInterpolatorTest.kt` for precedent).

---

## 5. Code style (extends existing CLAUDE.md rules)

- The horizontal-shift correction, the depth check, and ratio↔JSON round-tripping stay pure
  (`domain/util`/`domain/model`, zero Android/Compose imports) — same split as
  `StickmanKinematics`/`StickmanInterpolator` today. ViewModels stay thin wrappers.
- No hardcoded colors — reuse `StickmanBody`/`StickmanBar`/`StickmanFloor`/`NeonGreen`/
  `PowerAmber` tokens already in `presentation/theme/Color.kt`.
- Sliders use Material3 `Slider`, matching the existing scrub-slider styling (`NeonGreen`
  thumb/track) in `BiomechanicsVisualizerScreen`/`BiomechanicsCompareScreen`.
- `customLimbRatiosJson`/`getCustomLimbRatios`/`setCustomLimbRatios` follow the exact
  `selectedArchetypeName`/`getSelectedArchetype`/`setSelectedArchetype` precedent — no new
  persistence pattern invented.

---

## 6. Testing strategy

- **Bar-over-midfoot**: parametrized test sweeping progress 0.0→1.0 (11 samples, matching the
  existing rigid-limb-length test's shape) for every archetype + CUSTOM, asserting
  `barX ≈ midFootX` within float tolerance.
- **Depth**: one test per archetype (+ CUSTOM template) asserting `hip.y > knee.y` at BOTTOM.
- **Custom ratio persistence**: a pure encode→decode round-trip test on `LimbRatios` confirms it
  survives JSON serialization unchanged.
- **Slider → live update**: no unit test possible for the actual Compose redraw (same standing
  limitation as every Canvas composable in this codebase) — verified by running the app.
- Full existing suite (`StickmanKinematicsTest`, `StickmanInterpolatorTest`,
  `StickmanRendererTest`) must stay green. The horizontal-shift correction changes `buildNodes`'
  output, so existing expected values may need updating — re-derive the correct numbers, don't
  just delete or loosen failing assertions.

---

## 7. Boundaries

**Always do:**
- Keep the correction pure/testable, no IK solver (per the user's explicit choice above).
- Preserve existing archetype behavior (LONG_FEMUR/SHORT_FEMUR/PROPORTIONAL/WIDE_HIP) — CUSTOM
  is additive, a 5th option, not a replacement.
- Run the full test suite + `assembleGithubDebug` before shipping, same release discipline as
  every prior sprint (version bump before build, badging verification, explicit file staging).

**Ask first about:**
- Exact slider ranges/steps for each ratio — pick sane defaults centered on the existing 4
  archetypes' values, but confirm before locking them in if the first pass looks off.
- Where "Custom" lives in the archetype selection UI (5th grid card vs. a separate button) —
  defaults to a 5th card in the existing 2×2 grid unless told otherwise.

**Never do:**
- Don't touch the VBT bar-path feature (`presentation/screens/barpath/*`) — a different feature,
  out of scope even though both involve "bar path."
- Don't add a full 2D/3D inverse-kinematics solver — explicitly ruled out for this pass.
- Don't ship without re-verifying the full existing test suite passes with real, re-derived
  expected values (not stubbed-out or loosened assertions).

---

## 8. Notes

This turns the biomechanics visualizer from "4 fixed illustrative poses" into "a lightweight
per-user squat model," which is a meaningfully bigger commitment than the original Phase 1 MVP
scoped — flagged here rather than silently absorbed, since it moves past the original spec's own
stated Phase 1 boundary ("4 fixed archetypes, no continuous sliders"). The horizontal-shift
correction and depth check are both cheap, targeted fixes; the CUSTOM archetype + 6-slider screen
is the actual size of this sprint.
