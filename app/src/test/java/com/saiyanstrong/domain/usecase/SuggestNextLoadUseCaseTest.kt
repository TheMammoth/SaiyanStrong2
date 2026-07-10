package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.LoadSuggestion
import com.saiyanstrong.domain.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestNextLoadUseCaseTest {

    private val useCase = SuggestNextLoadUseCase()

    private fun set(weightKg: Double, reps: Int, rpe: Float?) =
        SetLog(setNumber = 1, weightKg = weightKg, reps = reps, rpe = rpe)

    @Test
    fun `no recorded RPE means no suggestion`() {
        assertNull(useCase.execute(set(100.0, 5, null), stepKg = 2.5))
    }

    @Test
    fun `100kg x5 at RPE9 suggests one more rep, not more weight`() {
        val suggestion = useCase.execute(set(100.0, 5, 9f), stepKg = 2.5)
        assertEquals(LoadSuggestion.MoreReps(6), suggestion)
    }

    @Test
    fun `high rep set at RPE9 suggests more weight instead of more reps`() {
        val suggestion = useCase.execute(set(100.0, 8, 9f), stepKg = 2.5)
        assertTrue(suggestion is LoadSuggestion.MoreWeight)
        val target = (suggestion as LoadSuggestion.MoreWeight).targetWeightKg
        assertTrue("expected $target > 100.0", target > 100.0)
    }

    @Test
    fun `low RPE suggests more weight at the same reps`() {
        val suggestion = useCase.execute(set(100.0, 5, 7f), stepKg = 2.5)
        assertTrue(suggestion is LoadSuggestion.MoreWeight)
        val target = (suggestion as LoadSuggestion.MoreWeight).targetWeightKg
        assertTrue("expected $target > 100.0", target > 100.0)
        assertEquals(0.0, target % 2.5, 0.0001) // rounds to the given step
    }

    @Test
    fun `RPE9_5 suggests holding`() {
        assertEquals(LoadSuggestion.Hold, useCase.execute(set(100.0, 5, 9.5f), stepKg = 2.5))
    }

    @Test
    fun `RPE10 suggests easing off`() {
        assertEquals(LoadSuggestion.EaseOff, useCase.execute(set(100.0, 5, 10f), stepKg = 2.5))
    }

    @Test
    fun `suggested weight always exceeds the previous weight`() {
        val suggestion = useCase.execute(set(100.0, 8, 6f), stepKg = 2.5) as LoadSuggestion.MoreWeight
        assertTrue(suggestion.targetWeightKg > 100.0)
    }
}
