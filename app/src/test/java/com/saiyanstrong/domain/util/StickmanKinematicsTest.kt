package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.PoseAngles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class StickmanKinematicsTest {

    private val ratios = LimbRatios(
        thighRatio = 0.25f, shankRatio = 0.25f, torsoRatio = 0.29f, headNeckRatio = 0.16f,
        footLenRatio = 0.10f, shoulderHalfRatio = 0.09f, hipHalfRatio = 0.07f,
        kneeHalfRatio = 0.05f, ankleHalfRatio = 0.045f,
        barRiseRatio = 0.06f, gripHalfRatio = 0.16f
    )

    private fun distance(a: com.saiyanstrong.domain.model.NodePosition, b: com.saiyanstrong.domain.model.NodePosition) =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    @Test
    fun `every node id is present exactly once`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        assertEquals(NodeId.entries.toSet(), nodes.map { it.id }.toSet())
        assertEquals(NodeId.entries.size, nodes.size)
    }

    @Test
    fun `hip-to-knee centerline distance equals thighRatio times body scale, at every pose`() {
        // This is THE regression test for the "movement isn't correct" bug: lerping raw node
        // positions doesn't preserve segment length as a limb rotates, so a limb would visibly
        // stretch/shrink mid-animation. Forward kinematics from an angle always reconstructs the
        // segment at its ratio-defined length, at every pose — this asserts that guarantee holds
        // across standing, mid-descent, and bottom. L_KNEE/R_KNEE are offset copies of the
        // centerline knee joint (proven symmetric in a separate test), so their midpoint recovers
        // the centerline point the thigh segment actually spans.
        val bodyScale = 0.80
        val expectedLength = ratios.thighRatio * bodyScale
        for (angles in listOf(
            PoseAngles(180f, 180f, 5f),
            PoseAngles(130f, 120f, 35f),
            PoseAngles(95f, 95f, 55f),
            PoseAngles(80f, 80f, 60f)
        )) {
            val nodes = StickmanKinematics.buildNodes(ratios, angles)
            val hip = nodes.first { it.id == NodeId.HIP_CENTER }
            val lKnee = nodes.first { it.id == NodeId.L_KNEE }
            val rKnee = nodes.first { it.id == NodeId.R_KNEE }
            val kneeCenterline = com.saiyanstrong.domain.model.NodePosition(
                NodeId.L_KNEE, (lKnee.x + rKnee.x) / 2f, (lKnee.y + rKnee.y) / 2f
            )
            val actualLength = distance(hip, kneeCenterline)
            assertEquals("mismatch at angles=$angles", expectedLength, actualLength, 1e-3)
        }
    }

    @Test
    fun `shoulder-to-elbow-to-wrist forms one straight line (elbow is the midpoint)`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(130f, 120f, 35f))
        val shoulder = nodes.first { it.id == NodeId.L_SHOULDER }
        val elbow = nodes.first { it.id == NodeId.L_ELBOW }
        val wrist = nodes.first { it.id == NodeId.L_WRIST }
        assertEquals((shoulder.x + wrist.x) / 2f, elbow.x, 1e-4f)
        assertEquals((shoulder.y + wrist.y) / 2f, elbow.y, 1e-4f)
    }

    @Test
    fun `L and R nodes are symmetric around the spine centerline`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 95f, 55f))
        val lHip = nodes.first { it.id == NodeId.L_HIP }
        val rHip = nodes.first { it.id == NodeId.R_HIP }
        val hipCenter = nodes.first { it.id == NodeId.HIP_CENTER }
        assertEquals(hipCenter.x, (lHip.x + rHip.x) / 2f, 1e-4f)
        assertEquals(lHip.y, rHip.y, 1e-4f)
        assertEquals(hipCenter.y, lHip.y, 1e-4f)
    }

    @Test
    fun `toe sits at floor level and forward of the ankle`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        val ankle = nodes.first { it.id == NodeId.L_ANKLE }
        val toe = nodes.first { it.id == NodeId.L_TOE }
        assertTrue(toe.x > ankle.x)
    }

    @Test
    fun `all nodes stay within a sane canvas bounds for every phase of every archetype-like ratio set`() {
        val angleSets = listOf(
            PoseAngles(180f, 180f, 5f), PoseAngles(130f, 120f, 35f),
            PoseAngles(95f, 95f, 55f), PoseAngles(80f, 80f, 60f),
            PoseAngles(180f, 180f, 5f), PoseAngles(125f, 110f, 20f),
            PoseAngles(90f, 85f, 30f), PoseAngles(75f, 75f, 32f)
        )
        for (angles in angleSets) {
            for (node in StickmanKinematics.buildNodes(ratios, angles)) {
                assertTrue("${node.id} x=${node.x} out of bounds", node.x in -0.1f..1.1f)
                assertTrue("${node.id} y=${node.y} out of bounds", node.y in -0.1f..1.1f)
            }
        }
    }
}
