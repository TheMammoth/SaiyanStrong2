package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
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
        barRiseRatio = 0.04f, gripHalfRatio = 0.12f
    )

    // Real archetype angle tables (v0.48.1), for depth/end-to-end checks.
    private fun ratiosOf(thigh: Float, shank: Float, torso: Float, head: Float, hipHalf: Float) = LimbRatios(
        thighRatio = thigh, shankRatio = shank, torsoRatio = torso, headNeckRatio = head,
        footLenRatio = 0.10f, shoulderHalfRatio = 0.090f, hipHalfRatio = hipHalf,
        kneeHalfRatio = 0.050f, ankleHalfRatio = 0.045f, barRiseRatio = 0.04f, gripHalfRatio = 0.12f
    )
    private val longFemurRatios = ratiosOf(thigh = 0.260f, shank = 0.240f, torso = 0.26f, head = 0.19f, hipHalf = 0.070f)
    private val shortFemurRatios = ratiosOf(thigh = 0.200f, shank = 0.300f, torso = 0.32f, head = 0.13f, hipHalf = 0.070f)
    private val proportionalRatios = ratiosOf(thigh = 0.230f, shank = 0.270f, torso = 0.29f, head = 0.16f, hipHalf = 0.070f)
    private val wideHipRatios = ratiosOf(thigh = 0.230f, shank = 0.270f, torso = 0.29f, head = 0.16f, hipHalf = 0.105f)

    private val archetypeBottomAngles = mapOf(
        "LONG_FEMUR" to (longFemurRatios to PoseAngles(75f, 58f, 62f)),
        "SHORT_FEMUR" to (shortFemurRatios to PoseAngles(70f, 56f, 34f)),
        "PROPORTIONAL" to (proportionalRatios to PoseAngles(72f, 57f, 47f)),
        "WIDE_HIP" to (wideHipRatios to PoseAngles(72f, 57f, 47f))
    )

    private fun node(nodes: List<NodePosition>, id: NodeId) = nodes.first { it.id == id }

    private fun distance(a: NodePosition, b: NodePosition) =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** L/R node pairs carry different per-joint half-widths (e.g. ankleHalfRatio != kneeHalfRatio),
     * so an L-side-to-L-side distance is NOT the true centerline segment length — it's off by
     * whatever the two joints' half-widths differ by. Recovering the centerline via the L/R
     * midpoint (exact, since they're symmetric by construction) is the only correct way to
     * measure a segment spanning two different joints. */
    private fun centerline(nodes: List<NodePosition>, left: NodeId, right: NodeId): NodePosition {
        val l = node(nodes, left)
        val r = node(nodes, right)
        return NodePosition(left, (l.x + r.x) / 2f, l.y)
    }

    @Test
    fun `every node id is present exactly once`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        assertEquals(NodeId.entries.toSet(), nodes.map { it.id }.toSet())
        assertEquals(NodeId.entries.size, nodes.size)
    }

    @Test
    fun `shank, thigh, torso, and head-neck segments all stay exactly rigid at every pose`() {
        // Since the v0.48.0 bar-over-midfoot correction was removed, there is no longer any
        // exception here: every segment in the chain (thigh included) holds its exact ratio at
        // every pose. This is the real regression coverage for "limb length stays rigid."
        val bodyScale = 0.80
        for (angles in listOf(
            PoseAngles(180f, 180f, 5f), PoseAngles(130f, 120f, 35f),
            PoseAngles(95f, 76f, 55f), PoseAngles(75f, 58f, 62f)
        )) {
            val nodes = StickmanKinematics.buildNodes(ratios, angles)
            val ankleCenter = centerline(nodes, NodeId.L_ANKLE, NodeId.R_ANKLE)
            val kneeCenter = centerline(nodes, NodeId.L_KNEE, NodeId.R_KNEE)
            val shankLength = distance(ankleCenter, kneeCenter)
            val thighLength = distance(kneeCenter, node(nodes, NodeId.HIP_CENTER))
            val torsoLength = distance(node(nodes, NodeId.HIP_CENTER), node(nodes, NodeId.NECK_BASE))
            val headLength = distance(node(nodes, NodeId.NECK_BASE), node(nodes, NodeId.HEAD))
            assertEquals("shank at $angles", ratios.shankRatio * bodyScale, shankLength, 1e-4)
            assertEquals("thigh at $angles", ratios.thighRatio * bodyScale, thighLength, 1e-4)
            assertEquals("torso at $angles", ratios.torsoRatio * bodyScale, torsoLength, 1e-4)
            assertEquals("head-neck at $angles", ratios.headNeckRatio * bodyScale, headLength, 1e-4)
        }
    }

    @Test
    fun `hip drops below the knee at BOTTOM, for every archetype`() {
        // The literal ask: "the animation should move till the hip joint is lower than the knee
        // joint." y grows downward in this rig, so "lower" = greater y.
        for ((name, pair) in archetypeBottomAngles) {
            val (archetypeRatios, bottomAngles) = pair
            val nodes = StickmanKinematics.buildNodes(archetypeRatios, bottomAngles)
            val hip = node(nodes, NodeId.HIP_CENTER)
            val knee = node(nodes, NodeId.L_KNEE)
            assertTrue("$name: expected hip.y (${hip.y}) > knee.y (${knee.y}) at BOTTOM", hip.y > knee.y)
        }
    }

    @Test
    fun `hip stays above the knee while standing`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        val hip = node(nodes, NodeId.HIP_CENTER)
        val knee = node(nodes, NodeId.L_KNEE)
        assertTrue(hip.y < knee.y)
    }

    @Test
    fun `shoulder-to-elbow-to-wrist forms one straight line (elbow is the midpoint)`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(130f, 120f, 35f))
        val shoulder = node(nodes, NodeId.L_SHOULDER)
        val elbow = node(nodes, NodeId.L_ELBOW)
        val wrist = node(nodes, NodeId.L_WRIST)
        assertEquals((shoulder.x + wrist.x) / 2f, elbow.x, 1e-4f)
        assertEquals((shoulder.y + wrist.y) / 2f, elbow.y, 1e-4f)
    }

    @Test
    fun `L and R nodes are symmetric around the spine centerline`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 76f, 55f))
        val lHip = node(nodes, NodeId.L_HIP)
        val rHip = node(nodes, NodeId.R_HIP)
        val hipCenter = node(nodes, NodeId.HIP_CENTER)
        assertEquals(hipCenter.x, (lHip.x + rHip.x) / 2f, 1e-4f)
        assertEquals(lHip.y, rHip.y, 1e-4f)
        assertEquals(hipCenter.y, lHip.y, 1e-4f)
    }

    @Test
    fun `toe sits at floor level and forward of the ankle`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        val ankle = node(nodes, NodeId.L_ANKLE)
        val toe = node(nodes, NodeId.L_TOE)
        assertTrue(toe.x > ankle.x)
    }

    @Test
    fun `ankle stays fixed regardless of pose (feet stay planted)`() {
        val reference = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        for (angles in listOf(PoseAngles(130f, 120f, 35f), PoseAngles(95f, 76f, 55f), PoseAngles(75f, 58f, 62f))) {
            val nodes = StickmanKinematics.buildNodes(ratios, angles)
            // Ankle position is a fixed constant regardless of pose (feet don't move during a
            // real squat) — assert it's identical across every phase, not just non-null.
            assertEquals(node(reference, NodeId.L_ANKLE).x, node(nodes, NodeId.L_ANKLE).x, 1e-4f)
            assertEquals(node(reference, NodeId.L_ANKLE).y, node(nodes, NodeId.L_ANKLE).y, 1e-4f)
        }
    }

    @Test
    fun `applyYaw at 0 degrees returns nodes unchanged`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 76f, 55f))
        val rotated = StickmanKinematics.applyYaw(nodes, 0f)
        assertEquals(nodes, rotated)
    }

    @Test
    fun `applyYaw squashes every node's x toward the mid-foot pivot as yaw increases`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 76f, 55f))
        val pivotX = node(nodes, NodeId.L_ANKLE).x.let { l ->
            (l + node(nodes, NodeId.R_ANKLE).x) / 2f
        }
        val rotated45 = StickmanKinematics.applyYaw(nodes, 45f)
        val rotated90 = StickmanKinematics.applyYaw(nodes, 90f)
        for (id in NodeId.entries) {
            val original = node(nodes, id).x
            val distOriginal = Math.abs(original - pivotX)
            if (distOriginal < 1e-4f) continue // already at the pivot, nothing to squash
            val dist45 = Math.abs(node(rotated45, id).x - pivotX)
            val dist90 = Math.abs(node(rotated90, id).x - pivotX)
            assertTrue("$id should squash toward pivot at yaw=45", dist45 < distOriginal + 1e-4f)
            assertTrue("$id should squash further toward pivot at yaw=90 than at yaw=45", dist90 <= dist45 + 1e-4f)
        }
        // At yaw=90, cos(90)=0, so every node collapses exactly onto the pivot.
        for (id in NodeId.entries) {
            assertEquals("$id should sit on the pivot at yaw=90", pivotX, node(rotated90, id).x, 1e-3f)
        }
    }

    @Test
    fun `all nodes stay within sane canvas bounds for every archetype's full angle range`() {
        for ((_, pair) in archetypeBottomAngles) {
            val (archetypeRatios, bottomAngles) = pair
            for (angles in listOf(PoseAngles(180f, 180f, 5f), bottomAngles)) {
                for (n in StickmanKinematics.buildNodes(archetypeRatios, angles)) {
                    assertTrue("${n.id} x=${n.x} out of bounds", n.x in -0.1f..1.1f)
                    assertTrue("${n.id} y=${n.y} out of bounds", n.y in -0.1f..1.1f)
                }
            }
        }
    }
}
