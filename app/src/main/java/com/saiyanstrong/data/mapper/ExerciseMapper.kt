package com.saiyanstrong.data.mapper

import com.saiyanstrong.data.local.entity.ExerciseEntity
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.domain.model.MuscleGroup

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    category = ExerciseCategory.valueOf(category),
    primaryMuscles = primaryMuscles.toMuscleGroupList(),
    secondaryMuscles = secondaryMuscles.toMuscleGroupList(),
    lottieAsset = lottieAsset,
    svgAssetName = svgAssetName
)

private fun String.toMuscleGroupList(): List<MuscleGroup> =
    if (isBlank()) emptyList() else split(",").map { MuscleGroup.valueOf(it.trim()) }
