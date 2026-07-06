package com.saiyanstrong.data.repository

import androidx.room.withTransaction
import com.saiyanstrong.data.local.AppDatabase
import com.saiyanstrong.data.local.dao.TemplateDao
import com.saiyanstrong.data.local.entity.TemplateEntity
import com.saiyanstrong.data.local.entity.TemplateExerciseEntity
import com.saiyanstrong.domain.model.WorkoutTemplate
import com.saiyanstrong.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val templateDao: TemplateDao
) : TemplateRepository {

    override fun getAllTemplates(): Flow<List<WorkoutTemplate>> =
        combine(
            templateDao.getAll(),
            templateDao.getAllTemplateExerciseEntries()
        ) { templates, entries ->
            val entriesByTemplate = entries.groupBy { it.templateId }
            templates.map { template ->
                val templateEntries = entriesByTemplate[template.id].orEmpty()
                WorkoutTemplate(
                    id = template.id,
                    name = template.name,
                    createdMs = template.createdMs,
                    exerciseIds = templateEntries.map { it.exerciseId },
                    exerciseNames = templateEntries.map { it.name }
                )
            }
        }

    override suspend fun saveTemplate(name: String, exerciseIds: List<Int>): Long =
        appDatabase.withTransaction {
            val templateId = templateDao.insert(
                TemplateEntity(name = name, createdMs = System.currentTimeMillis())
            )
            exerciseIds.forEachIndexed { index, exerciseId ->
                templateDao.insertExercise(
                    TemplateExerciseEntity(
                        templateId = templateId,
                        exerciseId = exerciseId,
                        orderIndex = index
                    )
                )
            }
            templateId
        }

    override suspend fun deleteTemplate(templateId: Long) =
        templateDao.deleteById(templateId)
}
