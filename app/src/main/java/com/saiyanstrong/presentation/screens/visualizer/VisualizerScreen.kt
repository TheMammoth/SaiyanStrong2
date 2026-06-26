package com.saiyanstrong.presentation.screens.visualizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.domain.model.MuscleGroup
import com.saiyanstrong.presentation.components.TelemetryLog
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.util.WeightFormatter

@Composable
fun VisualizerScreen(
    state: VisualizerState,
    onBeginSet: () -> Unit,
    onNextSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryLog(message = state.toTelemetryMessage())

            when (state) {
                is VisualizerState.Idle -> {
                    Text("Select an exercise to begin.")
                }

                is VisualizerState.Static -> {
                    Text(state.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.highlightedMuscles.joinToString(", ") { m ->
                            m.name.replace('_', ' ').lowercase()
                                .replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onBeginSet) { Text("Begin Set") }
                }

                is VisualizerState.DynamicTransition -> {
                    Text(state.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text("Pose ${state.poseIndex + 1} / 3")
                }

                is VisualizerState.FullActivation -> {
                    ParticleTendrilCanvas()
                    Text("+${state.powerLevelGained} Power", color = MaterialTheme.colorScheme.secondary)
                    Text("Est. 1RM: ${WeightFormatter.formatOneRm(state.estimatedOneRmKg)}")
                    Button(onClick = onNextSet) { Text("Next Set") }
                }
            }
        }
    }
}

private fun VisualizerState.toTelemetryMessage(): String = when (this) {
    is VisualizerState.Idle -> "AWAITING EXERCISE SELECTION..."
    is VisualizerState.Static -> "TARGET LOCKED: ${exercise.name.uppercase()}"
    is VisualizerState.DynamicTransition -> "POWER SURGE INITIATED..."
    is VisualizerState.FullActivation -> "FULL ACTIVATION — +$powerLevelGained POWER"
}

private val PreviewExercise = Exercise(
    id = 1,
    name = "Barbell Squat",
    category = ExerciseCategory.SQUAT,
    primaryMuscles = listOf(MuscleGroup.QUADRICEPS, MuscleGroup.GLUTEUS_MAXIMUS),
    secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS),
    lottieAsset = "squat_transition.json",
    svgAssetName = "muscle_squat"
)

@PreviewLightDark
@Composable
private fun VisualizerScreenIdlePreview() {
    SaiyanTheme {
        VisualizerScreen(state = VisualizerState.Idle, onBeginSet = {}, onNextSet = {})
    }
}

@PreviewLightDark
@Composable
private fun VisualizerScreenFullActivationPreview() {
    SaiyanTheme {
        VisualizerScreen(
            state = VisualizerState.FullActivation(
                exercise = PreviewExercise,
                powerLevelGained = 142,
                estimatedOneRmKg = 187.5
            ),
            onBeginSet = {},
            onNextSet = {}
        )
    }
}
