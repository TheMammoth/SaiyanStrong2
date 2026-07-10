package com.saiyanstrong.data.backup

import androidx.room.withTransaction
import com.saiyanstrong.data.datastore.UserPreferencesDataStore
import com.saiyanstrong.data.local.AppDatabase
import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.dao.BodyWeightDao
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
import com.saiyanstrong.data.local.dao.TemplateDao
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import com.saiyanstrong.data.local.entity.BodyWeightEntity
import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import com.saiyanstrong.data.local.entity.SessionEntity
import com.saiyanstrong.data.local.entity.SetLogEntity
import com.saiyanstrong.data.local.entity.TemplateEntity
import com.saiyanstrong.data.local.entity.TemplateExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSerializer @Inject constructor(
    private val appDatabase: AppDatabase,
    private val sessionDao: SessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val templateDao: TemplateDao,
    private val bodyWeightDao: BodyWeightDao,
    private val exerciseDao: ExerciseDao,
    private val barPathMetricsDao: BarPathMetricsDao,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun buildPayload(): BackupPayload = BackupPayload(
        sessions = sessionDao.getAll().first().map { it.toDto() },
        exerciseLogs = exerciseLogDao.getAll().first().map { it.toDto() },
        setLogs = setLogDao.getAll().first().map { it.toDto() },
        templates = templateDao.getAllTemplates().first().map { it.toDto() },
        templateExercises = templateDao.getAllTemplateExercises().first().map { it.toDto() },
        bodyWeightLogs = bodyWeightDao.getAll().first().map { it.toDto() },
        exerciseRestTimerOverrides = exerciseDao.getAll().first()
            .mapNotNull { entity ->
                entity.restTimerSec?.let { ExerciseRestTimerOverrideDto(entity.id, it) }
            },
        lifetimePowerEarned = userPreferencesDataStore.lifetimePowerEarned.first(),
        useFemaleDotsFormula = userPreferencesDataStore.useFemaleDotsFormula.first(),
        defaultRestSeconds = userPreferencesDataStore.defaultRestSeconds.first(),
        barPathMetrics = barPathMetricsDao.getAll().first().map { it.toDto() }
    )

    fun encode(envelope: BackupEnvelope): String = json.encodeToString(envelope)

    fun decode(jsonString: String): BackupEnvelope = json.decodeFromString(jsonString)

    suspend fun restore(payload: BackupPayload) {
        appDatabase.withTransaction {
            setLogDao.deleteAll()
            exerciseLogDao.deleteAll()
            sessionDao.deleteAll()
            templateDao.deleteAllExercises()
            templateDao.deleteAll()
            bodyWeightDao.deleteAll()
            barPathMetricsDao.deleteAll()

            sessionDao.insertAll(payload.sessions.map { it.toEntity() })
            exerciseLogDao.insertAll(payload.exerciseLogs.map { it.toEntity() })
            setLogDao.insertAll(payload.setLogs.map { it.toEntity() })
            templateDao.insertAll(payload.templates.map { it.toEntity() })
            templateDao.insertExercises(payload.templateExercises.map { it.toEntity() })
            bodyWeightDao.insertAll(payload.bodyWeightLogs.map { it.toEntity() })

            // Pre-v0.25.0 backups have no exerciseId on bar path rows — resolve it from the
            // payload itself (set -> exercise_log -> exercise_id) rather than a DB query,
            // since these lists are already in memory here.
            val exerciseLogIdToExerciseId = payload.exerciseLogs.associate { it.id to it.exerciseId }
            val setLogIdToExerciseLogId = payload.setLogs.associate { it.id to it.exerciseLogId }
            barPathMetricsDao.insertAll(
                payload.barPathMetrics.map { dto ->
                    val resolvedExerciseId = dto.exerciseId.takeIf { it != 0 }
                        ?: dto.setLogId?.let { setLogIdToExerciseLogId[it] }?.let { exerciseLogIdToExerciseId[it] }
                        ?: 0
                    dto.toEntity(resolvedExerciseId)
                }
            )

            payload.exerciseRestTimerOverrides.forEach { override ->
                exerciseDao.updateRestTimer(override.exerciseId, override.restTimerSec)
            }
        }
        userPreferencesDataStore.setLifetimePowerEarned(payload.lifetimePowerEarned)
        userPreferencesDataStore.setUseFemaleDotsFormula(payload.useFemaleDotsFormula)
        userPreferencesDataStore.setDefaultRestSeconds(payload.defaultRestSeconds)
    }

    private fun SessionEntity.toDto() = SessionDto(id, dateMs, durationMs, totalVolumeKg, powerEarned, notes, title)
    private fun SessionDto.toEntity() = SessionEntity(id, dateMs, durationMs, totalVolumeKg, powerEarned, notes, title)

    private fun ExerciseLogEntity.toDto() = ExerciseLogDto(id, sessionId, exerciseId, orderIndex)
    private fun ExerciseLogDto.toEntity() = ExerciseLogEntity(id, sessionId, exerciseId, orderIndex)

    private fun SetLogEntity.toDto() = SetLogDto(id, exerciseLogId, setNumber, weightKg, reps, rpe, isFailure, volumeKg, timestampMs)
    private fun SetLogDto.toEntity() = SetLogEntity(id, exerciseLogId, setNumber, weightKg, reps, rpe, isFailure, volumeKg, timestampMs)

    private fun TemplateEntity.toDto() = TemplateDto(id, name, createdMs, isFromCoach)
    private fun TemplateDto.toEntity() = TemplateEntity(id, name, createdMs, isFromCoach)

    private fun TemplateExerciseEntity.toDto() = TemplateExerciseDto(id, templateId, exerciseId, orderIndex)
    private fun TemplateExerciseDto.toEntity() = TemplateExerciseEntity(id, templateId, exerciseId, orderIndex)

    private fun BodyWeightEntity.toDto() = BodyWeightDto(id, dateMs, weightKg)
    private fun BodyWeightDto.toEntity() = BodyWeightEntity(id, dateMs, weightKg)

    private fun BarPathMetricsEntity.toDto() = BarPathMetricsDto(
        id, setLogId, exerciseId, createdAtMs, peakVelocityMs, meanConcentricVelocityMs,
        peakPowerWatts, meanPowerWatts, rangeOfMotionCm, barPathDeviationCm, velocityZone
    )
    private fun BarPathMetricsDto.toEntity(resolvedExerciseId: Int) = BarPathMetricsEntity(
        id, setLogId, resolvedExerciseId, createdAtMs.takeIf { it != 0L } ?: System.currentTimeMillis(),
        peakVelocityMs, meanConcentricVelocityMs, peakPowerWatts, meanPowerWatts,
        rangeOfMotionCm, barPathDeviationCm, velocityZone
    )
}
