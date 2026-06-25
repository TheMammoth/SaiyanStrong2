package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.WeightKnobButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.screens.visualizer.VisualizerScreen
import com.saiyanstrong.presentation.screens.visualizer.VisualizerState
import com.saiyanstrong.presentation.screens.visualizer.VisualizerViewModel
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: (Long) -> Unit,
    onViewHistory: () -> Unit,
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
        onViewHistory = onViewHistory,
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
    onViewHistory: () -> Unit,
    onAddExerciseClicked: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    onExercisePickerDismissed: () -> Unit,
    onBeginSet: () -> Unit,
    onLogSet: (Double, Int, Float?) -> Unit,
    onNextSet: () -> Unit,
    onSkipRest: () -> Unit,
    onFinishWorkout: () -> Unit
) {
    val activeExerciseLog = uiState.exerciseLogs.find { it.exercise.id == uiState.activeExerciseId }
    val nextSetNumber = (activeExerciseLog?.sets?.size ?: 0) + 1
    val lastWeightKg = activeExerciseLog?.sets?.lastOrNull()?.weightKg ?: 60.0
    val totalSets = uiState.exerciseLogs.sumOf { it.sets.size }
    val totalVolumeKg = uiState.exerciseLogs.sumOf { log -> log.sets.sumOf { it.volumeKg } }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .padding(padding)
        ) {
            // Header bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "SAIYAN STRONG",
                    color = PowerAmber,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Text(
                    text = if (activeExerciseLog != null)
                        "${activeExerciseLog.exercise.name} — Set $nextSetNumber"
                    else
                        "SELECT AN EXERCISE TO BEGIN",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { exerciseLog ->
                    ExerciseLogCard(exerciseLog)
                }

                if (uiState.activeExerciseId != null) {
                    item {
                        VisualizerScreen(
                            state = visualizerState,
                            onBeginSet = onBeginSet,
                            onNextSet = onNextSet
                        )
                    }
                    if (visualizerState is VisualizerState.DynamicTransition) {
                        item {
                            SetInputPanel(initialWeightKg = lastWeightKg, onLogSet = onLogSet)
                        }
                    }
                }

                uiState.restTimerSecondsRemaining?.let { secs ->
                    item { RestTimerRow(secondsRemaining = secs, onSkip = onSkipRest) }
                }
            }

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SaiyanButton(onClick = onAddExerciseClicked, modifier = Modifier.weight(1f)) {
                    Text("+ EXERCISE", fontWeight = FontWeight.Bold)
                }
                SaiyanButton(onClick = onViewHistory) {
                    Text("LOG")
                }
                SaiyanButton(onClick = onFinishWorkout, modifier = Modifier.weight(1f)) {
                    Text("FINISH", fontWeight = FontWeight.Bold)
                }
            }

            // Telemetry bar
            Text(
                text = "// SETS: $totalSets  |  VOL: ${WeightFormatter.formatVolume(totalVolumeKg)} //",
                color = TelemetryGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
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
private fun ExerciseLogCard(exerciseLog: ExerciseLog) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            exerciseLog.exercise.name.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        if (exerciseLog.sets.isEmpty()) {
            Text(
                "NO SETS LOGGED",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            exerciseLog.sets.forEach { set ->
                Text(
                    "SET ${set.setNumber}  ${WeightFormatter.format(set.weightKg)} × ${set.reps}",
                    color = TelemetryGreen,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SetInputPanel(
    initialWeightKg: Double,
    onLogSet: (Double, Int, Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightKg by remember(initialWeightKg) { mutableStateOf(initialWeightKg) }
    var reps by remember { mutableIntStateOf(5) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(8.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WEIGHT", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WeightKnobButton("+10") { weightKg = (weightKg + 10.0).coerceAtLeast(0.0) }
            Text(
                WeightFormatter.format(weightKg),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            WeightKnobButton("+25") { weightKg = (weightKg + 25.0).coerceAtLeast(0.0) }
        }

        Text("REPS", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { if (reps > 1) reps-- }) {
                Text("−", color = NeonGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "$reps",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            IconButton(onClick = { reps++ }) {
                Text("+", color = NeonGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }

        SaiyanButton(
            onClick = { onLogSet(weightKg, reps, null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "LOG SET  >>>",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RestTimerRow(secondsRemaining: Int, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(4.dp))
            .border(1.dp, PowerAmber, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "⏱  REST  ${secondsRemaining}s",
            color = PowerAmber,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        SaiyanButton(onClick = onSkip) {
            Text("SKIP", fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

@PreviewLightDark
@Composable
private fun ActiveWorkoutContentPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(),
            visualizerState = VisualizerState.Idle,
            onViewHistory = {},
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
