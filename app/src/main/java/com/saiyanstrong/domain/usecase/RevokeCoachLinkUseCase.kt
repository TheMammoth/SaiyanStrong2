package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class RevokeCoachLinkUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(linkId: String): Result<Unit> = coachRepository.revokeCoachLink(linkId)
}
