package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class GenerateInviteCodeUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(): Result<String> = coachRepository.generateInviteCode()
}
