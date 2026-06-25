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
import com.saiyanstrong.presentation.theme.SaiyanTheme

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: (Long) -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let(onWorkoutFinished)
    }

    ActiveWorkoutContent(
        uiState = uiState,
        onAddExerciseClicked = viewModel::onAddExerciseClicked,
        onExerciseSelected = viewModel::onExerciseSelected,
        onExercisePickerDismissed = viewModel::onExercisePickerDismissed,
        onLogSet = viewModel::onLogSet,
        onSkipRest = viewModel::onSkipRest,
        onFinishWorkout = viewModel::onFinishWorkout
    )
}

@Composable
private fun ActiveWorkoutContent(
    uiState: ActiveWorkoutUiState,
    onAddExerciseClicked: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    onExercisePickerDismissed: () -> Unit,
    onLogSet: (Double, Int, Float?) -> Unit,
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { exerciseLog ->
                    ExerciseLogRow(
                        exerciseLog = exerciseLog,
                        isActive = exerciseLog.exercise.id == uiState.activeExerciseId,
                        onLogSet = onLogSet
                    )
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
private fun ExerciseLogRow(
    exerciseLog: ExerciseLog,
    isActive: Boolean,
    onLogSet: (Double, Int, Float?) -> Unit
) {
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var rpeText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(exerciseLog.exercise.name, style = MaterialTheme.typography.titleMedium)
        exerciseLog.sets.forEach { set ->
            Text("Set ${set.setNumber}: ${set.weightKg} kg x ${set.reps}")
        }
        if (isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

@PreviewLightDark
@Composable
private fun ActiveWorkoutContentPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(),
            onAddExerciseClicked = {},
            onExerciseSelected = {},
            onExercisePickerDismissed = {},
            onLogSet = { _, _, _ -> },
            onSkipRest = {},
            onFinishWorkout = {}
        )
    }
}
