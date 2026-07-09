package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

class RedeemInviteCodeUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(code: String): Result<Unit> = coachRepository.redeemInviteCode(code)
}
