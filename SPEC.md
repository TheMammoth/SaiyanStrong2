# SaiyanStrong — Biomechanics Visualizer: Turntable View + Perfectly Rigid Proportions

## Status: Draft — ready for implementation.

(Replaces the previous "Real Squat Mechanics + Custom Proportions" spec in this file — that
shipped as v0.48.0/v0.48.1; see CLAUDE.md "Sprint — Biomechanics Visualizer Phase 1" onward. User
tried v0.48.1 and asked for two more things: rotate the stickman to view it from different angles,
and stop letting the squat correction distort limb proportions at all.)

---

## 0. Decisions locked in via clarifying questions

- **Rotation approach**: **fake 3D turntable**, not a real 3D rig and not a plain 2D canvas tilt.
  The existing single-sagittal-plane rig stays exactly as-is; a yaw angle is applied as a
  post-process horizontal squash on top of `StickmanKinematics.buildNodes`' output — every node's
  x-deviation from a fixed pivot (mid-foot, `ANKLE_X`) is multiplied by `cos(yaw)`. At yaw = 0°
  (default) the view is pixel-identical to today. As yaw approaches 90° the whole cutout
  foreshortens toward a thin vertical sliver at the pivot — like spinning a flat paper cutout, not
  a real front/back view with different anatomy. Cheap, no new per-node depth data, existing
  kinematics/tests untouched (the transform is a rendering-time projection, not a change to
  `buildNodes`).
- **Proportion priority vs. the bar-over-mid-foot pin**: **perfect proportions win.** The
  horizontal-shift "bar-over-mid-foot" correction from v0.48.0 (which stretched the thigh segment
  by a few percent to keep the bar pinned to the exact mid-foot pixel every frame) is **removed
  entirely**. Every limb — including the thigh — now stays exactly `ratio × bodyScale` at every
  single interpolated frame, with no exceptions. The bar will still land close to mid-foot as a
  natural consequence of the angle-driven chain (torso lean compensates for hip travel, same
  physical reason real lifters' bar paths stay fairly vertical), but it is no longer
  force-corrected to the exact pixel. This is a one-way tradeoff, made explicitly by the user
  after being told what it costs.

---

## 1. Objective

Two independent changes to the existing Biomechanics Visualizer (`presentation/screens/
biomechanics/*`, `domain/util/StickmanKinematics.kt`), both requested after trying v0.48.1:

1. **Rotate the view.** Today the stickman only ever renders from one fixed side-on angle. Add an
   interactive yaw control so the user can spin the figure left/right on its own vertical axis and
   see it from a range of angles, without needing a real 3D rig.
2. **Never distort proportions during the squat.** Today's bar-over-mid-foot correction (v0.48.0)
   is a translation applied after the fact, which makes the *thigh* segment silently drift off its
   true ratio-defined length while every other limb stays rigid. Remove that correction so *every*
   limb, with no exception, holds its exact ratio at every frame of the animation — accepting that
   the bar will no longer be pinned to the literal mid-foot pixel.

Target users: unchanged — anyone on the "Body" tab, viewing a fixed archetype, comparing
archetypes, or using their own Custom Proportions.

---

## 2. Core features & acceptance criteria

### 2.1 Turntable yaw rotation
- New pure function, `StickmanKinematics.applyYaw(nodes: List<NodePosition>, yawDegrees: Float):
  List<NodePosition>` (or a new sibling object `StickmanYawProjection` — final placement decided
  during implementation, following the existing `domain/util/` pure-core convention). For every
  node: `newX = pivotX + (node.x - pivotX) * cos(yawDegrees)`, `y` unchanged. `pivotX` is the
  fixed `ANKLE_X` constant (mid-foot) — same pivot the feet plant on, so rotation reads as
  "spinning in place" rather than sliding sideways.
- Applied at **render time only**, downstream of `StickmanInterpolator.interpolate(...)` — never
  inside `buildNodes` itself, so every existing kinematics/interpolation test (rigidity, depth,
  symmetry) continues to assert against the un-rotated rig and needs zero changes.
- **Control**: drag horizontally anywhere on the `StickmanCanvas` to rotate — this is the natural
  "spin the figure" gesture and avoids adding a second slider next to the existing scrub slider.
  Yaw is clamped to a sensible range (e.g. −80°..80°, stopping short of the degenerate 90° sliver)
  and is session-only state (resets to 0° on screen open) — not persisted, since it's a viewing
  convenience, not a body-proportion setting.
- Wired into every screen that hosts a `StickmanCanvas`: `BiomechanicsVisualizerScreen`,
  `BiomechanicsCompareScreen` (each panel rotates independently), `CustomProportionsScreen`,
  `ArchetypeSelectionScreen`'s small preview cards excluded (static thumbnails, not worth the
  gesture surface on a tiny card).
- **Acceptance**: at yaw = 0°, rendered output is identical to today (regression-testable: `applyYaw(nodes,
  0f) == nodes`, exact equality). Dragging left/right on the canvas visibly rotates the figure in
  real time with no lag (pure per-frame recompute, same cost class as the existing scrub).

