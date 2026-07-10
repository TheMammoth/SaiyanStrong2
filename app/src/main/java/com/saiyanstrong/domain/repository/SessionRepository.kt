package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.ExerciseSetHistory
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getAllSessions(): Flow<List<WorkoutSession>>
    fun getSessionById(sessionId: Long): Flow<WorkoutSession?>
    fun getTotalPowerEarned(): Flow<Int>
    suspend fun saveSession(session: WorkoutSession): Long
    suspend fun deleteSession(sessionId: Long)
    suspend fun updateTitle(sessionId: Long, title: String)
    suspend fun updateSet(sessionId: Long, setLogId: Long, weightKg: Double, reps: Int, isFailure: Boolean)
    suspend fun deleteSet(sessionId: Long, setLogId: Long)
    suspend fun getLastSetsForExercise(exerciseId: Int): List<SetLog>
    fun getExerciseHistory(exerciseId: Int): Flow<List<ExerciseSetHistory>>
}
