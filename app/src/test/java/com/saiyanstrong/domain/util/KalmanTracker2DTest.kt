package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanTracker2DTest {

    @Test
    fun `converges to the true velocity on a constant-velocity track`() {
        val tracker = KalmanTracker2D()
        tracker.pixelsPerMeter = 1000.0
        tracker.reset(0.0, 100.0)
        var x = 0.0
        // +10 px every 0.1s = 100 px/s = 0.1 m/s (at 1000 px/m)
        repeat(50) {
            x += 10.0
            tracker.predict(0.1)
            tracker.update(x, 100.0)
        }
        assertEquals(0.1, tracker.smoothedVelocityMps.toDouble(), 0.01)
    }

    @Test
    fun `coasts forward on a missed frame using its momentum`() {
        val tracker = KalmanTracker2D()
        tracker.pixelsPerMeter = 1000.0
        tracker.reset(0.0, 0.0)
        var x = 0.0
        repeat(30) {
            x += 10.0
            tracker.predict(0.1)
            tracker.update(x, 0.0)
        }
        val velocityBefore = tracker.smoothedVelocityMps
        val positionBefore = tracker.smoothedPosition.x
        tracker.predict(0.1) // missed frame: predict only, no update
        assertTrue(
            "position should coast forward, was $positionBefore now ${tracker.smoothedPosition.x}",
            tracker.smoothedPosition.x > positionBefore + 5.0
        )
        assertEquals(velocityBefore.toDouble(), tracker.smoothedVelocityMps.toDouble(), 0.02)
    }

    @Test
    fun `reset zeroes velocity and seeds the position from the given point`() {
        val tracker = KalmanTracker2D()
        tracker.pixelsPerMeter = 1000.0
        // put some motion into it first
        var x = 0.0
        repeat(10) { x += 10.0; tracker.predict(0.1); tracker.update(x, 0.0) }
        assertTrue(tracker.smoothedVelocityMps > 0f)

        tracker.reset(50.0, 60.0)
        assertEquals(0.0, tracker.smoothedVelocityMps.toDouble(), 1e-9)
        assertEquals(50.0, tracker.smoothedPosition.x, 1e-9)
        assertEquals(60.0, tracker.smoothedPosition.y, 1e-9)
    }

    @Test
    fun `predicted acceleration is clamped to the physical bound`() {
        val tracker = KalmanTracker2D() // maxAccelerationMps2 = 15.0
        tracker.pixelsPerMeter = 100.0  // cap in pixel space = 1500 px/s²
        tracker.reset(0.0, 0.0)
        // a wildly accelerating sequence over tiny dt implies accelerations far above 15 m/s²
        val positions = listOf(5.0, 20.0, 50.0, 100.0, 180.0, 300.0)
        for (p in positions) {
            tracker.predict(0.01)
            tracker.update(p, 0.0)
        }
        tracker.predict(0.01) // final predict applies the clamp
        assertTrue(
            "acceleration ${tracker.accelerationMagnitudeMps2} m/s² should be clamped to <= 15",
            tracker.accelerationMagnitudeMps2 <= 15.0 + 1e-6
        )
    }

    @Test
    fun `a stationary marker yields near-zero smoothed velocity`() {
        val tracker = KalmanTracker2D()
        tracker.pixelsPerMeter = 1000.0
        tracker.reset(200.0, 200.0)
        repeat(30) {
            tracker.predict(0.033)
            tracker.update(200.0, 200.0)
        }
        assertEquals(0.0, tracker.smoothedVelocityMps.toDouble(), 0.005)
    }

    @Test
    fun `defaults come from VbtConstants`() {
        // Not a behavioral assertion so much as a guard that the tunables wire through — a
        // tracker built with explicit VbtConstants values behaves identically to the default one.
        val defaultTracker = KalmanTracker2D()
        val explicitTracker = KalmanTracker2D(
            measurementNoiseSigma = VbtConstants.KALMAN_MEASUREMENT_NOISE,
            processNoiseSigma = VbtConstants.KALMAN_PROCESS_NOISE
        )
        defaultTracker.pixelsPerMeter = 1000.0
        explicitTracker.pixelsPerMeter = 1000.0
        defaultTracker.reset(0.0, 0.0)
        explicitTracker.reset(0.0, 0.0)
        var x = 0.0
        repeat(20) {
            x += 10.0
            defaultTracker.predict(0.1); defaultTracker.update(x, 0.0)
            explicitTracker.predict(0.1); explicitTracker.update(x, 0.0)
        }
        assertEquals(
            defaultTracker.smoothedVelocityMps.toDouble(),
            explicitTracker.smoothedVelocityMps.toDouble(),
            1e-9
        )
    }
}
