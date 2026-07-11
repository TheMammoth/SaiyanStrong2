package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockOnTrackerTest {

    @Test
    fun `not detected starts and stays SEARCHING`() {
        val tracker = LockOnTracker()
        val update = tracker.update(detected = false, diameterPx = null)
        assertEquals(ReticleState.SEARCHING, update.state)
        assertEquals(0f, update.confidence, 0.0001f)
        assertFalse(update.justLostLock)
    }

    @Test
    fun `detected frames below the lock threshold are ACQUIRING`() {
        val tracker = LockOnTracker()
        repeat(4) {
            val update = tracker.update(detected = true, diameterPx = 20.0)
            assertEquals(ReticleState.ACQUIRING, update.state)
        }
    }

    @Test
    fun `5 consecutive detections reaches LOCKED`() {
        val tracker = LockOnTracker()
        var last: LockOnUpdate? = null
        repeat(5) { last = tracker.update(detected = true, diameterPx = 20.0) }
        assertEquals(ReticleState.LOCKED, last!!.state)
    }

    @Test
    fun `a miss before reaching lock resets the streak back to SEARCHING`() {
        val tracker = LockOnTracker()
        repeat(3) { tracker.update(detected = true, diameterPx = 20.0) } // ACQUIRING
        val afterMiss = tracker.update(detected = false, diameterPx = null)
        assertEquals(ReticleState.SEARCHING, afterMiss.state)
        // and the streak really did reset -- 4 more detections should still be ACQUIRING, not LOCKED
        var last: LockOnUpdate? = null
        repeat(4) { last = tracker.update(detected = true, diameterPx = 20.0) }
        assertEquals(ReticleState.ACQUIRING, last!!.state)
    }

    @Test
    fun `once LOCKED a brief miss streak under 10 frames is tolerated`() {
        val tracker = LockOnTracker()
        repeat(5) { tracker.update(detected = true, diameterPx = 20.0) } // LOCKED
        var last: LockOnUpdate? = null
        repeat(9) { last = tracker.update(detected = false, diameterPx = null) }
        assertEquals(ReticleState.LOCKED, last!!.state)
        assertFalse(last!!.justLostLock)
    }

    @Test
    fun `10 consecutive misses while LOCKED drops back to SEARCHING with justLostLock`() {
        val tracker = LockOnTracker()
        repeat(5) { tracker.update(detected = true, diameterPx = 20.0) } // LOCKED
        repeat(9) { tracker.update(detected = false, diameterPx = null) } // still tolerated
        val tenth = tracker.update(detected = false, diameterPx = null)
        assertEquals(ReticleState.SEARCHING, tenth.state)
        assertTrue(tenth.justLostLock)
    }

    @Test
    fun `justLostLock fires exactly once, not on every subsequent miss`() {
        val tracker = LockOnTracker()
        repeat(5) { tracker.update(detected = true, diameterPx = 20.0) }
        repeat(10) { tracker.update(detected = false, diameterPx = null) } // triggers the loss
        val again = tracker.update(detected = false, diameterPx = null)
        assertFalse(again.justLostLock)
    }

    @Test
    fun `a stable diameter over the window yields high confidence`() {
        val tracker = LockOnTracker()
        var last: LockOnUpdate? = null
        repeat(10) { last = tracker.update(detected = true, diameterPx = 25.0) }
        assertTrue("expected high confidence, got ${last!!.confidence}", last!!.confidence > 0.9f)
    }

    @Test
    fun `a wildly varying diameter yields low confidence`() {
        val tracker = LockOnTracker()
        val sizes = listOf(10.0, 40.0, 5.0, 50.0, 8.0, 45.0, 12.0, 38.0, 6.0, 42.0)
        var last: LockOnUpdate? = null
        for (d in sizes) last = tracker.update(detected = true, diameterPx = d)
        assertTrue("expected low confidence, got ${last!!.confidence}", last!!.confidence < 0.5f)
    }

    @Test
    fun `reset clears all state back to a fresh SEARCHING start`() {
        val tracker = LockOnTracker()
        repeat(5) { tracker.update(detected = true, diameterPx = 20.0) } // LOCKED
        tracker.reset()
        val update = tracker.update(detected = true, diameterPx = 20.0)
        assertEquals(ReticleState.ACQUIRING, update.state) // back to streak=1, not still LOCKED
    }
}
