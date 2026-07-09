package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.PushedTemplate
import com.saiyanstrong.domain.repository.CoachRepository
import com.saiyanstrong.domain.repository.TemplateRepository
import javax.inject.Inject

/**
 * Saves a coach-pushed template into the athlete's own local templates (with isFromCoach=true
 * so it keeps the badge), then marks it accepted server-side so it won't be re-offered.
 */
class AcceptPushedTemplateUseCase @Inject constructor(
    private val coachRepository: CoachRepository,
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(pushedTemplate: PushedTemplate): Result<Unit> = runCatching {
        templateRepository.saveTemplate(
            name = pushedTemplate.name,
            exerciseIds = pushedTemplate.exerciseIds,
            isFromCoach = true
        )
        coachRepository.markPushedTemplateAccepted(pushedTemplate.id).getOrThrow()
    }
}
