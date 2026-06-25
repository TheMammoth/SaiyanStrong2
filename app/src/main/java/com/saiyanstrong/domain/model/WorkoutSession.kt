package com.saiyanstrong.domain.model

data class WorkoutSession(
    val id: Long = 0,
    val dateMs: Long,
    val durationMs: Long,
    val exerciseLogs: List<ExerciseLog>,
    val totalVolumeKg: Double,
    val powerEarned: Int,
    val notes: String = ""
)
