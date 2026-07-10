package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.BarPathRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class BarPathQuota(
    val usedThisMonth: Int,
    val limit: Int = 5,
    val isUnlimited: Boolean
) {
    val isExhausted: Boolean get() = !isUnlimited && usedThisMonth >= limit
}

/**
 * The one place the standalone bar-path free/Coach quota is checked — mirrors
 * IsCoachUseCase's "single shared check" precedent. Quota is derived live from
 * this-month's freestanding analysis count, not a separately maintained counter,
 * so it can never drift the way lifetimePowerEarned once did (see CLAUDE.md v0.18.1).
 */
class GetBarPathQuotaUseCase @Inject constructor(
    private val barPathRepository: BarPathRepository,
    private val isCoachUseCase: IsCoachUseCase
) {
    fun execute(): Flow<BarPathQuota> = combine(
        barPathRepository.getFreestandingCountThisMonth(),
        flow { emit(isCoachUseCase.execute()) }
    ) { count, isCoach -> computeBarPathQuota(count, isCoach) }
}

/** Pure, top-level, and unit-testable without faking the repository/use-case chain. */
internal fun computeBarPathQuota(usedThisMonth: Int, isCoach: Boolean): BarPathQuota =
    BarPathQuota(usedThisMonth = usedThisMonth, isUnlimited = isCoach)
