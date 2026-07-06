package com.saiyanstrong.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.SetLogEntity
import kotlinx.coroutines.flow.Flow

data class SetWithDate(
    @ColumnInfo(name = "date_ms") val dateMs: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    @ColumnInfo(name = "is_failure") val isFailure: Boolean
)

@Dao
interface SetLogDao {
    @Query("SELECT * FROM set_logs WHERE exercise_log_id = :exerciseLogId ORDER BY set_number ASC")
    fun getForExerciseLog(exerciseLogId: Long): Flow<List<SetLogEntity>>

    @Insert
    suspend fun insert(setLog: SetLogEntity): Long

    @Query("DELETE FROM set_logs WHERE exercise_log_id IN (SELECT id FROM exercise_logs WHERE session_id = :sessionId)")
    suspend fun deleteForSession(sessionId: Long)

    @Query("""
        SELECT s.date_ms, s.id AS session_id, sl.weight_kg, sl.reps, sl.is_failure
        FROM set_logs sl
        INNER JOIN exercise_logs el ON sl.exercise_log_id = el.id
        INNER JOIN sessions s ON el.session_id = s.id
        WHERE el.exercise_id = :exerciseId
        ORDER BY s.date_ms ASC, sl.set_number ASC
    """)
    fun getHistoryForExercise(exerciseId: Int): Flow<List<SetWithDate>>
}
