package com.saiyanstrong.presentation.screens.visualizer

import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.MuscleGroup

sealed class VisualizerState {
    data object Idle : VisualizerState()

    // State 0: exercise selected → show anatomy SVG with muscle highlights
    data class Static(
        val exercise: Exercise,
        val highlightedMuscles: List<MuscleGroup>
    ) : VisualizerState()

    // State 1: user taps "Begin Set" → Lottie 3-pose sequence plays
    data class DynamicTransition(
        val exercise: Exercise,
        val poseIndex: Int = 0
    ) : VisualizerState()

    // State 2: set logged → full activation, Canvas particles fire
    data class FullActivation(
        val exercise: Exercise,
        val powerLevelGained: Int,
        val estimatedOneRmKg: Double
    ) : VisualizerState()
}
