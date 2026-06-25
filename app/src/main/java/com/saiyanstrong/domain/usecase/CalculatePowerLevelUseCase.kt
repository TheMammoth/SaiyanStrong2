package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.SetLog
import javax.inject.Inject

class CalculatePowerLevelUseCase @Inject constructor() {

    companion object {
        const val BASE_POWER = 9_001

        fun intensityMultiplier(reps: Int): Double = when {
            reps <= 3 -> 1.5
            reps <= 5 -> 1.25
            reps <= 8 -> 1.0
            else -> 0.85
        }
    }

    fun sessionPowerGained(sets: List<SetLog>): Int =
        sets.sumOf { (it.volumeKg * intensityMultiplier(it.reps)).toInt() }

    fun getPowerLevel(lifetimePowerEarned: Int): PowerLevel {
        val total = BASE_POWER + lifetimePowerEarned
        val stage = SaiyanStage.entries
            .filter { it.threshold <= total }
            .maxByOrNull { it.threshold } ?: SaiyanStage.BASE
        val next = SaiyanStage.entries.firstOrNull { it.threshold > total }
        val progress = next?.let {
            val base = stage.threshold.coerceAtLeast(BASE_POWER)
            ((total - base).toFloat() / (it.threshold - base)).coerceIn(0f, 1f)
        } ?: 1f
        return PowerLevel(total, stage, next?.threshold ?: total, progress)
    }
}
