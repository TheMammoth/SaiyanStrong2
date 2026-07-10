package com.saiyanstrong.presentation.screens.exercises

import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.ExerciseSetHistory
import com.saiyanstrong.domain.model.VelocityZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailViewModelTest {

    private fun analysis(mcv: Double) = BarPathAnalysis(
        peakVelocityMs = mcv, meanConcentricVelocityMs = mcv, peakPowerWatts = 0.0,
        meanPowerWatts = 0.0, rangeOfMotionCm = 0.0, barPathDeviationCm = 0.0,
        velocityZone = VelocityZone.STRENGTH_SPEED
    )

    private fun set(setLogId: Long, sessionId: Long, dateMs: Long, weightKg: Double) =
        ExerciseSetHistory(setLogId, dateMs, sessionId, weightKg, reps = 5, isFailure = false)

    @Test
    fun `sessions with no tracked sets contribute no chart point`() {
        val bySession = mapOf(1L to listOf(set(1, 1, 1000, 100.0), set(2, 1, 1000, 105.0)))
        val chart = buildVelocityChart(bySession, emptyMap())
        assertTrue(chart.isEmpty())
    }

    @Test
    fun `the heaviest tracked set of a session contributes its velocity`() {
        val bySession = mapOf(
            1L to listOf(set(1, 1, 1000, 100.0), set(2, 1, 1000, 105.0))
        )
        val barPathBySetId = mapOf(1L to analysis(0.8), 2L to analysis(0.5))
        val chart = buildVelocityChart(bySession, barPathBySetId)
        assertEquals(1, chart.size)
        // set 2 (105kg) is heavier than set 1 (100kg) -- its velocity (0.5) should win, not set 1's
        assertEquals(0.5, chart.single().value, 0.0001)
    }

    @Test
    fun `only the tracked set counts even if an untracked set is heavier`() {
        val bySession = mapOf(
            1L to listOf(set(1, 1, 1000, 100.0), set(2, 1, 1000, 150.0)) // set 2 heavier, untracked
        )
        val barPathBySetId = mapOf(1L to analysis(0.9))
        val chart = buildVelocityChart(bySession, barPathBySetId)
        assertEquals(0.9, chart.single().value, 0.0001)
    }

    @Test
    fun `chart is sorted chronologically across sessions`() {
        val bySession = mapOf(
            2L to listOf(set(20, 2, 2000, 100.0)),
            1L to listOf(set(10, 1, 1000, 100.0))
        )
        val barPathBySetId = mapOf(10L to analysis(0.7), 20L to analysis(0.6))
        val chart = buildVelocityChart(bySession, barPathBySetId)
        assertEquals(listOf(1000L, 2000L), chart.map { it.dateMs })
    }

    @Test
    fun `no sessions produce an empty chart`() {
        assertTrue(buildVelocityChart(emptyMap(), emptyMap()).isEmpty())
    }
}
