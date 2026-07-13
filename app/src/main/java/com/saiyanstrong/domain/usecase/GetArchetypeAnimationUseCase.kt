package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.domain.repository.BiomechanicsRepository
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Composes two repositories (BiomechanicsRepository for the archetype content/angle template,
 * UserRepository for the user's saved custom ratios) rather than either repository depending on
 * the other — cross-repository resolution belongs at the use-case layer per this project's
 * Clean Architecture rule ("Use Cases call Repository interfaces"). */
class GetArchetypeAnimationUseCase @Inject constructor(
    private val biomechanicsRepository: BiomechanicsRepository,
    private val userRepository: UserRepository
) {
    suspend fun execute(archetype: Archetype, lift: LiftType): ArchetypeAnimation {
        if (archetype != Archetype.CUSTOM) {
            return biomechanicsRepository.getAnimation(archetype, lift)
        }
        // CUSTOM has no JSON entry — reuse PROPORTIONAL's angle keyframes and content (sagittal
        // joint angles are a movement-pattern choice, not a body proportion the user sets here),
        // swap in the user's own saved LimbRatios.
        val template = biomechanicsRepository.getAnimation(Archetype.PROPORTIONAL, lift)
        val customRatios = userRepository.getCustomLimbRatios().first()
        return template.copy(
            archetype = Archetype.CUSTOM,
            limbRatios = customRatios,
            mechanicalFacts = listOf(
                "This build reflects the proportions you set below — torso lean, knee travel, and " +
                    "hip depth follow whatever thigh, shin, and torso lengths you chose.",
                "Widening or narrowing a ratio changes how this animation moves; there's no single " +
                    "correct setting, only what matches your own build.",
                "The bar still stays over mid-foot throughout, the same physical constraint every " +
                    "proportion has to satisfy."
            ),
            stanceCue = "Adjust the sliders until the standing pose matches your own proportions, " +
                "then compare the descent to how your squat actually feels.",
            irrelevantCue = "\"Every squat should look the same\" → mechanics scale with the " +
                "proportions set here — there's no universal template to match."
        )
    }
}
