package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.UserRepository
import javax.inject.Inject

class CompleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val calculatePowerLevelUseCase: CalculatePowerLevelUseCase
) {
    suspend fun execute(
        dateMs: Long,
        durationMs: Long,
        exerciseLogs: List<ExerciseLog>,
        notes: String = ""
    ): WorkoutSession {
        val totalVolumeKg = exerciseLogs.sumOf { log -> log.sets.sumOf { it.volumeKg } }
        val powerEarned = exerciseLogs.sumOf { log ->
            calculatePowerLevelUseCase.sessionPowerGained(log.sets)
        }
        val session = WorkoutSession(
            dateMs = dateMs,
            durationMs = durationMs,
            exerciseLogs = exerciseLogs,
            totalVolumeKg = totalVolumeKg,
            powerEarned = powerEarned,
            notes = notes
        )
        val sessionId = sessionRepository.saveSession(session)
        userRepository.addPowerEarned(powerEarned)
        return session.copy(id = sessionId)
    }
}
