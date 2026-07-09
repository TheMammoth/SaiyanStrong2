package com.saiyanstrong.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY date_ms DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun getById(sessionId: Long): Flow<SessionEntity?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("SELECT COALESCE(SUM(power_earned), 0) FROM sessions")
    fun getTotalPowerEarned(): Flow<Int>
}
