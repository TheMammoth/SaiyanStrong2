package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.PushedTemplate
import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class GetPendingPushedTemplatesUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(): Result<List<PushedTemplate>> = coachRepository.getPendingPushedTemplates()
}
