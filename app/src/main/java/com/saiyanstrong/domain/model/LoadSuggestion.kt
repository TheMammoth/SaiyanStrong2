package com.saiyanstrong.domain.model

sealed class LoadSuggestion {
    data class MoreWeight(val targetWeightKg: Double) : LoadSuggestion()
    data class MoreReps(val targetReps: Int) : LoadSuggestion()
    data object Hold : LoadSuggestion()
    data object EaseOff : LoadSuggestion()
}
