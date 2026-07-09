package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetEvolutionStageUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val calculatePowerLevelUseCase: CalculatePowerLevelUseCase
) {
    // Derived live from actual sessions rather than a separately-maintained counter —
    // deleting a session immediately reduces Power Level with no bookkeeping to forget.
    fun execute(): Flow<PowerLevel> =
        sessionRepository.getTotalPowerEarned().map { totalPowerEarned ->
            calculatePowerLevelUseCase.getPowerLevel(totalPowerEarned)
        }
}
