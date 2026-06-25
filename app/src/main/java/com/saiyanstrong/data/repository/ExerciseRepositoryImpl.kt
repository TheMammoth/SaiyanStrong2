package com.saiyanstrong.data.repository

import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.mapper.toDomain
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao
) : ExerciseRepository {

    override fun getAllExercises(): Flow<List<Exercise>> =
        exerciseDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getExerciseById(exerciseId: Int): Flow<Exercise?> =
        exerciseDao.getById(exerciseId).map { it?.toDomain() }
}
