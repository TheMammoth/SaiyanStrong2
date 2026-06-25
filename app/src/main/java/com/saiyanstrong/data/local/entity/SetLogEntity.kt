package com.saiyanstrong.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_log_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exercise_log_id")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_log_id") val exerciseLogId: Long,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val reps: Int,
    val rpe: Float? = null,
    @ColumnInfo(name = "volume_kg") val volumeKg: Double,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long
)
