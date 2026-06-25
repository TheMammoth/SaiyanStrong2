package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetEvolutionStageUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val calculatePowerLevelUseCase: CalculatePowerLevelUseCase
) {
    fun execute(): Flow<PowerLevel> =
        userRepository.getLifetimePowerEarned().map { lifetimePowerEarned ->
            calculatePowerLevelUseCase.getPowerLevel(lifetimePowerEarned)
        }
}
