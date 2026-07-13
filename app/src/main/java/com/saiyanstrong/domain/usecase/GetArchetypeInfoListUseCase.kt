package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.ArchetypeInfo
import com.saiyanstrong.domain.repository.BiomechanicsRepository
import javax.inject.Inject

class GetArchetypeInfoListUseCase @Inject constructor(
    private val biomechanicsRepository: BiomechanicsRepository
) {
    suspend fun execute(): List<ArchetypeInfo> = biomechanicsRepository.getArchetypeInfoList()
}
