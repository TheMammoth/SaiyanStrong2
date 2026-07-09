package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.SessionRepository
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deleting a session must reverse the lifetime power it earned — otherwise Power Level
 * (and the Saiyan stage derived from it) stays inflated by sessions that no longer exist.
 * CompleteSessionUseCase is the only other place lifetime power changes, so this is the
 * one place a deletion needs to undo that.
 */
class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository
) {
    suspend fun execute(sessionId: Long) {
        val session = sessionRepository.getSessionById(sessionId).first()
        sessionRepository.deleteSession(sessionId)
        session?.let { userRepository.addPowerEarned(-it.powerEarned) }
    }
}
