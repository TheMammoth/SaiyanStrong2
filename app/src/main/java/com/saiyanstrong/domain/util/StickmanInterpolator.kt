package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.StickmanKeyframe

/** Pure keyframe interpolation for the biomechanics visualizer's scrub slider — no IK solver,
 * matching the spec's explicit Phase 1 scope (4 fixed keyframes, linear blend between the pair
 * the slider currently sits between). */
object StickmanInterpolator {

    /** @param progress 0.0 (first keyframe) to 1.0 (last keyframe), linear across all segments. */
    fun interpolate(keyframes: List<StickmanKeyframe>, progress: Float): List<NodePosition> {
        if (keyframes.isEmpty()) return emptyList()
        if (keyframes.size == 1) return keyframes.single().nodes

        val segmentCount = keyframes.size - 1
        val clamped = progress.coerceIn(0f, 1f)
        val globalT = clamped * segmentCount
        val segmentIndex = globalT.toInt().coerceIn(0, segmentCount - 1)
        val localT = globalT - segmentIndex

        val from = keyframes[segmentIndex].nodes
        val to = keyframes[segmentIndex + 1].nodes
        return from.zip(to).map { (a, b) ->
            require(a.id == b.id) { "Keyframe node order mismatch: ${a.id} vs ${b.id}" }
            NodePosition(id = a.id, x = lerp(a.x, b.x, localT), y = lerp(a.y, b.y, localT))
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
