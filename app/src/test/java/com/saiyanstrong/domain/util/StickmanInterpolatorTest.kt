package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BiomechanicsPhase
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.model.StickmanKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickmanInterpolatorTest {

    private fun frame(phase: BiomechanicsPhase, x: Float, y: Float) =
        StickmanKeyframe(phase, listOf(NodePosition(NodeId.HEAD, x, y)))

    private val keyframes = listOf(
        frame(BiomechanicsPhase.STANDING, 0f, 0f),
        frame(BiomechanicsPhase.DESCENT_MID, 1f, 1f),
        frame(BiomechanicsPhase.PARALLEL, 2f, 2f),
        frame(BiomechanicsPhase.BOTTOM, 3f, 3f)
    )

    @Test
    fun `progress 0 returns the first keyframe exactly`() {
        val result = StickmanInterpolator.interpolate(keyframes, 0f)
        assertEquals(0f, result.single().x)
    }

    @Test
    fun `progress 1 returns the last keyframe exactly`() {
        val result = StickmanInterpolator.interpolate(keyframes, 1f)
        assertEquals(3f, result.single().x, 1e-4f)
    }

    @Test
    fun `progress at exact segment boundary lands on that keyframe`() {
        // 4 keyframes = 3 segments; progress 1/3 should land exactly on DESCENT_MID (x=1)
        val result = StickmanInterpolator.interpolate(keyframes, 1f / 3f)
        assertEquals(1f, result.single().x, 1e-3f)
    }

    @Test
    fun `progress mid-segment linearly blends between the two surrounding keyframes`() {
        // Halfway through the first segment (progress 1/6): x should be 0.5
        val result = StickmanInterpolator.interpolate(keyframes, 1f / 6f)
        assertEquals(0.5f, result.single().x, 1e-3f)
    }

    @Test
    fun `progress out of range is clamped, not extrapolated`() {
        val below = StickmanInterpolator.interpolate(keyframes, -0.5f)
        val above = StickmanInterpolator.interpolate(keyframes, 1.5f)
        assertEquals(0f, below.single().x)
        assertEquals(3f, above.single().x, 1e-4f)
    }

    @Test
    fun `single keyframe list returns it unchanged regardless of progress`() {
        val result = StickmanInterpolator.interpolate(listOf(keyframes[2]), 0.7f)
        assertEquals(2f, result.single().x)
    }

    @Test
    fun `empty keyframe list returns empty`() {
        assertTrue(StickmanInterpolator.interpolate(emptyList(), 0.5f).isEmpty())
    }
}
