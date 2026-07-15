package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.BiomechanicsPhase
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.PoseAngles
import com.saiyanstrong.domain.model.StickmanKeyframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StickmanInterpolatorTest {

    private val ratios = LimbRatios(
        thighRatio = 0.25f, shankRatio = 0.25f, torsoRatio = 0.29f, headNeckRatio = 0.16f,
        footLenRatio = 0.10f, shoulderHalfRatio = 0.09f, hipHalfRatio = 0.07f,
        kneeHalfRatio = 0.05f, ankleHalfRatio = 0.045f,
        barRiseRatio = 0.06f, gripHalfRatio = 0.16f
    )

    private fun frame(phase: BiomechanicsPhase, hip: Float, knee: Float, torso: Float) =
        StickmanKeyframe(phase, PoseAngles(hip, knee, torso))

    private val keyframes = listOf(
        frame(BiomechanicsPhase.STANDING, 180f, 180f, 5f),
        frame(BiomechanicsPhase.DESCENT_MID, 130f, 120f, 35f),
        frame(BiomechanicsPhase.PARALLEL, 95f, 95f, 55f),
        frame(BiomechanicsPhase.BOTTOM, 80f, 80f, 60f)
    )

    // --- angle interpolation (pure, no kinematics involved) ---

    @Test
    fun `progress 0 returns the first keyframe's angles exactly`() {
        val angles = StickmanInterpolator.interpolateAngles(keyframes, 0f)
        assertEquals(180f, angles.hipAngleDeg)
        assertEquals(5f, angles.torsoAngleDeg)
    }

    @Test
    fun `progress 1 returns the last keyframe's angles exactly`() {
        val angles = StickmanInterpolator.interpolateAngles(keyframes, 1f)
        assertEquals(80f, angles.hipAngleDeg, 1e-3f)
        assertEquals(60f, angles.torsoAngleDeg, 1e-3f)
    }

    @Test
    fun `progress at exact segment boundary lands on that keyframe`() {
        // 4 keyframes = 3 segments; progress 1/3 lands exactly on DESCENT_MID
        val angles = StickmanInterpolator.interpolateAngles(keyframes, 1f / 3f)
        assertEquals(130f, angles.hipAngleDeg, 1e-2f)
        assertEquals(35f, angles.torsoAngleDeg, 1e-2f)
    }

    @Test
    fun `progress mid-segment linearly blends the two surrounding keyframes' angles`() {
        val angles = StickmanInterpolator.interpolateAngles(keyframes, 1f / 6f) // halfway through segment 1
        assertEquals(155f, angles.hipAngleDeg, 1e-2f) // (180+130)/2
        assertEquals(20f, angles.torsoAngleDeg, 1e-2f) // (5+35)/2
    }

    @Test
    fun `progress out of range is clamped, not extrapolated`() {
        val below = StickmanInterpolator.interpolateAngles(keyframes, -0.5f)
        val above = StickmanInterpolator.interpolateAngles(keyframes, 1.5f)
        assertEquals(180f, below.hipAngleDeg)
        assertEquals(80f, above.hipAngleDeg, 1e-3f)
    }

    @Test
    fun `single keyframe list returns its angles unchanged regardless of progress`() {
        val angles = StickmanInterpolator.interpolateAngles(listOf(keyframes[2]), 0.7f)
        assertEquals(95f, angles.hipAngleDeg)
    }

    // --- end-to-end: interpolate() feeds angles through kinematics ---

    @Test
    fun `interpolate produces nodes with shank length rigid at every progress step`() {
        // Since v0.49.0 there's no bar-over-midfoot correction at all — every segment (shank
        // included) is provably constant-length across the whole scrub, matching the v0.47.1
        // regression test's spirit. See StickmanKinematicsTest for the full-chain (incl. thigh)
        // rigidity coverage.
        val bodyScale = 0.80
        val expectedLength = ratios.shankRatio * bodyScale
        for (step in 0..10) {
            val progress = step / 10f
            val nodes = StickmanInterpolator.interpolate(keyframes, ratios, progress)
            val ankle = nodes.first { it.id == NodeId.L_ANKLE }
            val knee = nodes.first { it.id == NodeId.L_KNEE }
            val actualLength = hypot((ankle.x - knee.x).toDouble(), (ankle.y - knee.y).toDouble())
            assertTrue(
                "shank length drifted at progress=$progress: expected ~$expectedLength, got $actualLength",
                Math.abs(actualLength - expectedLength) < 1e-3
            )
        }
    }

    @Test
    fun `torsoLeanBiasDeg interpolates linearly and lands exactly on keyframe values`() {
        val ascent = listOf(
            StickmanKeyframe(BiomechanicsPhase.BOTTOM, PoseAngles(72f, 57f, 47f, 0f)),
            StickmanKeyframe(BiomechanicsPhase.ASCENT_STICK, PoseAngles(102f, 87f, 45f, 16f))
        )
        assertEquals(0f, StickmanInterpolator.interpolateAngles(ascent, 0f).torsoLeanBiasDeg)
        assertEquals(16f, StickmanInterpolator.interpolateAngles(ascent, 1f).torsoLeanBiasDeg, 1e-3f)
        assertEquals(8f, StickmanInterpolator.interpolateAngles(ascent, 0.5f).torsoLeanBiasDeg, 1e-3f)
    }

    @Test
    fun `empty keyframe list returns empty nodes`() {
        assertTrue(StickmanInterpolator.interpolate(emptyList(), ratios, 0.5f).isEmpty())
    }
}
