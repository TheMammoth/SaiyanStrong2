package com.saiyanstrong.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_ms") val dateMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double,
    @ColumnInfo(name = "power_earned") val powerEarned: Int,
    val notes: String = "",
    val title: String = ""
)
