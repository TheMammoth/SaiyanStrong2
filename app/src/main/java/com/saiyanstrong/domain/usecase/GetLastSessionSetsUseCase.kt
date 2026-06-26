package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.repository.SessionRepository
import javax.inject.Inject

class GetLastSessionSetsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend fun execute(exerciseId: Int): List<SetLog> =
        sessionRepository.getLastSetsForExercise(exerciseId)
}
