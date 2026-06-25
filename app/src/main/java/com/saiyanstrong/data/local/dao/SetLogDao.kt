package com.saiyanstrong.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.SetLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SetLogDao {
    @Query("SELECT * FROM set_logs WHERE exercise_log_id = :exerciseLogId ORDER BY set_number ASC")
    fun getForExerciseLog(exerciseLogId: Long): Flow<List<SetLogEntity>>

    @Insert
    suspend fun insert(setLog: SetLogEntity): Long
}
