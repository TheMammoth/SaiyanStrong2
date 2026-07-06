package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.ExerciseMedia

interface ExerciseMediaRepository {
    /** Best-effort match of one of our exercise names against the free-exercise-db dataset. */
    suspend fun getMediaFor(exerciseName: String): ExerciseMedia?
}
