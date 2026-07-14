package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.PoseAngles
import kotlin.math.cos
import kotlin.math.sin

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
 * available for future use/content) but is **not** fed into this geometry — [torsoAngleDeg]
 * already gives the torso's absolute lean directly (spec section 7's own column is literally
 * "Torso Angle (from vert.)"), and deriving the thigh from *both* hip and knee angles would
 * require them to already be geometrically self-consistent, which spec's hand-authored table
 * was never guaranteed to be. This is a documented, deliberate simplification, not an oversight.
 *
 * ## No bar-over-mid-foot correction — proportions win
 * A v0.48.0 revision added a horizontal-shift correction that translated every node from the hip
 * up so the bar landed exactly on mid-foot every frame — but since a translation can't move a
 * joint without changing the segment lengths on either side of it, that let the thigh segment
 * (knee-to-hip) drift a few percent off its true `thighRatio × bodyScale` length. Per an explicit
 * product decision, that correction has been removed entirely: every limb, including the thigh,
 * now holds its exact ratio-defined length at every interpolated frame, no exception. The bar
 * still lands close to mid-foot as a natural side-effect of the angle-driven chain (torso lean
 * partially offsets hip travel), but is no longer force-pinned to the exact pixel.
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

    fun buildNodes(ratios: LimbRatios, angles: PoseAngles): List<NodePosition> {
        val shankLean = (180f - angles.kneeAngleDeg) * 0.15f
        val thighLean = shankLean - (180f - angles.kneeAngleDeg)
        val torsoLean = angles.torsoAngleDeg

        // --- Foot and shank: planted, fixed for the whole rep ---
        val ankle = ANKLE_X to ANKLE_Y
        val knee = ankle + rotateUp(ratios.shankRatio * BODY_SCALE, shankLean)
        val toe = (ankle.first + ratios.footLenRatio * BODY_SCALE) to FLOOR_Y

        // --- Hip and up: no post-hoc correction — every segment stays exactly ratio-rigid ---
        val hip = knee + rotateUp(ratios.thighRatio * BODY_SCALE, thighLean)
        val neck = hip + rotateUp(ratios.torsoRatio * BODY_SCALE, torsoLean)
        val head = neck + rotateUp(ratios.headNeckRatio * BODY_SCALE, torsoLean)
        val bar = neck + rotateUp(ratios.barRiseRatio * BODY_SCALE, torsoLean)

        val gripHalf = ratios.gripHalfRatio * BODY_SCALE

        val (lHip, rHip) = side(hip, ratios.hipHalfRatio * BODY_SCALE)
        val (lKnee, rKnee) = side(knee, ratios.kneeHalfRatio * BODY_SCALE)
        val (lAnkle, rAnkle) = side(ankle, ratios.ankleHalfRatio * BODY_SCALE)
        val (lToe, rToe) = side(toe, ratios.ankleHalfRatio * BODY_SCALE)
        val (lShoulder, rShoulder) = side(neck, ratios.shoulderHalfRatio * BODY_SCALE)
        val (lWrist, rWrist) = side(bar, gripHalf)
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

    /** Vector from the lower joint to the upper joint: [leanDeg] measured from vertical, positive
     * leans toward +x (the direction the lifter faces). */
    private fun rotateUp(length: Float, leanDeg: Float): Pair<Float, Float> {
        val rad = Math.toRadians(leanDeg.toDouble())
        return (length * sin(rad)).toFloat() to (-length * cos(rad)).toFloat()
    }

    private fun side(center: Pair<Float, Float>, half: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> =
        (center.first - half to center.second) to (center.first + half to center.second)

    private fun midpoint(a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> =
        (a.first + b.first) / 2f to (a.second + b.second) / 2f

    private operator fun Pair<Float, Float>.plus(delta: Pair<Float, Float>): Pair<Float, Float> =
        (first + delta.first) to (second + delta.second)
}
