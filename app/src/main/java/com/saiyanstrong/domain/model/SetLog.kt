package com.saiyanstrong.domain.model

data class SetLog(
    val id: Long = 0,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Float? = null,
    val isFailure: Boolean = false,
    val volumeKg: Double = weightKg * reps,
    val timestampMs: Long = System.currentTimeMillis()
)
