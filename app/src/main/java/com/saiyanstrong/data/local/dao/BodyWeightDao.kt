package com.saiyanstrong.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.BodyWeightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight_logs ORDER BY date_ms DESC")
    fun getAll(): Flow<List<BodyWeightEntity>>

    @Insert
    suspend fun insert(log: BodyWeightEntity): Long

    @Insert
    suspend fun insertAll(logs: List<BodyWeightEntity>)

    @Query("DELETE FROM body_weight_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_weight_logs")
    suspend fun deleteAll()
}
