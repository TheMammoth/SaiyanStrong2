package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getAllExercises(): Flow<List<Exercise>>
    fun getExerciseById(exerciseId: Int): Flow<Exercise?>
    fun getExerciseUsageCounts(): Flow<Map<Int, Int>>
}
