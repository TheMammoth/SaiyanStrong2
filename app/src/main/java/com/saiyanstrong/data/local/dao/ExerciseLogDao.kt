package com.saiyanstrong.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs WHERE session_id = :sessionId ORDER BY order_index ASC")
    fun getForSession(sessionId: Long): Flow<List<ExerciseLogEntity>>

    @Insert
    suspend fun insert(exerciseLog: ExerciseLogEntity): Long

    @Query("DELETE FROM exercise_logs WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
