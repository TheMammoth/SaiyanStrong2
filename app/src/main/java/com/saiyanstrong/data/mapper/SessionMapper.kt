package com.saiyanstrong.data.mapper

import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import com.saiyanstrong.data.local.entity.SessionEntity
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.WorkoutSession

fun WorkoutSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    dateMs = dateMs,
    durationMs = durationMs,
    totalVolumeKg = totalVolumeKg,
    powerEarned = powerEarned,
    notes = notes
)

fun SessionEntity.toDomain(exerciseLogs: List<ExerciseLog>): WorkoutSession = WorkoutSession(
    id = id,
    dateMs = dateMs,
    durationMs = durationMs,
    exerciseLogs = exerciseLogs,
    totalVolumeKg = totalVolumeKg,
    powerEarned = powerEarned,
    notes = notes
)

fun ExerciseLog.toEntity(sessionId: Long): ExerciseLogEntity = ExerciseLogEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exercise.id,
    orderIndex = orderIndex
)
