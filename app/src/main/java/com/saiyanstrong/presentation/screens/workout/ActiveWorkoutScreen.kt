package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.presentation.screens.visualizer.VisualizerScreen
import com.saiyanstrong.presentation.screens.visualizer.VisualizerState
import com.saiyanstrong.presentation.screens.visualizer.VisualizerViewModel
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.util.WeightFormatter

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: (Long) -> Unit,
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel(),
    visualizerViewModel: VisualizerViewModel = hiltViewModel()
) {
    val uiState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    val visualizerState by visualizerViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let(onWorkoutFinished)
    }

    ActiveWorkoutContent(
        uiState = uiState,
        visualizerState = visualizerState,
        onAddExerciseClicked = workoutViewModel::onAddExerciseClicked,
        onExerciseSelected = { exercise ->
            workoutViewModel.onExerciseSelected(exercise)
            visualizerViewModel.onExerciseSelected(exercise)
        },
        onExercisePickerDismissed = workoutViewModel::onExercisePickerDismissed,
        onBeginSet = visualizerViewModel::onBeginSet,
        onLogSet = { weightKg, reps, rpe ->
            workoutViewModel.onLogSet(weightKg, reps, rpe)
            visualizerViewModel.onSetLogged(weightKg, reps)
        },
        onNextSet = visualizerViewModel::onNextSet,
        onSkipRest = workoutViewModel::onSkipRest,
        onFinishWorkout = workoutViewModel::onFinishWorkout
    )
}

@Composable
private fun ActiveWorkoutContent(
    uiState: ActiveWorkoutUiState,
    visualizerState: VisualizerState,
    onAddExerciseClicked: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    onExercisePickerDismissed: () -> Unit,
    onBeginSet: () -> Unit,
    onLogSet: (Double, Int, Float?) -> Unit,
    onNextSet: () -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { exerciseLog ->
                    ExerciseLogSummaryRow(exerciseLog)
                }
            }

            if (uiState.activeExerciseId != null) {
                VisualizerScreen(
                    state = visualizerState,
                    onBeginSet = onBeginSet,
                    onNextSet = onNextSet
                )

                if (visualizerState is VisualizerState.DynamicTransition) {
                    SetEntryForm(onLogSet = onLogSet)
                }
            }

            uiState.restTimerSecondsRemaining?.let { secondsRemaining ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rest: ${secondsRemaining}s", color = MaterialTheme.colorScheme.secondary)
                    Button(onClick = onSkipRest) { Text("Skip") }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onAddExerciseClicked) { Text("Add Exercise") }
                Button(onClick = onFinishWorkout) { Text("Finish Workout") }
            }
        }
    }

    if (uiState.isExercisePickerVisible) {
        ExercisePickerSheet(
            exercises = uiState.availableExercises,
            onExerciseSelected = onExerciseSelected,
            onDismiss = onExercisePickerDismissed
        )
    }
}

@Composable
private fun ExerciseLogSummaryRow(exerciseLog: ExerciseLog) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(exerciseLog.exercise.name, style = MaterialTheme.typography.titleMedium)
        exerciseLog.sets.forEach { set ->
            Text("Set ${set.setNumber}: ${WeightFormatter.format(set.weightKg)} x ${set.reps}")
        }
    }
}

@Composable
private fun SetEntryForm(onLogSet: (Double, Int, Float?) -> Unit) {
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var rpeText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text("kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            label = { Text("reps") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = rpeText,
            onValueChange = { rpeText = it },
            label = { Text("RPE") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }

    val weightKg = weightText.toDoubleOrNull()
    val reps = repsText.toIntOrNull()
    Button(
        onClick = {
            onLogSet(weightKg!!, reps!!, rpeText.toFloatOrNull())
            weightText = ""
            repsText = ""
            rpeText = ""
        },
        enabled = weightKg != null && reps != null
    ) {
        Text("Log Set")
    }
}

@PreviewLightDark
@Composable
private fun ActiveWorkoutContentPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(),
            visualizerState = VisualizerState.Idle,
            onAddExerciseClicked = {},
            onExerciseSelected = {},
            onExercisePickerDismissed = {},
            onBeginSet = {},
            onLogSet = { _, _, _ -> },
            onNextSet = {},
            onSkipRest = {},
            onFinishWorkout = {}
        )
    }
}
