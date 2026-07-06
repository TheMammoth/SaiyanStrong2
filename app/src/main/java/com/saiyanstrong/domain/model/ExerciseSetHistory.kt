package com.saiyanstrong.domain.model

data class ExerciseSetHistory(
    val dateMs: Long,
    val sessionId: Long,
    val weightKg: Double,
    val reps: Int,
    val isFailure: Boolean
)
