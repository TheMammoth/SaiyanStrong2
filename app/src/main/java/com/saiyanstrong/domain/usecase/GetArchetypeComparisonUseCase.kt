package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.repository.BiomechanicsRepository
import javax.inject.Inject

/** Backs both compare entry points (spec Screen 4): the visualizer's 2-up "Compare with
 * another build" and the selection screen's 4-up "Compare all four" — same use case, callers
 * just pass a different-sized [archetypes] list. */
class GetArchetypeComparisonUseCase @Inject constructor(
    private val biomechanicsRepository: BiomechanicsRepository
) {
    suspend fun execute(archetypes: List<Archetype>, lift: LiftType): List<ArchetypeAnimation> =
        archetypes.map { biomechanicsRepository.getAnimation(it, lift) }
}
