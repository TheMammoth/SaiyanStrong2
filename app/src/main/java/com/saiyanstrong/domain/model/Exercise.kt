package com.saiyanstrong.domain.model

enum class ExerciseCategory { SQUAT, HINGE, PUSH, PULL }

data class Exercise(
    val id: Int,
    val name: String,
    val category: ExerciseCategory,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val lottieAsset: String,
    val svgAssetName: String
)
