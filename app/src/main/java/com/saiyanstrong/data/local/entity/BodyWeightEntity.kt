package com.saiyanstrong.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_weight_logs")
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_ms") val dateMs: Long,
    @ColumnInfo(name = "weight_kg") val weightKg: Double
)
