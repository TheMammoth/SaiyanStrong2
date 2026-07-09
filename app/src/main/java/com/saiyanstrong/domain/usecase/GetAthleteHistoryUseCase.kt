package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class GetAthleteHistoryUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(athleteId: String): Result<List<WorkoutSession>> =
        coachRepository.getAthleteHistory(athleteId)
}
