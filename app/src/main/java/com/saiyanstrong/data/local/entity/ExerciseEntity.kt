package com.saiyanstrong.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val category: String,
    @ColumnInfo(name = "primary_muscles") val primaryMuscles: String,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: String,
    @ColumnInfo(name = "lottie_asset") val lottieAsset: String,
    @ColumnInfo(name = "svg_asset_name") val svgAssetName: String
)
