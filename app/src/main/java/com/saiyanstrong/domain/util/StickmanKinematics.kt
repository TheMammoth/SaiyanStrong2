package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.PoseAngles
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure forward kinematics (no IK solver — matches the spec's explicit Phase 1/2 scope) that
 * reconstructs all 18 node positions from [PoseAngles] + [LimbRatios]. Because this runs on
 * every interpolated angle (not just the 4 authored keyframes), a limb's on-screen length is
 * always exactly its ratio times body scale, at every point in the scrub — the animation can't
 * warp/stretch mid-rotation the way lerping raw node positions did before v0.47.1.
 *
 * ## The leg chain (knee-driven, depth-correct)
 * [PoseAngles.kneeAngleDeg] is the sole driver of the thigh's orientation relative to the shank
 * — the textbook forward-kinematics relationship for a 2-link hinge chain: thigh deviates from
 * the shank's own angle by exactly `(180° - kneeAngleDeg)`, backward (matching how a knee
 * actually folds — the femur rotates back-and-down as the knee closes, not forward). A prior
 * version of this formula damped that deviation by 0.5, which meant even a fully-flexed knee
 * angle could never rotate the thigh past horizontal — the hip could never actually drop below
 * the knee, which is why the squat "didn't look right." Removing the damping (full 1:1
 * deviation) is what makes real depth (hip.y > knee.y) reachable at all; see
 * [com.saiyanstrong.domain.util.StickmanKinematicsTest] for the regression test.
 *
 * [PoseAngles.hipAngleDeg] stays in the model (matches the spec's authored angle table and is
 * available for future use/content) but is **not** fed into this geometry, for the same reason
 * [torsoAngleDeg] no longer is either (see below) — deriving the thigh from a raw authored angle
 * would require that angle to already be geometrically self-consistent with everything else,
 * which the hand-authored table was never guaranteed to be. Documented, deliberate, not an
 * oversight.
 *
 * ## Torso angle is solved from a balance equation, not read from PoseAngles
 * [PoseAngles.torsoAngleDeg] is **not** used here. Two earlier revisions each picked one side of
 * a tradeoff: v0.48.0 translated every upper-body node so the bar landed exactly on mid-foot,
 * which broke the thigh's exact rigidity (a translation can't move a joint without changing the
 * segment lengths on either side of it); v0.49.0 dropped that correction entirely, restoring
 * exact rigidity but losing the bar-over-mid-foot guarantee. Solving for torso lean as a
 * *rotation* — instead of translating anything — gets both at once, since a rotation about the
 * hip never changes segment length: `torsoLean` is chosen so that
 * `ankleX + shankDx + thighDx + (torsoRatio + barRiseRatio) × bodyScale × sin(torsoLean) ==
 * midFootX`. This is also what makes the femur↔torso coupling physically real: a longer
 * [LimbRatios.thighRatio] pushes the hip further from the ankle at the same knee angle, which
 * this equation directly translates into a larger required torso lean — hand-verified before
 * writing this: at LONG_FEMUR's own BOTTOM ratios/angles the solve yields a visibly larger torso
 * lean than PROPORTIONAL's, matching this app's own archetype content copy ("Forward torso lean
 * is greater than standard" for LONG_FEMUR) that the kinematics never actually enforced before.
 * `sin(torsoLean)` is clamped to `[-1, 1]` before `asin` (an unreachable ratio combination could
 * otherwise be mathematically impossible to balance) and the resulting angle clamped to
 * `[0°, 90°]` (a squat torso never leans backward or past horizontal) — both defensive bounds,
 * not expected to trigger for any ratio reachable from the Custom Proportions sliders.
 *
 * ## Torso ratio nudges thigh lean too (explicitly a comfort factor, not physics)
 * The reverse coupling — dragging the TORSO ratio slider should also visibly move the femur —
 * has no real mechanical justification (there's no physical reason torso *length* dictates knee
 * bend at a fixed squat depth), so it's implemented as a small, clearly-labeled stylistic nudge:
 * [THIGH_TORSO_COMFORT_GAIN] scales how far the knee-driven deviation rotates the thigh, based on
 * how far [LimbRatios.torsoRatio] sits from [TORSO_REFERENCE_RATIO] (PROPORTIONAL's own torso
 * ratio — the value this whole rig was originally tuned against, so at exactly that ratio the
 * scale is 1.0 and behavior is unchanged). Clamped to `0.6..1.4` so it can never zero out or
 * invert the knee-driven rotation direction — see [com.saiyanstrong.domain.util.StickmanKinematicsTest]
 * for the regression coverage confirming the existing hip-below-knee depth guarantee still holds
 * with this factor applied.
 *
 * ## Turntable yaw (rendering-time only, not part of this chain)
 * [applyYaw] is a separate, purely cosmetic post-process — never called from [buildNodes] —
 * that fakes rotating the flat rig like a spinning paper cutout: every node's x-deviation from
 * the fixed mid-foot pivot ([ANKLE_X]) is scaled by `cos(yawDegrees)`. At yaw 0° the output is
 * identical to the un-rotated rig; toward ±90° the silhouette foreshortens to a thin vertical
 * sliver at the pivot. Not a real 3D projection — a cheap viewing convenience only.
 *
 * Single sagittal spine centerline (ankle -> knee -> hip -> neck -> head); L/R nodes are the
 * centerline point at that joint's height, offset symmetrically in x by a per-joint half-width
 * — the same stylized-diagram simplification used by real exercise-form illustrations, where
 * forward lean reads as a centerline x-shift rather than true sagittal foreshortening.
 *
 * The bar is modeled as rigidly attached to the upper back (rotates with the torso, the way a
 * racked bar actually does), with straight arms — elbow is the midpoint of shoulder-to-wrist, so
 * each arm renders as one clean line with no visible kink.
 */
object StickmanKinematics {

    private const val ANKLE_X = 0.5f
    private const val FLOOR_Y = 0.93f
    private const val ANKLE_Y = FLOOR_Y - 0.03f
    private const val BODY_SCALE = 0.80f

    /** PROPORTIONAL's own torso ratio — the reference point at which [THIGH_TORSO_COMFORT_GAIN]
     * has zero effect (comfort scale == 1.0), matching the rig's original tuning exactly. */
    private const val TORSO_REFERENCE_RATIO = 0.29f

    /** Stylistic knob only (see class KDoc "Torso ratio nudges thigh lean too") — not derived
     * from any real biomechanical relationship. */
    private const val THIGH_TORSO_COMFORT_GAIN = 1.5f
    private const val COMFORT_SCALE_MIN = 0.6f
    private const val COMFORT_SCALE_MAX = 1.4f

    /** Overhead press arm lean (degrees from vertical) at the rack — the arm points forward-and-up
     * so the bar sits in front at roughly shoulder height; it rotates to 0° (straight overhead) as
     * pressFraction reaches 1. */
    private const val OHP_RACK_ARM_LEAN = 78f

    // --- Bench press (supine layout — see [buildBenchNodes]) ---
    /** Y of the bench surface the torso lies along (y grows downward; the floor is at FLOOR_Y). */
    private const val BENCH_Y = 0.66f
    /** X of the hip on the bench; the torso runs from here toward the head (−x). */
    private const val BENCH_HIP_X = 0.58f
    /** How far above the chest the bar sits at the bottom of the press (pressFraction 0). */
    private const val BENCH_CHEST_GAP = 0.05f
    /** How far the elbows flare toward the feet at the bottom (0 at lockout) — reads as the elbow
     * bending, which a single straight-line arm can't show on its own. */
    private const val BENCH_ELBOW_FLARE = 0.13f

    fun buildNodes(
        ratios: LimbRatios,
        angles: PoseAngles,
        lift: LiftType = LiftType.SQUAT
    ): List<NodePosition> {
        // Bench is supine — a completely different layout (body horizontal on a bench, legs down to
        // the floor, bar pressing straight up over the chest). It doesn't share the standing
        // ankle→knee→hip→torso chain, so it builds its own node set rather than branch inside it.
        if (lift == LiftType.BENCH) return buildBenchNodes(ratios, angles)

        val kneeDeviation = 180f - angles.kneeAngleDeg
        val shankLean = kneeDeviation * 0.15f
        // The femur↔torso comfort nudge is a squat-only stylistic coupling (the squat's torso is
        // solved from balance); the deadlift's torso is an authored hinge angle, so its thigh is
        // pure knee-driven with no nudge.
        val comfortScale = if (lift == LiftType.SQUAT) {
            (1f + THIGH_TORSO_COMFORT_GAIN * (ratios.torsoRatio - TORSO_REFERENCE_RATIO))
                .coerceIn(COMFORT_SCALE_MIN, COMFORT_SCALE_MAX)
        } else 1f
        val thighLean = shankLean - kneeDeviation * comfortScale

        // --- Foot and shank: planted, fixed for the whole rep (shared by every lift) ---
        val ankle = ANKLE_X to ANKLE_Y
        val knee = ankle + rotateUp(ratios.shankRatio * BODY_SCALE, shankLean)
        val toe = (ankle.first + ratios.footLenRatio * BODY_SCALE) to FLOOR_Y

        // --- Hip and up ---
        val hip = knee + rotateUp(ratios.thighRatio * BODY_SCALE, thighLean)
        // Torso lean per lift: the squat SOLVES it (balance, bar over mid-foot); the deadlift
        // USES the authored hinge angle directly (torso pitch IS the defining feature of a
        // hinge — solving it would erase the archetype differences). Both then add the grind
        // bias as a rotation about the hip (never changes a segment length, so rigidity holds).
        // At bias 0 the squat branch is exactly the pre-existing solved lean, so squat is unchanged.
        val torsoLean = when (lift) {
            // Only the squat solves torso lean from the bar-over-mid-foot balance equation. Every
            // other lift authors it directly (a deadlift hinge, a near-vertical press) — solving
            // would be meaningless for a bar that isn't over the foot.
            LiftType.SQUAT -> (solveTorsoLean(ratios, ankle.first, hip) + angles.torsoLeanBiasDeg).coerceIn(0f, 90f)
            else -> (angles.torsoAngleDeg + angles.torsoLeanBiasDeg).coerceIn(0f, 90f)
        }
        val neck = hip + rotateUp(ratios.torsoRatio * BODY_SCALE, torsoLean)
        val head = neck + rotateUp(ratios.headNeckRatio * BODY_SCALE, torsoLean)

        val (lShoulder, rShoulder) = side(neck, ratios.shoulderHalfRatio * BODY_SCALE)

        // --- Arms + bar, per lift ---
        // Squat: bar racked on the upper back (rotates with the torso), wrists grip it either side.
        // Deadlift: arms hang straight down from the shoulders by a fixed arm length; the bar
        // spans the wrists and therefore tracks under the shoulders — the correct DL bar path,
        // with the bar's height falling out of shoulder height + arm reach (no bar-height field).
        val (bar, lWrist, rWrist) = when (lift) {
            LiftType.DEADLIFT -> {
                val armLen = ratios.armRatio * BODY_SCALE
                val lw = lShoulder.first to (lShoulder.second + armLen)
                val rw = rShoulder.first to (rShoulder.second + armLen)
                Triple(midpoint(lw, rw), lw, rw)
            }
            LiftType.OVERHEAD_PRESS -> {
                // Straight-line arm rotating from ~forward-horizontal at the rack to straight
                // overhead at lockout. pressFraction 0 → arm at OHP_RACK_ARM_LEAN (bar in front at
                // roughly shoulder height); pressFraction 1 → arm at 0° from vertical (bar over the
                // head). Arm length is fixed, so the segment stays rigid the whole press.
                val armLen = ratios.armRatio * BODY_SCALE
                val armLean = OHP_RACK_ARM_LEAN * (1f - angles.pressFraction)
                val lw = lShoulder + rotateUp(armLen, armLean)
                val rw = rShoulder + rotateUp(armLen, armLean)
                Triple(midpoint(lw, rw), lw, rw)
            }
            else -> {
                val b = neck + rotateUp(ratios.barRiseRatio * BODY_SCALE, torsoLean)
                val (lw, rw) = side(b, ratios.gripHalfRatio * BODY_SCALE)
                Triple(b, lw, rw)
            }
        }

        val (lHip, rHip) = side(hip, ratios.hipHalfRatio * BODY_SCALE)
        val (lKnee, rKnee) = side(knee, ratios.kneeHalfRatio * BODY_SCALE)
        val (lAnkle, rAnkle) = side(ankle, ratios.ankleHalfRatio * BODY_SCALE)
        val (lToe, rToe) = side(toe, ratios.ankleHalfRatio * BODY_SCALE)
        val lElbow = midpoint(lShoulder, lWrist)
        val rElbow = midpoint(rShoulder, rWrist)

        val nodes = mapOf(
            NodeId.HEAD to head, NodeId.NECK_BASE to neck,
            NodeId.L_SHOULDER to lShoulder, NodeId.R_SHOULDER to rShoulder,
            NodeId.L_ELBOW to lElbow, NodeId.R_ELBOW to rElbow,
            NodeId.L_WRIST to lWrist, NodeId.R_WRIST to rWrist,
            NodeId.HIP_CENTER to hip, NodeId.L_HIP to lHip, NodeId.R_HIP to rHip,
            NodeId.L_KNEE to lKnee, NodeId.R_KNEE to rKnee,
            NodeId.L_ANKLE to lAnkle, NodeId.R_ANKLE to rAnkle,
            NodeId.L_TOE to lToe, NodeId.R_TOE to rToe,
            NodeId.BAR to bar
        )
        return nodes.map { (id, point) -> NodePosition(id, point.first, point.second) }
    }

    /**
     * Supine bench-press layout (side view, head to the left). The torso lies flat along the bench
     * at [BENCH_Y]; the legs drop to the floor with the shins vertical and the feet planted; the
     * bar travels straight up over the chest as [PoseAngles.pressFraction] goes 0 → 1, and the
     * elbows flare toward the feet at the bottom (which a single straight-line arm can't show on
     * its own). Segments that are truly rigid on a real bench — torso, head, and the leg links —
     * hold their exact ratio length; the arm intentionally does not (the elbow bends), so it isn't
     * asserted rigid.
     */
    private fun buildBenchNodes(ratios: LimbRatios, angles: PoseAngles): List<NodePosition> {
        val scale = BODY_SCALE

        // Torso lies flat along the bench, head to the left.
        val hip = BENCH_HIP_X to BENCH_Y
        val neck = (hip.first - ratios.torsoRatio * scale) to BENCH_Y
        val head = (neck.first - ratios.headNeckRatio * scale) to BENCH_Y

        // Legs: shins vertical to the floor, thigh spans hip → knee toward the feet (+x). Solving
        // the thigh's horizontal reach from its fixed length keeps every leg link rigid AND lands
        // the foot on the floor for every archetype.
        val kneeY = FLOOR_Y - ratios.shankRatio * scale
        val thighLen = ratios.thighRatio * scale
        val verticalGap = kneeY - BENCH_Y
        val thighDx = sqrt((thighLen * thighLen - verticalGap * verticalGap).coerceAtLeast(0f))
        val kneeCenter = (hip.first + thighDx) to kneeY
        val ankleCenter = kneeCenter.first to FLOOR_Y
        val toeCenter = (ankleCenter.first + ratios.footLenRatio * scale) to FLOOR_Y

        // Arms + bar: the bar sits over the shoulders and travels straight up. Bottom (pressFraction
        // 0) = a small gap above the chest; lockout (1) = a full arm length above.
        val (lShoulder, rShoulder) = side(neck, ratios.shoulderHalfRatio * scale)
        val armLen = ratios.armRatio * scale
        val barY = neck.second - (BENCH_CHEST_GAP + (armLen - BENCH_CHEST_GAP) * angles.pressFraction)
        val lWrist = lShoulder.first to barY
        val rWrist = rShoulder.first to barY
        val bar = ((lWrist.first + rWrist.first) / 2f) to barY
        val flare = BENCH_ELBOW_FLARE * scale * (1f - angles.pressFraction)
        val lElbowMid = midpoint(lShoulder, lWrist)
        val rElbowMid = midpoint(rShoulder, rWrist)
        val lElbow = (lElbowMid.first + flare) to lElbowMid.second
        val rElbow = (rElbowMid.first + flare) to rElbowMid.second

        val (lHip, rHip) = side(hip, ratios.hipHalfRatio * scale)
        val (lKnee, rKnee) = side(kneeCenter, ratios.kneeHalfRatio * scale)
        val (lAnkle, rAnkle) = side(ankleCenter, ratios.ankleHalfRatio * scale)
        val (lToe, rToe) = side(toeCenter, ratios.ankleHalfRatio * scale)

        val nodes = mapOf(
            NodeId.HEAD to head, NodeId.NECK_BASE to neck,
            NodeId.L_SHOULDER to lShoulder, NodeId.R_SHOULDER to rShoulder,
            NodeId.L_ELBOW to lElbow, NodeId.R_ELBOW to rElbow,
            NodeId.L_WRIST to lWrist, NodeId.R_WRIST to rWrist,
            NodeId.HIP_CENTER to hip, NodeId.L_HIP to lHip, NodeId.R_HIP to rHip,
            NodeId.L_KNEE to lKnee, NodeId.R_KNEE to rKnee,
            NodeId.L_ANKLE to lAnkle, NodeId.R_ANKLE to rAnkle,
            NodeId.L_TOE to lToe, NodeId.R_TOE to rToe,
            NodeId.BAR to bar
        )
        return nodes.map { (id, point) -> NodePosition(id, point.first, point.second) }
    }

    /** Purely cosmetic rendering-time projection — see the class KDoc "Turntable yaw" section.
     * Never called from [buildNodes]; applied downstream, after interpolation. [yawDegrees] is
     * typically clamped by the caller (e.g. -80f..80f) to avoid the degenerate ±90° sliver. */
    fun applyYaw(nodes: List<NodePosition>, yawDegrees: Float): List<NodePosition> {
        if (yawDegrees == 0f) return nodes
        val scale = cos(Math.toRadians(yawDegrees.toDouble())).toFloat()
        return nodes.map { node ->
            node.copy(x = ANKLE_X + (node.x - ANKLE_X) * scale)
        }
    }

    /** The turntable's snap detents: front, three-quarter (±45°), and near-side (±80°, just
     * short of the degenerate ±90° sliver). Used by [StickmanCanvas] to snap yaw on drag release. */
    val YAW_DETENTS: List<Float> = listOf(-80f, -45f, 0f, 45f, 80f)

    /** Nearest snap detent to a given yaw — pure so it's unit-testable without a Canvas. */
    fun nearestYawDetent(yawDegrees: Float): Float =
        YAW_DETENTS.minByOrNull { kotlin.math.abs(it - yawDegrees) } ?: 0f

    /** Vector from the lower joint to the upper joint: [leanDeg] measured from vertical, positive
     * leans toward +x (the direction the lifter faces). */
    private fun rotateUp(length: Float, leanDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(leanDeg.toDouble())
        return (length * sin(rad)).toFloat() to (-length * cos(rad)).toFloat()
    }

    /** Solves the torso lean (degrees) that brings the bar back over mid-foot, given where the
     * knee-driven ankle→knee→hip chain already landed. See class KDoc "Torso angle is solved..."
     * for the derivation and both defensive clamps. */
    private fun solveTorsoLean(ratios: LimbRatios, ankleX: Float, hip: Pair<Float, Float>): Float {
        val midFootX = ankleX + ratios.footLenRatio * BODY_SCALE * 0.5f
        val torsoBarSpan = (ratios.torsoRatio + ratios.barRiseRatio) * BODY_SCALE
        val sinTorso = ((midFootX - hip.first) / torsoBarSpan).coerceIn(-1f, 1f)
        val solved = Math.toDegrees(asin(sinTorso.toDouble())).toFloat()
        return solved.coerceIn(0f, 90f)
    }

    private fun side(center: Pair<Float, Float>, half: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> =
        (center.first - half to center.second) to (center.first + half to center.second)

    private fun midpoint(a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> =
        (a.first + b.first) / 2f to (a.second + b.second) / 2f

    private operator fun Pair<Float, Float>.plus(delta: Pair<Float, Float>): Pair<Float, Float> =
        (first + delta.first) to (second + delta.second)
}
