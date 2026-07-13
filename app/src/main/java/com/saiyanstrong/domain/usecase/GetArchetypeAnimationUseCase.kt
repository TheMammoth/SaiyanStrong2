package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.repository.BiomechanicsRepository
import javax.inject.Inject

class GetArchetypeAnimationUseCase @Inject constructor(
    private val biomechanicsRepository: BiomechanicsRepository
) {
    suspend fun execute(archetype: Archetype, lift: LiftType): ArchetypeAnimation =
        biomechanicsRepository.getAnimation(archetype, lift)
}
