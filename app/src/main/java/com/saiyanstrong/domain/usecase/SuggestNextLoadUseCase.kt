package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.LoadSuggestion
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.util.RpeChart
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Autoregulation hint from a previously logged (weight, reps, RPE) triple — a real coach's read
 * of an RPE log, not a fixed percentage-progression scheme. Returns null when the previous set
 * has no recorded RPE (nothing to base a suggestion on).
 */
class SuggestNextLoadUseCase @Inject constructor() {

    fun execute(previousSet: SetLog, stepKg: Double): LoadSuggestion? {
        val rpe = previousSet.rpe ?: return null
        val reps = previousSet.reps
        val weightKg = previousSet.weightKg

        return when {
            rpe <= 8.0f -> LoadSuggestion.MoreWeight(
                targetWeight(weightKg, reps, rpe, targetRpe = 9.0f, stepKg = stepKg)
            )
            rpe <= 9.0f -> if (reps <= 6) {
                LoadSuggestion.MoreReps(reps + 1)
            } else {
                LoadSuggestion.MoreWeight(
                    targetWeight(weightKg, reps, rpe, targetRpe = 9.5f, stepKg = stepKg)
                )
            }
            rpe <= 9.5f -> LoadSuggestion.Hold
            else -> LoadSuggestion.EaseOff
        }
    }

    private fun targetWeight(weightKg: Double, reps: Int, rpe: Float, targetRpe: Float, stepKg: Double): Double {
        val estimated1Rm = RpeChart.estimateTrue1Rm(weightKg, reps, rpe)
        val target = estimated1Rm * RpeChart.percentOf1Rm(reps, targetRpe)
        val rounded = (target / stepKg).roundToInt() * stepKg
        // Targeting a harder RPE than what was actually logged is always a heavier weight on a
        // monotonic chart — this floor just guards against a rounding edge landing on the same
        // weight as before, which would be a useless "suggestion."
        return rounded.coerceAtLeast(weightKg + stepKg)
    }
}
