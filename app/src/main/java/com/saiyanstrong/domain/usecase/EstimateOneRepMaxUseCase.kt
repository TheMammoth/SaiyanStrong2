package com.saiyanstrong.domain.usecase

import javax.inject.Inject

class EstimateOneRepMaxUseCase @Inject constructor() {
    fun execute(weightKg: Double, reps: Int): Double =
        if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
}
