package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.CoachRepository
import javax.inject.Inject

/**
 * The ONE place Coach entitlement is checked client-side. Every screen/ViewModel that
 * needs to gate coach-only UI calls this — never re-implement the check inline.
 */
class IsCoachUseCase @Inject constructor(
    private val coachRepository: CoachRepository
) {
    suspend fun execute(): Boolean = coachRepository.isCoach().getOrDefault(false)
}
