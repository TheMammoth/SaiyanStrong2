package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getAllSessions(): Flow<List<WorkoutSession>>
    fun getSessionById(sessionId: Long): Flow<WorkoutSession?>
    suspend fun saveSession(session: WorkoutSession): Long
}
