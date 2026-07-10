package com.saiyanstrong.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RpeChartTest {

    @Test
    fun `5 reps at RPE9 is 83_7 percent of 1RM`() {
        assertEquals(0.837, RpeChart.percentOf1Rm(5, 9f), 0.0001)
    }

    @Test
    fun `5 reps at RPE10 is 86_3 percent of 1RM`() {
        assertEquals(0.863, RpeChart.percentOf1Rm(5, 10f), 0.0001)
    }

    @Test
    fun `1 rep at RPE10 is 100 percent of 1RM`() {
        assertEquals(1.0, RpeChart.percentOf1Rm(1, 10f), 0.0001)
    }

    @Test
    fun `estimateTrue1Rm for 100kg x5 at RPE9 is about 119_5kg`() {
        assertEquals(119.47, RpeChart.estimateTrue1Rm(100.0, 5, 9f), 0.1)
    }

    @Test
    fun `estimated 1RM round-trips back through the same percent`() {
        val estimated = RpeChart.estimateTrue1Rm(100.0, 5, 9f)
        val roundTripped = estimated * RpeChart.percentOf1Rm(5, 9f)
        assertEquals(100.0, roundTripped, 0.01)
    }

    @Test
    fun `reps beyond 12 clamp to the 12-rep row`() {
        assertEquals(RpeChart.percentOf1Rm(12, 8f), RpeChart.percentOf1Rm(20, 8f), 0.0001)
    }

    @Test
    fun `rpe below 6 clamps to the 6 column`() {
        assertEquals(RpeChart.percentOf1Rm(5, 6f), RpeChart.percentOf1Rm(5, 3f), 0.0001)
    }

    @Test
    fun `percent decreases as reps increase for a fixed RPE`() {
        val threeReps = RpeChart.percentOf1Rm(3, 9f)
        val eightReps = RpeChart.percentOf1Rm(8, 9f)
        assert(threeReps > eightReps)
    }

    @Test
    fun `percent decreases as RPE decreases for a fixed rep count`() {
        val rpe10 = RpeChart.percentOf1Rm(5, 10f)
        val rpe7 = RpeChart.percentOf1Rm(5, 7f)
        assert(rpe10 > rpe7)
    }
}
