package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.PoseAngles
import com.saiyanstrong.domain.model.StickmanKeyframe

/**
 * Interpolates the 3 [PoseAngles] scalars between keyframes (never raw node positions — lerping
 * an (x,y) pair independently doesn't preserve segment length as a limb rotates, which is exactly
 * what made the scrub animation look wrong before this rework), then runs
 * [StickmanKinematics.buildNodes] on the blended angle to get the frame's actual node positions.
 * No IK solver — matches the spec's explicit Phase 1 scope.
 */
object StickmanInterpolator {

    /** @param progress 0.0 (first keyframe) to 1.0 (last keyframe), linear across all segments. */
    fun interpolate(
        keyframes: List<StickmanKeyframe>,
        ratios: LimbRatios,
        progress: Float,
        lift: LiftType = LiftType.SQUAT
    ): List<NodePosition> {
        if (keyframes.isEmpty()) return emptyList()
        val angles = interpolateAngles(keyframes, progress)
        return StickmanKinematics.buildNodes(ratios, angles, lift)
    }

    internal fun interpolateAngles(keyframes: List<StickmanKeyframe>, progress: Float): PoseAngles {
        if (keyframes.size == 1) return keyframes.single().angles

        val segmentCount = keyframes.size - 1
        val clamped = progress.coerceIn(0f, 1f)
        val globalT = clamped * segmentCount
        val segmentIndex = globalT.toInt().coerceIn(0, segmentCount - 1)
        val localT = globalT - segmentIndex

        val from = keyframes[segmentIndex].angles
        val to = keyframes[segmentIndex + 1].angles
        return PoseAngles(
            hipAngleDeg = lerp(from.hipAngleDeg, to.hipAngleDeg, localT),
            kneeAngleDeg = lerp(from.kneeAngleDeg, to.kneeAngleDeg, localT),
            torsoAngleDeg = lerp(from.torsoAngleDeg, to.torsoAngleDeg, localT),
            torsoLeanBiasDeg = lerp(from.torsoLeanBiasDeg, to.torsoLeanBiasDeg, localT)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
