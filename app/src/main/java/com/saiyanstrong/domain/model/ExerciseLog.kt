package com.saiyanstrong.domain.model

data class ExerciseLog(
    val id: Long = 0,
    val exercise: Exercise,
    val sets: List<SetLog>,
    val orderIndex: Int
)
