package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class PushTemplateUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(athleteId: String, name: String, exerciseIds: List<Int>): Result<Unit> =
        coachRepository.pushTemplate(athleteId, name, exerciseIds)
}
