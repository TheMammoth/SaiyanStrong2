package com.saiyanstrong.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bar_path_metrics",
    foreignKeys = [
        ForeignKey(
            entity = SetLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_log_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"]
        )
    ],
    indices = [Index("set_log_id"), Index("exercise_id"), Index("created_at_ms")]
)
data class BarPathMetricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "set_log_id") val setLogId: Long?,
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "peak_velocity_ms") val peakVelocityMs: Double,
    @ColumnInfo(name = "mean_concentric_velocity_ms") val meanConcentricVelocityMs: Double,
    @ColumnInfo(name = "peak_power_watts") val peakPowerWatts: Double,
    @ColumnInfo(name = "mean_power_watts") val meanPowerWatts: Double,
    @ColumnInfo(name = "range_of_motion_cm") val rangeOfMotionCm: Double,
    @ColumnInfo(name = "bar_path_deviation_cm") val barPathDeviationCm: Double,
    @ColumnInfo(name = "velocity_zone") val velocityZone: String
)
