package com.saiyanstrong.data.mapper

import com.saiyanstrong.data.local.entity.SetLogEntity
import com.saiyanstrong.domain.model.SetLog

fun SetLogEntity.toDomain(): SetLog = SetLog(
    id = id,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isFailure = isFailure,
    volumeKg = volumeKg,
    timestampMs = timestampMs
)

fun SetLog.toEntity(exerciseLogId: Long): SetLogEntity = SetLogEntity(
    id = id,
    exerciseLogId = exerciseLogId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    rpe = rpe,
    isFailure = isFailure,
    volumeKg = volumeKg,
    timestampMs = timestampMs
)
