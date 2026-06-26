package com.saiyanstrong.data.repository

import androidx.room.withTransaction
import com.saiyanstrong.data.local.AppDatabase
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.dao.ExerciseLogDao
import com.saiyanstrong.data.local.dao.SessionDao
import com.saiyanstrong.data.local.dao.SetLogDao
import com.saiyanstrong.data.local.entity.SessionEntity
import com.saiyanstrong.data.mapper.toDomain
import com.saiyanstrong.data.mapper.toEntity
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val sessionDao: SessionDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
    private val exerciseDao: ExerciseDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<WorkoutSession>> =
        sessionDao.getAll().map { sessions -> sessions.map { it.toDomainWithDetails() } }

    override fun getSessionById(sessionId: Long): Flow<WorkoutSession?> =
        sessionDao.getById(sessionId).map { it?.toDomainWithDetails() }

    override suspend fun saveSession(session: WorkoutSession): Long =
        appDatabase.withTransaction {
            val sessionId = sessionDao.insert(session.toEntity())
            session.exerciseLogs.forEach { exerciseLog ->
                val exerciseLogId = exerciseLogDao.insert(exerciseLog.toEntity(sessionId))
                exerciseLog.sets.forEach { setLog ->
                    setLogDao.insert(setLog.toEntity(exerciseLogId))
                }
            }
            sessionId
        }

    override suspend fun deleteSession(sessionId: Long) =
        appDatabase.withTransaction {
            setLogDao.deleteForSession(sessionId)
            exerciseLogDao.deleteForSession(sessionId)
            sessionDao.deleteById(sessionId)
        }

    override suspend fun updateTitle(sessionId: Long, title: String) =
        sessionDao.updateTitle(sessionId, title)

    private suspend fun SessionEntity.toDomainWithDetails(): WorkoutSession {
        val exerciseLogEntities = exerciseLogDao.getForSession(id).first()
        val exerciseLogs = exerciseLogEntities.map { logEntity ->
            val exercise = exerciseDao.getById(logEntity.exerciseId).first()
                ?: error("Exercise ${logEntity.exerciseId} not found for log ${logEntity.id}")
            val sets = setLogDao.getForExerciseLog(logEntity.id).first().map { it.toDomain() }
            ExerciseLog(
                id = logEntity.id,
                exercise = exercise.toDomain(),
                sets = sets,
                orderIndex = logEntity.orderIndex
            )
        }
        return toDomain(exerciseLogs)
    }
}
