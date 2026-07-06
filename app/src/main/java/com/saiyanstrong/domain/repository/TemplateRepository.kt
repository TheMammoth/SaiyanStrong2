package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>
    suspend fun saveTemplate(name: String, exerciseIds: List<Int>): Long
    suspend fun deleteTemplate(templateId: Long)
}
