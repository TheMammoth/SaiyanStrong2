package com.saiyanstrong.domain.model

data class WorkoutTemplate(
    val id: Long = 0,
    val name: String,
    val createdMs: Long,
    val exerciseIds: List<Int>,
    val exerciseNames: List<String>,
    val isFromCoach: Boolean = false
)
