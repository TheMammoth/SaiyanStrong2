package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.AthleteSummary
import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class GetAthleteSummariesUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(): Result<List<AthleteSummary>> = coachRepository.getAthleteSummaries()
}