### 2.2 Remove the bar-over-mid-foot correction — perfect proportion rigidity
- Delete the horizontal-shift block in `StickmanKinematics.buildNodes` (the `midFootX`/
  `correction`/`hipC`/`neckC`/`headC`/`barC` translation) entirely. `hip`, `neck`, `head`, and
  `bar` are used directly, uncorrected, as already computed by the angle-driven chain.
- Update the class-level KDoc: remove the "Bar-over-mid-foot correction" section and its stated
  thigh-rigidity tradeoff — it no longer applies.
- **Acceptance**: a new/updated regression test asserts the thigh segment (`knee`→`hip`, via the
  same L/R-midpoint `centerline()` helper already used for shank/torso) stays exactly
  `thighRatio × bodyScale` at every interpolated progress step, for every archetype — closing the
  one gap the existing "shank, torso, head-neck stay rigid" test explicitly excluded.
- The old exact-equality bar-over-mid-foot test is removed (the invariant it asserted no longer
  holds by design) — optionally replaced with a loose sanity check that the bar stays within some
  generous band of mid-foot at BOTTOM, if useful, but not a hard requirement.

---

## 3. Tech stack additions

None. Pure Kotlin (`domain/util/StickmanKinematics.kt` simplified; new pure yaw-projection
function/object) + a `pointerInput`/`detectDragGestures` modifier on the existing Compose
`StickmanCanvas`. No new dependencies.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── domain/util/
│   └── StickmanKinematics.kt          ← remove bar-over-midfoot correction block + KDoc
│   └── (new) yaw projection fn/object ← pure, StickmanKinematics.applyYaw or new sibling file
├── presentation/screens/biomechanics/
│   ├── StickmanCanvas.kt              ← + drag-to-rotate gesture, applies yaw before draw
│   ├── BiomechanicsVisualizerScreen.kt/ViewModel.kt   ← thread yaw state through (session-only)
│   ├── BiomechanicsCompareScreen.kt/ViewModel.kt      ← per-panel independent yaw state
│   └── CustomProportionsScreen.kt/ViewModel.kt        ← same drag-to-rotate on its canvas
```

Test files mirror each changed pure-logic file, per this project's "pure core, untested Compose
shell" split (`StickmanKinematicsTest.kt` updated; new yaw-projection test file).

---

## 5. Code style (extends existing CLAUDE.md rules)

- Yaw projection stays pure (`domain/util`, zero Android/Compose imports), same split as every
  other kinematics function today.
- No hardcoded colors, no new dependencies, no new persistence — yaw is transient UI state
  (`remember`/ViewModel `StateFlow`, not DataStore).
- Drag gesture uses Compose's own first-party `pointerInput { detectDragGestures { ... } }` —
  consistent with this project's "Compose only, first-party APIs" pattern used elsewhere
  (`Animatable`, `rememberInfiniteTransition` etc. in the VBT reticle work).

---

## 6. Testing strategy

- **Yaw identity at 0°**: `applyYaw(nodes, 0f)` returns nodes with x unchanged (exact equality) —
  locks in "default view matches today" permanently.
- **Yaw squashes toward the pivot**: at some nonzero yaw (e.g. 45°/90°), every node's x moves
  strictly closer to `ANKLE_X` than at yaw 0° (except nodes already exactly at the pivot) —
  verifies the squash direction/magnitude without over-specifying exact pixel values.
- **Thigh rigidity**: new test parallel to the existing shank/torso/head-neck rigidity test,
  sweeping progress 0.0→1.0 across every archetype, asserting thigh length stays exactly
  `thighRatio × bodyScale` (tolerance 1e-4, matching existing style).
- Full existing suite (`StickmanKinematicsTest`, `StickmanInterpolatorTest`,
  `StickmanRendererTest`) must stay green — re-derive any expected values that assumed the
  now-removed correction (the old bar-over-mid-foot exact-equality test is expected to be
  deleted/replaced, not "fixed" to still pass against a no-longer-true invariant).

---

## 7. Boundaries

**Always do:**
- Keep the yaw transform a pure, separately-testable function — never bake it into `buildNodes`.
- Preserve every other existing archetype/CUSTOM behavior — this sprint only removes the
  bar-pin correction and adds a rendering-time rotation, nothing else about kinematics changes.
- Run the full test suite + `assembleGithubDebug` before shipping, same release discipline as
  every prior sprint (version bump before build, badging verification, explicit file staging).

**Ask first about:**
- Exact yaw clamp range and drag sensitivity (pixels-per-degree) — pick a sane default, confirm
  after it's tried once if it feels off.

**Never do:**
- Don't build a real 3D rig (per-node depth/z data, 3D projection) — explicitly ruled out in favor
  of the cheap cos(yaw) squash.
- Don't re-add any form of bar-pinning correction — proportions win outright per the locked-in
  decision above.
- Don't touch the VBT bar-path feature (`presentation/screens/barpath/*`) — unrelated, out of
  scope even though both involve "bar path."

---

## 8. Notes

Removing the bar-over-mid-foot correction is a genuine step back on the "bar always over mid-foot"
mechanical-accuracy front from v0.48.0 — flagged, not silently absorbed. The user chose perfect
proportions over that pin when told the tradeoff explicitly. The turntable rotation is cosmetic
only (a rendering projection), so it carries no mechanical-accuracy tradeoff of its own — the
figure is still the same single-sagittal-plane rig underneath, just viewed at a squash angle.
