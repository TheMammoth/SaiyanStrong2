package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.SetLog
import javax.inject.Inject

class LogSetUseCase @Inject constructor() {
    fun execute(setNumber: Int, weightKg: Double, reps: Int, rpe: Float? = null): SetLog =
        SetLog(
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe
        )
}
