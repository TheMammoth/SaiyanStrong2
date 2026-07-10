package com.saiyanstrong.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BarPathMetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metrics: BarPathMetricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<BarPathMetricsEntity>)

    @Query("SELECT * FROM bar_path_metrics WHERE set_log_id = :setLogId")
    fun getForSetLog(setLogId: Long): Flow<BarPathMetricsEntity?>

    @Query("SELECT * FROM bar_path_metrics")
    fun getAll(): Flow<List<BarPathMetricsEntity>>

    @Query("DELETE FROM bar_path_metrics WHERE set_log_id = :setLogId")
    suspend fun deleteForSetLog(setLogId: Long)

    @Query("DELETE FROM bar_path_metrics")
    suspend fun deleteAll()
}
