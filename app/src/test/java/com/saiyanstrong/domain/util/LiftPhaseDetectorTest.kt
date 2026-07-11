package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftPhaseDetectorTest {

    private fun p(x: Double, y: Double = 100.0) = Point2D(x, y)

    /** Feeds a stationary settling window and returns the detector parked in READY. */
    private fun settledDetector(): LiftPhaseDetector {
        val d = LiftPhaseDetector().apply { pixelsPerMeter = 1000.0 }
        d.startRep()
        d.update(p(100.0), 0L)
        d.update(p(101.0), 400L)
        d.update(p(100.0, 101.0), 800L)
        d.update(p(100.0), 1200L)
        val ready = d.update(p(100.0), 1600L) // crosses the 1500ms settling window
        assertEquals(LiftPhase.READY, ready.phase)
        return d
    }

    @Test
    fun `idle ignores centroids and records nothing`() {
        val d = LiftPhaseDetector()
        val u = d.update(p(300.0, 300.0), 0L)
        assertEquals(LiftPhase.IDLE, u.phase)
        assertFalse(u.shouldRecordVelocity)
        assertNull(u.filteredCentroid)
    }

    @Test
    fun `startRep enters settling and does not record velocity`() {
        val d = LiftPhaseDetector()
        d.startRep()
        val u = d.update(p(100.0), 0L)
        assertEquals(LiftPhase.SETTLING, u.phase)
        assertFalse(u.shouldRecordVelocity)
    }

    @Test
    fun `settling transitions to ready after the settling window`() {
        val d = settledDetector()
        assertEquals(LiftPhase.READY, d.phase)
    }

    @Test
    fun `a stationary bar in ready clamps the filtered centroid to zero`() {
        val d = settledDetector()
        val u = d.update(p(100.0), 1650L) // essentially on the baseline
        assertEquals(LiftPhase.READY, u.phase)
        assertFalse(u.shouldRecordVelocity)
        assertEquals(Point2D(0.0, 0.0), u.filteredCentroid)
    }

    @Test
    fun `sustained consistent movement transitions ready to moving`() {
        val d = settledDetector()
        var t = 1700L
        var last = LiftPhaseUpdate(LiftPhase.READY, false, null, false)
        // 8 rightward frames, each well past the baseline and moving the same direction.
        for (x in listOf(120.0, 124.0, 128.0, 132.0, 136.0, 140.0, 144.0, 148.0)) {
            last = d.update(p(x), t); t += 50L
        }
        assertEquals(LiftPhase.MOVING, last.phase)
        assertTrue(last.shouldRecordVelocity)
    }

    @Test
    fun `back-and-forth jitter past the threshold does not trigger moving`() {
        val d = settledDetector()
        var t = 1700L
        repeat(20) {
            val x = if (it % 2 == 0) 120.0 else 132.0 // reverses direction each frame
            d.update(p(x), t); t += 50L
        }
        assertEquals(LiftPhase.READY, d.phase)
    }

    @Test
    fun `moving completes only after real ROM then sustained low velocity`() {
        val d = settledDetector()
        var t = 1700L
        // drive into MOVING
        for (x in listOf(120.0, 124.0, 128.0, 132.0, 136.0, 140.0, 144.0, 148.0)) {
            d.update(p(x), t); t += 50L
        }
        assertEquals(LiftPhase.MOVING, d.phase)
        // move well past MIN_ROM (148 -> 260 px = 0.112 m at 1000 px/m)
        d.update(p(260.0), t); t += 16L
        // hold still for COMPLETION_FRAMES
        var completedUpdate: LiftPhaseUpdate? = null
        repeat(LiftPhaseDetector.COMPLETION_FRAMES) {
            completedUpdate = d.update(p(260.0), t); t += 16L
        }
        assertEquals(LiftPhase.COMPLETE, d.phase)
        assertTrue("repJustCompleted should fire on the completing frame", completedUpdate!!.repJustCompleted)
    }

    @Test
    fun `low velocity without enough ROM does not complete the rep`() {
        val d = settledDetector()
        var t = 1700L
        for (x in listOf(120.0, 124.0, 128.0, 132.0, 136.0, 140.0, 144.0, 148.0)) {
            d.update(p(x), t); t += 50L
        }
        // barely moved past onset (148 is only ~48px from the 100 baseline -> under MIN_ROM's
        // 50px, and moveStart is 148 so displacement-from-start is ~0); hold still.
        repeat(LiftPhaseDetector.COMPLETION_FRAMES + 5) { d.update(p(148.0), t); t += 16L }
        assertEquals(LiftPhase.MOVING, d.phase) // never reached COMPLETE
    }

    @Test
    fun `complete returns to ready after the rerack delay`() {
        val d = settledDetector()
        var t = 1700L
        for (x in listOf(120.0, 124.0, 128.0, 132.0, 136.0, 140.0, 144.0, 148.0)) {
            d.update(p(x), t); t += 50L
        }
        d.update(p(260.0), t); t += 16L
        repeat(LiftPhaseDetector.COMPLETION_FRAMES) { d.update(p(260.0), t); t += 16L }
        assertEquals(LiftPhase.COMPLETE, d.phase)
        val completeAt = t
        d.update(p(260.0), completeAt + 100L)
        val backToReady = d.update(p(260.0), completeAt + LiftPhaseDetector.COMPLETE_DURATION_MS + 50L)
        assertEquals(LiftPhase.READY, backToReady.phase)
    }

    @Test
    fun `computeBaseline returns the mean and a nonnegative variance`() {
        val (mean, variance) = LiftPhaseDetector.computeBaseline(
            listOf(Point2D(10.0, 20.0), Point2D(20.0, 20.0), Point2D(15.0, 30.0))
        )
        assertEquals(15.0, mean.x, 0.001)
        assertEquals(23.333, mean.y, 0.01)
        assertTrue(variance >= 0.0)
    }
}
