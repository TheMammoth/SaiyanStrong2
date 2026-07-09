package com.saiyanstrong.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val timestampMs: Long,
    val appVersionCode: Int,
    val payload: BackupPayload
)

@Serializable
data class BackupPayload(
    val sessions: List<SessionDto>,
    val exerciseLogs: List<ExerciseLogDto>,
    val setLogs: List<SetLogDto>,
    val templates: List<TemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val bodyWeightLogs: List<BodyWeightDto>,
    val exerciseRestTimerOverrides: List<ExerciseRestTimerOverrideDto>,
    val lifetimePowerEarned: Int,
    val useFemaleDotsFormula: Boolean,
    val defaultRestSeconds: Int
)

@Serializable
data class SessionDto(
    val id: Long,
    val dateMs: Long,
    val durationMs: Long,
    val totalVolumeKg: Double,
    val powerEarned: Int,
    val notes: String,
    val title: String
)

@Serializable
data class ExerciseLogDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Int,
    val orderIndex: Int
)

@Serializable
data class SetLogDto(
    val id: Long,
    val exerciseLogId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Float?,
    val isFailure: Boolean,
    val volumeKg: Double,
    val timestampMs: Long
)

@Serializable
data class TemplateDto(
    val id: Long,
    val name: String,
    val createdMs: Long,
    val isFromCoach: Boolean = false
)

@Serializable
data class TemplateExerciseDto(
    val id: Long,
    val templateId: Long,
    val exerciseId: Int,
    val orderIndex: Int
)

@Serializable
data class BodyWeightDto(
    val id: Long,
    val dateMs: Long,
    val weightKg: Double
)

@Serializable
data class ExerciseRestTimerOverrideDto(
    val exerciseId: Int,
    val restTimerSec: Int
)
