package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.model.VelocityZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeBarPathUseCaseTest {

    private val useCase = AnalyzeBarPathUseCase()

    // Bar moves up 0.4m over 0.5s at constant velocity — 0.8 m/s, squarely in Speed-Strength.
    // pixelsPerMeter = 1000. yPx decreases as the bar moves up (image Y grows downward).
    private val constantVelocitySamples = listOf(
        BarPathSample(0, 300.0, 500.0),
        BarPathSample(100, 300.0, 420.0),
        BarPathSample(200, 300.0, 340.0),
        BarPathSample(300, 300.0, 260.0),
        BarPathSample(400, 300.0, 180.0),
        BarPathSample(500, 300.0, 100.0)
    )

    @Test
    fun `constant velocity rep computes correct mean concentric velocity and zone`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 500)
        assertEquals(0.8, result.meanConcentricVelocityMs, 0.001)
        assertEquals(VelocityZone.SPEED_STRENGTH, result.velocityZone)
    }

    @Test
    fun `constant velocity rep has matching peak velocity since there is no sticking point`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 500)
        assertEquals(0.8, result.peakVelocityMs, 0.001)
    }

    @Test
    fun `power at constant velocity is nonzero — supporting the load against gravity still costs power`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 500)
        val expectedPower = 100.0 * 9.81 * 0.8
        assertEquals(expectedPower, result.meanPowerWatts, 5.0)
        assertEquals(expectedPower, result.peakPowerWatts, 5.0)
    }

    @Test
    fun `range of motion matches the hand-computed vertical travel`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 500)
        assertEquals(40.0, result.rangeOfMotionCm, 0.01) // (500-100)px / 1000 * 100
    }

    @Test
    fun `bar path deviation reflects horizontal drift, zero when the bar travels straight`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 500)
        assertEquals(0.0, result.barPathDeviationCm, 0.001)
    }

    @Test
    fun `bar path deviation is nonzero when the bar drifts sideways`() {
        val driftingSamples = listOf(
            BarPathSample(0, 300.0, 500.0),
            BarPathSample(100, 310.0, 420.0),
            BarPathSample(200, 290.0, 340.0),
            BarPathSample(300, 300.0, 260.0)
        )
        val result = useCase.execute(driftingSamples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 300)
        assertEquals(2.0, result.barPathDeviationCm, 0.01) // 20px drift / 1000 * 100
    }

    @Test
    fun `peak velocity reflects the fastest instant, not the average, through a sticking point`() {
        // fast, slow, slow, fast — a real sticking-point profile. Displacements in meters per
        // 100ms interval: 0.10, 0.02, 0.02, 0.10 -> instantaneous velocities 1.0, 0.2, 0.2, 1.0
        val samples = listOf(
            BarPathSample(0, 0.0, 1000.0),
            BarPathSample(100, 0.0, 900.0),
            BarPathSample(200, 0.0, 880.0),
            BarPathSample(300, 0.0, 860.0),
            BarPathSample(400, 0.0, 760.0)
        )
        val result = useCase.execute(samples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 400)
        assertEquals(1.0, result.peakVelocityMs, 0.001)
        assertTrue(
            "mean concentric velocity (${result.meanConcentricVelocityMs}) should be well below the peak (${result.peakVelocityMs})",
            result.meanConcentricVelocityMs < result.peakVelocityMs - 0.3
        )
    }

    @Test
    fun `image Y decreasing means the bar is moving up — positive velocity`() {
        val samples = listOf(BarPathSample(0, 0.0, 500.0), BarPathSample(100, 0.0, 400.0))
        val result = useCase.execute(samples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 100)
        assertTrue(result.meanConcentricVelocityMs > 0.0)
    }

    @Test
    fun `image Y increasing means the bar is moving down — negative velocity`() {
        val samples = listOf(BarPathSample(0, 0.0, 400.0), BarPathSample(100, 0.0, 500.0))
        val result = useCase.execute(samples, pixelsPerMeter = 1000.0, massKg = 100.0, 0, 100)
        assertTrue(result.meanConcentricVelocityMs < 0.0)
    }

    @Test
    fun `fewer than two samples in the window returns a safe zeroed result`() {
        val result = useCase.execute(
            listOf(BarPathSample(0, 0.0, 500.0)),
            pixelsPerMeter = 1000.0, massKg = 100.0, concentricStartMs = 0, concentricEndMs = 100
        )
        assertEquals(0.0, result.meanConcentricVelocityMs, 0.0001)
        assertEquals(VelocityZone.ABSOLUTE_STRENGTH, result.velocityZone)
    }

    @Test
    fun `samples outside the concentric window are excluded`() {
        val result = useCase.execute(constantVelocitySamples, pixelsPerMeter = 1000.0, massKg = 100.0, concentricStartMs = 0, concentricEndMs = 200)
        // Only the first 3 samples (0, 100, 200ms) fall in the window — still constant velocity, same answer.
        assertEquals(0.8, result.meanConcentricVelocityMs, 0.001)
    }
}
