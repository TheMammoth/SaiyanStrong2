package com.saiyanstrong.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.ExerciseLogEntity
import kotlinx.coroutines.flow.Flow

data class ExerciseUsageCount(
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    val count: Int
)

@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_logs WHERE session_id = :sessionId ORDER BY order_index ASC")
    fun getForSession(sessionId: Long): Flow<List<ExerciseLogEntity>>

    @Insert
    suspend fun insert(exerciseLog: ExerciseLogEntity): Long

    @Query("DELETE FROM exercise_logs WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("""
        SELECT el.id FROM exercise_logs el
        INNER JOIN sessions s ON el.session_id = s.id
        WHERE el.exercise_id = :exerciseId
        ORDER BY s.date_ms DESC
        LIMIT 1
    """)
    suspend fun getMostRecentExerciseLogId(exerciseId: Int): Long?

    @Query("SELECT exercise_id, COUNT(*) as count FROM exercise_logs GROUP BY exercise_id")
    fun getUsageCounts(): Flow<List<ExerciseUsageCount>>
}
