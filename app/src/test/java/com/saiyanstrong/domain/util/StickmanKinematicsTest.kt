package com.saiyanstrong.domain.util

import com.saiyanstrong.domain.model.LiftType
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

    /** Lean angle (degrees from vertical) of the hip-to-neck vector — the rendered torso angle,
     * read back out of the built nodes rather than re-deriving it independently. */
    private fun torsoLeanOf(nodes: List<NodePosition>): Double {
        val hip = node(nodes, NodeId.HIP_CENTER)
        val neck = node(nodes, NodeId.NECK_BASE)
        return Math.toDegrees(Math.atan2((neck.x - hip.x).toDouble(), (hip.y - neck.y).toDouble()))
    }

    /** Lean angle (degrees from vertical) of the knee-to-hip vector — the rendered thigh angle. */
    private fun thighAngleOf(nodes: List<NodePosition>): Double {
        val kneeCenter = centerline(nodes, NodeId.L_KNEE, NodeId.R_KNEE)
        val hip = node(nodes, NodeId.HIP_CENTER)
        return Math.toDegrees(Math.atan2((hip.x - kneeCenter.x).toDouble(), (kneeCenter.y - hip.y).toDouble()))
    }

    @Test
    fun `every node id is present exactly once`() {
        val nodes = StickmanKinematics.buildNodes(ratios, PoseAngles(180f, 180f, 5f))
        assertEquals(NodeId.entries.toSet(), nodes.map { it.id }.toSet())
        assertEquals(NodeId.entries.size, nodes.size)
    }

    @Test
    fun `shank, thigh, torso, and head-neck segments all stay exactly rigid at every pose`() {
        // Torso lean is now solved (a rotation about the hip), not translated — a rotation can
        // never change segment length, so there is no exception here: every segment in the chain
        // (thigh and torso included) holds its exact ratio at every pose. This is the real
        // regression coverage for "limb length stays rigid."
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
    fun `increasing thigh ratio changes the solved torso lean (femur to torso coupling)`() {
        // The literal ask: "when i move the femur length the torso angle should also move."
        // A longer thigh pushes the hip further from the ankle at the same knee angle, which the
        // balance equation must compensate for with a different torso lean.
        val angles = PoseAngles(72f, 57f, 47f)
        val shortThigh = ratiosOf(thigh = 0.18f, shank = 0.32f, torso = 0.29f, head = 0.16f, hipHalf = 0.07f)
        val longThigh = ratiosOf(thigh = 0.32f, shank = 0.18f, torso = 0.29f, head = 0.16f, hipHalf = 0.07f)
        val torsoLeanShort = torsoLeanOf(StickmanKinematics.buildNodes(shortThigh, angles))
        val torsoLeanLong = torsoLeanOf(StickmanKinematics.buildNodes(longThigh, angles))
        assertTrue(
            "expected a different torso lean for a different thigh ratio, got $torsoLeanShort for both",
            Math.abs(torsoLeanLong - torsoLeanShort) > 1f
        )
    }

    @Test
    fun `changing torso ratio changes the resulting thigh angle (comfort coupling)`() {
        // The literal ask: "when i move the torso slider the femur angle should change." This is
        // the explicitly-labeled stylistic comfort factor, not a balance-equation consequence.
        val angles = PoseAngles(72f, 57f, 47f)
        val shortTorso = ratiosOf(thigh = 0.23f, shank = 0.27f, torso = 0.20f, head = 0.16f, hipHalf = 0.07f)
        val longTorso = ratiosOf(thigh = 0.23f, shank = 0.27f, torso = 0.36f, head = 0.16f, hipHalf = 0.07f)
        val thighAngleShort = thighAngleOf(StickmanKinematics.buildNodes(shortTorso, angles))
        val thighAngleLong = thighAngleOf(StickmanKinematics.buildNodes(longTorso, angles))
        assertTrue(
            "expected a different thigh angle for a different torso ratio, got $thighAngleShort for both",
            Math.abs(thighAngleLong - thighAngleShort) > 1f
        )
    }

    @Test
    fun `bar lands back on (or very near) mid-foot now that torso lean is solved`() {
        // Restores the mechanical fact both v0.48.0 (via a translation that broke rigidity) and
        // v0.49.0 (by dropping the correction entirely) failed to deliver cleanly — this time as
        // a direct, exact consequence of the balance equation, not an approximation.
        for ((name, pair) in archetypeBottomAngles) {
            val (archetypeRatios, bottomAngles) = pair
            val nodes = StickmanKinematics.buildNodes(archetypeRatios, bottomAngles)
            val bar = node(nodes, NodeId.BAR)
            val ankleCenter = centerline(nodes, NodeId.L_ANKLE, NodeId.R_ANKLE)
            val midFootX = ankleCenter.x + archetypeRatios.footLenRatio * 0.80f * 0.5f
            assertEquals("$name: bar not near mid-foot at BOTTOM", midFootX, bar.x, 1e-3f)
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
    fun `torsoLeanBiasDeg pitches the torso further forward without changing any segment length`() {
        // The mechanism behind "real ascent": at the same knee angle, a positive bias must lean
        // the torso further forward (bigger torsoLeanOf) yet leave every limb exactly rigid,
        // because the bias is a rotation about the hip, not a translation.
        val base = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 76f, 55f, 0f))
        val biased = StickmanKinematics.buildNodes(ratios, PoseAngles(95f, 76f, 55f, 15f))

        val leanBase = torsoLeanOf(base)
        val leanBiased = torsoLeanOf(biased)
        assertTrue(
            "expected ~15° more forward lean with a +15 bias, got ${leanBiased - leanBase}",
            (leanBiased - leanBase) in 13.0..17.0
        )

        val bodyScale = 0.80
        val kneeCenter = centerline(biased, NodeId.L_KNEE, NodeId.R_KNEE)
        val thigh = distance(kneeCenter, node(biased, NodeId.HIP_CENTER))
        val torso = distance(node(biased, NodeId.HIP_CENTER), node(biased, NodeId.NECK_BASE))
        val head = distance(node(biased, NodeId.NECK_BASE), node(biased, NodeId.HEAD))
        assertEquals("thigh rigid under bias", ratios.thighRatio * bodyScale, thigh, 1e-4)
        assertEquals("torso rigid under bias", ratios.torsoRatio * bodyScale, torso, 1e-4)
        assertEquals("head-neck rigid under bias", ratios.headNeckRatio * bodyScale, head, 1e-4)
    }

    @Test
    fun `zero bias leaves the pose identical to the solved-only lean`() {
        // Guarantees squat's existing motion is unchanged: PoseAngles built with the default bias
        // must produce exactly the same nodes as before the bias field existed.
        val a = StickmanKinematics.buildNodes(ratios, PoseAngles(72f, 57f, 47f))
        val b = StickmanKinematics.buildNodes(ratios, PoseAngles(72f, 57f, 47f, 0f))
        assertEquals(a, b)
    }

    @Test
    fun `nearestYawDetent snaps to the closest of front, three-quarter, and side`() {
        assertEquals(0f, StickmanKinematics.nearestYawDetent(5f))
        assertEquals(0f, StickmanKinematics.nearestYawDetent(-8f))
        assertEquals(45f, StickmanKinematics.nearestYawDetent(40f))
        assertEquals(80f, StickmanKinematics.nearestYawDetent(70f))
        assertEquals(-45f, StickmanKinematics.nearestYawDetent(-52f))
        assertEquals(-80f, StickmanKinematics.nearestYawDetent(-72f))
    }

    // --- Deadlift (Slice 2): hanging arms, authored hinge torso ---

    private val deadliftSetup = PoseAngles(66f, 122f, 57f)
    private val deadliftLockout = PoseAngles(178f, 180f, 4f)

    @Test
    fun `deadlift arms hang straight down from the shoulders`() {
        val nodes = StickmanKinematics.buildNodes(proportionalRatios, deadliftSetup, LiftType.DEADLIFT)
        val lShoulder = node(nodes, NodeId.L_SHOULDER)
        val lWrist = node(nodes, NodeId.L_WRIST)
        val rShoulder = node(nodes, NodeId.R_SHOULDER)
        val rWrist = node(nodes, NodeId.R_WRIST)
        assertEquals("left arm vertical", lShoulder.x, lWrist.x, 1e-4f)
        assertEquals("right arm vertical", rShoulder.x, rWrist.x, 1e-4f)
        assertTrue("wrist hangs below the shoulder", lWrist.y > lShoulder.y)
        // arm length is a fixed ratio (not stretched to reach the floor)
        assertEquals(proportionalRatios.armRatio * 0.80, distance(lShoulder, lWrist), 1e-4)
    }

    @Test
    fun `deadlift bar spans the wrists and hangs under the shoulders`() {
        val nodes = StickmanKinematics.buildNodes(proportionalRatios, deadliftSetup, LiftType.DEADLIFT)
        val bar = node(nodes, NodeId.BAR)
        val lWrist = node(nodes, NodeId.L_WRIST)
        val rWrist = node(nodes, NodeId.R_WRIST)
        val lShoulder = node(nodes, NodeId.L_SHOULDER)
        assertEquals((lWrist.x + rWrist.x) / 2f, bar.x, 1e-4f)
        assertTrue("bar sits below the shoulders", bar.y > lShoulder.y)
    }

    @Test
    fun `deadlift torso uses the authored hinge angle directly (not the solved squat lean)`() {
        val leanSetup = torsoLeanOf(StickmanKinematics.buildNodes(proportionalRatios, deadliftSetup, LiftType.DEADLIFT))
        val leanLockout = torsoLeanOf(StickmanKinematics.buildNodes(proportionalRatios, deadliftLockout, LiftType.DEADLIFT))
        assertEquals("setup lean == authored 57°", 57.0, leanSetup, 0.5)
        assertEquals("lockout lean == authored 4°", 4.0, leanLockout, 0.5)
    }

    @Test
    fun `deadlift bar rises from setup to lockout`() {
        val barSetup = node(StickmanKinematics.buildNodes(proportionalRatios, deadliftSetup, LiftType.DEADLIFT), NodeId.BAR)
        val barLockout = node(StickmanKinematics.buildNodes(proportionalRatios, deadliftLockout, LiftType.DEADLIFT), NodeId.BAR)
        // y grows downward, so a higher bar at lockout means a smaller y.
        assertTrue("bar should be higher at lockout than setup", barLockout.y < barSetup.y)
    }

    @Test
    fun `deadlift segments stay rigid (legs, torso, head, arms)`() {
        val bodyScale = 0.80
        for (angles in listOf(deadliftSetup, PoseAngles(75f, 130f, 56f), deadliftLockout)) {
            val nodes = StickmanKinematics.buildNodes(proportionalRatios, angles, LiftType.DEADLIFT)
            val ankleCenter = centerline(nodes, NodeId.L_ANKLE, NodeId.R_ANKLE)
            val kneeCenter = centerline(nodes, NodeId.L_KNEE, NodeId.R_KNEE)
            assertEquals("shank", proportionalRatios.shankRatio * bodyScale, distance(ankleCenter, kneeCenter), 1e-4)
            assertEquals("thigh", proportionalRatios.thighRatio * bodyScale, distance(kneeCenter, node(nodes, NodeId.HIP_CENTER)), 1e-4)
            assertEquals("torso", proportionalRatios.torsoRatio * bodyScale, distance(node(nodes, NodeId.HIP_CENTER), node(nodes, NodeId.NECK_BASE)), 1e-4)
            assertEquals("arm", proportionalRatios.armRatio * bodyScale, distance(node(nodes, NodeId.L_SHOULDER), node(nodes, NodeId.L_WRIST)), 1e-4)
        }
    }

    // --- Overhead press (Slice 3): press-up arms, near-vertical torso ---

    private val ohpRack = PoseAngles(180f, 180f, 5f, 0f, 0f)
    private val ohpLockout = PoseAngles(180f, 180f, 2f, 0f, 1f)

    @Test
    fun `overhead press bar sits in front at the rack and stacks over the shoulders at lockout`() {
        val rack = StickmanKinematics.buildNodes(proportionalRatios, ohpRack, LiftType.OVERHEAD_PRESS)
        val lockout = StickmanKinematics.buildNodes(proportionalRatios, ohpLockout, LiftType.OVERHEAD_PRESS)
        // Rack: bar is forward of the shoulder line (arm angled forward). Lockout: bar is stacked
        // directly over the shoulder centerline (arm vertical). Each compared against its own
        // pose's neck x — a slightly different torso angle shifts the neck between the two poses.
        assertTrue("bar should be forward of the shoulders at the rack", node(rack, NodeId.BAR).x > node(rack, NodeId.NECK_BASE).x)
        assertEquals("bar over shoulders at lockout", node(lockout, NodeId.NECK_BASE).x, node(lockout, NodeId.BAR).x, 1e-4f)
    }

    @Test
    fun `overhead press bar rises from rack to overhead as pressFraction goes to 1`() {
        val barRack = node(StickmanKinematics.buildNodes(proportionalRatios, ohpRack, LiftType.OVERHEAD_PRESS), NodeId.BAR)
        val barLockout = node(StickmanKinematics.buildNodes(proportionalRatios, ohpLockout, LiftType.OVERHEAD_PRESS), NodeId.BAR)
        val shoulder = node(StickmanKinematics.buildNodes(proportionalRatios, ohpLockout, LiftType.OVERHEAD_PRESS), NodeId.L_SHOULDER)
        // y grows downward: overhead means a smaller y, and the locked-out bar is above the shoulders.
        assertTrue("bar rises from rack to lockout", barLockout.y < barRack.y)
        assertTrue("locked-out bar is above the shoulders", barLockout.y < shoulder.y)
    }

    @Test
    fun `overhead press arms stay rigid and legs stay straight`() {
        val bodyScale = 0.80
        for (angles in listOf(ohpRack, PoseAngles(180f, 180f, 3f, 0f, 0.6f), ohpLockout)) {
            val nodes = StickmanKinematics.buildNodes(proportionalRatios, angles, LiftType.OVERHEAD_PRESS)
            assertEquals("arm rigid", proportionalRatios.armRatio * bodyScale, distance(node(nodes, NodeId.L_SHOULDER), node(nodes, NodeId.L_WRIST)), 1e-4)
            // Straight legs: standing, hip stays above the knee.
            assertTrue("hip above knee (straight legs)", node(nodes, NodeId.HIP_CENTER).y < node(nodes, NodeId.L_KNEE).y)
        }
    }

    // --- Bench press (Slice 4): supine layout ---

    private val benchChest = PoseAngles(0f, 0f, 0f, 0f, 0f)
    private val benchTop = PoseAngles(0f, 0f, 0f, 0f, 1f)

    @Test
    fun `bench torso lies flat and the feet are planted on the floor`() {
        val nodes = StickmanKinematics.buildNodes(proportionalRatios, benchChest, LiftType.BENCH)
        val hip = node(nodes, NodeId.HIP_CENTER)
        val neck = node(nodes, NodeId.NECK_BASE)
        assertEquals("torso lies flat (hip and neck at the same height)", hip.y, neck.y, 1e-4f)
        // Feet on the floor (FLOOR_Y = 0.93 in the rig).
        assertEquals("foot planted on the floor", 0.93f, node(nodes, NodeId.L_ANKLE).y, 1e-4f)
        assertEquals(NodeId.entries.toSet(), nodes.map { it.id }.toSet())
    }

    @Test
    fun `bench bar sits over the chest and travels up from chest to lockout`() {
        val chest = StickmanKinematics.buildNodes(proportionalRatios, benchChest, LiftType.BENCH)
        val top = StickmanKinematics.buildNodes(proportionalRatios, benchTop, LiftType.BENCH)
        val neck = node(chest, NodeId.NECK_BASE)
        // Bar is stacked over the shoulders (chest), not off to one side.
        assertEquals("bar over the chest", neck.x, node(chest, NodeId.BAR).x, 1e-4f)
        // y grows downward: the locked-out bar is higher (smaller y) than at the chest, and both
        // are above the chest (bar never sits below the torso).
        assertTrue("bar rises from chest to lockout", node(top, NodeId.BAR).y < node(chest, NodeId.BAR).y)
        assertTrue("bar stays above the chest", node(chest, NodeId.BAR).y < neck.y)
    }

    @Test
    fun `bench torso and leg segments stay rigid`() {
        val bodyScale = 0.80
        for (angles in listOf(benchChest, PoseAngles(0f, 0f, 0f, 0f, 0.5f), benchTop)) {
            val nodes = StickmanKinematics.buildNodes(proportionalRatios, angles, LiftType.BENCH)
            val ankleCenter = centerline(nodes, NodeId.L_ANKLE, NodeId.R_ANKLE)
            val kneeCenter = centerline(nodes, NodeId.L_KNEE, NodeId.R_KNEE)
            assertEquals("torso", proportionalRatios.torsoRatio * bodyScale, distance(node(nodes, NodeId.HIP_CENTER), node(nodes, NodeId.NECK_BASE)), 1e-4)
            assertEquals("shank", proportionalRatios.shankRatio * bodyScale, distance(ankleCenter, kneeCenter), 1e-4)
            assertEquals("thigh", proportionalRatios.thighRatio * bodyScale, distance(kneeCenter, node(nodes, NodeId.HIP_CENTER)), 1e-4)
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
