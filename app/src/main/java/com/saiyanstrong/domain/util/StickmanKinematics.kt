package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.PoseAngles
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure forward kinematics (no IK solver — matches the spec's explicit Phase 1 scope) that
 * reconstructs all 18 node positions from [PoseAngles] + [LimbRatios]. Because this runs on
 * every interpolated angle (not just the 4 authored keyframes), a limb's on-screen length is
 * always exactly its ratio times body scale, at every point in the scrub — the animation can't
 * warp/stretch mid-rotation the way lerping raw node positions could.
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
        val thighLean = -(180f - angles.hipAngleDeg) * 0.5f + shankLean
        val torsoLean = angles.torsoAngleDeg

        val ankle = ANKLE_X to ANKLE_Y
        val knee = ankle + rotateUp(ratios.shankRatio * BODY_SCALE, shankLean)
        val hip = knee + rotateUp(ratios.thighRatio * BODY_SCALE, thighLean)
        val neck = hip + rotateUp(ratios.torsoRatio * BODY_SCALE, torsoLean)
        val head = neck + rotateUp(ratios.headNeckRatio * BODY_SCALE, torsoLean)
        val toe = (ankle.first + ratios.footLenRatio * BODY_SCALE) to FLOOR_Y

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
