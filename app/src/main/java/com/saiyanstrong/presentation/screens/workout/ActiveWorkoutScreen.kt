package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.MuscleGroup
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
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
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val uiState by workoutViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let(onWorkoutFinished)
    }

    ActiveWorkoutContent(
        uiState = uiState,
        onViewHistory = onViewHistory,
        onAddExerciseClicked = workoutViewModel::onAddExerciseClicked,
        onExerciseSelected = workoutViewModel::onExerciseSelected,
        onExercisePickerDismissed = workoutViewModel::onExercisePickerDismissed,
        onAddSetClicked = workoutViewModel::onAddSetClicked,
        onLogSet = workoutViewModel::onLogSet,
        onDeleteSet = workoutViewModel::onDeleteSet,
        onSkipRest = workoutViewModel::onSkipRest,
        onAdjustRest = workoutViewModel::onAdjustRestTimer,
        onFinishWorkout = workoutViewModel::onFinishWorkout
    )
}

@Composable
internal fun ActiveWorkoutContent(
    uiState: ActiveWorkoutUiState,
    onViewHistory: () -> Unit,
    onAddExerciseClicked: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    onExercisePickerDismissed: () -> Unit,
    onAddSetClicked: (Int) -> Unit,
    onLogSet: (Int, Double, Int, Float?, Boolean) -> Unit,
    onDeleteSet: (Int, Int) -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onFinishWorkout: () -> Unit
) {
    val totalSets = uiState.exerciseLogs.sumOf { it.sets.size }
    val totalVolumeKg = uiState.exerciseLogs.sumOf { log -> log.sets.sumOf { it.volumeKg } }
    val expandedLog = uiState.exerciseLogs.find { it.exercise.id == uiState.expandedExerciseId }
    val headerSubtitle = when {
        expandedLog != null -> "${expandedLog.exercise.name} — SET ${expandedLog.sets.size + 1}"
        uiState.exerciseLogs.isNotEmpty() -> "${uiState.exerciseLogs.size} EXERCISE${if (uiState.exerciseLogs.size != 1) "S" else ""} LOADED"
        else -> "SELECT AN EXERCISE TO BEGIN"
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .padding(padding)
        ) {
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
                Text(headerSubtitle, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { exerciseLog ->
                    ExerciseLogCard(
                        exerciseLog = exerciseLog,
                        previousSets = uiState.previousPerformance[exerciseLog.exercise.id] ?: emptyList(),
                        isSetInputExpanded = exerciseLog.exercise.id == uiState.expandedExerciseId,
                        restTimerSecondsRemaining = if (exerciseLog.exercise.id == uiState.restTimerForExerciseId)
                            uiState.restTimerSecondsRemaining else null,
                        onAddSetClicked = { onAddSetClicked(exerciseLog.exercise.id) },
                        onLogSet = { weightKg, reps, rpe, isFailure ->
                            onLogSet(exerciseLog.exercise.id, weightKg, reps, rpe, isFailure)
                        },
                        onDeleteSet = { setIndex -> onDeleteSet(exerciseLog.exercise.id, setIndex) },
                        onSkipRest = onSkipRest,
                        onAdjustRest = onAdjustRest
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SaiyanButton(onClick = onAddExerciseClicked, modifier = Modifier.weight(1f)) {
                    Text("+ EXERCISE", fontWeight = FontWeight.Bold)
                }
                SaiyanButton(onClick = onViewHistory) { Text("LOG") }
                SaiyanButton(onClick = onFinishWorkout, modifier = Modifier.weight(1f)) {
                    Text("FINISH", fontWeight = FontWeight.Bold)
                }
            }

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
            usageCounts = uiState.exerciseUsageCounts,
            onExerciseSelected = onExerciseSelected,
            onDismiss = onExercisePickerDismissed
        )
    }
}

@Composable
internal fun ExerciseLogCard(
    exerciseLog: ExerciseLog,
    previousSets: List<SetLog> = emptyList(),
    isSetInputExpanded: Boolean = false,
    restTimerSecondsRemaining: Int? = null,
    onAddSetClicked: () -> Unit = {},
    onLogSet: (Double, Int, Float?, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (Int) -> Unit = {},
    onSkipRest: () -> Unit = {},
    onAdjustRest: (Int) -> Unit = {}
) {
    val stepKg = remember(exerciseLog.exercise.name) {
        val n = exerciseLog.exercise.name
        if (n.contains("Dumbbell", ignoreCase = true) || n.contains("Kettlebell", ignoreCase = true)) 2.0 else 5.0
    }
    val lastWeight = exerciseLog.sets.lastOrNull()?.weightKg ?: 60.0
    val lastReps = exerciseLog.sets.lastOrNull()?.reps ?: 5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(4.dp))
            .border(
                1.dp,
                NeonGreen.copy(alpha = if (isSetInputExpanded) 0.6f else 0.2f),
                RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            exerciseLog.exercise.name.uppercase(),
            color = if (isSetInputExpanded) NeonGreen else Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (exerciseLog.sets.isEmpty() && !isSetInputExpanded) {
            Text(
                "NO SETS LOGGED",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        if (exerciseLog.sets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                Text("PREV", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f))
                Text("KG", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("REPS", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                Text("×", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            }
            exerciseLog.sets.forEachIndexed { index, set ->
                SetTableRow(set = set, previousSet = previousSets.getOrNull(index), onDelete = { onDeleteSet(index) })
            }
        }

        if (restTimerSecondsRemaining != null) {
            InlineRestTimer(
                secondsRemaining = restTimerSecondsRemaining,
                onSkip = onSkipRest,
                onAdjust = onAdjustRest,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (isSetInputExpanded) {
            InlineSetInput(
                initialWeightKg = lastWeight,
                weightStepKg = stepKg,
                initialReps = lastReps,
                onLogSet = onLogSet,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            SaiyanButton(
                onClick = onAddSetClicked,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("+ ADD SET  >>>", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SetTableRow(set: SetLog, previousSet: SetLog?, onDelete: () -> Unit) {
    val prevText = previousSet?.let {
        "${WeightFormatter.format(it.weightKg)} × ${it.reps}${if (it.isFailure) "[F]" else ""}"
    } ?: "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonGreen.copy(alpha = 0.07f), RoundedCornerShape(2.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (set.isFailure) "F" else "${set.setNumber}",
            color = if (set.isFailure) DangerRed else NeonGreen,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Text(prevText, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
        Text(WeightFormatter.format(set.weightKg), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
        Text("${set.reps}", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
        TextButton(
            onClick = onDelete,
            modifier = Modifier.width(28.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("×", color = DangerRed.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InlineSetInput(
    initialWeightKg: Double,
    weightStepKg: Double = 5.0,
    initialReps: Int = 5,
    onLogSet: (Double, Int, Float?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightKg by remember { mutableStateOf(initialWeightKg) }
    var reps by remember { mutableIntStateOf(initialReps) }
    var isFailure by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NeonGreen.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { weightKg = (weightKg - weightStepKg).coerceAtLeast(0.0) }) {
                Text("-${weightStepKg.toInt()}", color = NeonGreen, fontWeight = FontWeight.Bold)
            }
            Text(
                WeightFormatter.format(weightKg),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { weightKg += weightStepKg }) {
                Text("+${weightStepKg.toInt()}", color = NeonGreen, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .padding(vertical = 8.dp)
                    .background(TelemetryGreen.copy(alpha = 0.3f))
            )

            TextButton(onClick = { if (reps > 1) reps-- }) {
                Text("−", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "$reps",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp)
            )
            TextButton(onClick = { reps++ }) {
                Text("+", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SaiyanButton(onClick = { isFailure = !isFailure }, modifier = Modifier.weight(1f)) {
                Text("[F]", color = if (isFailure) DangerRed else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
            SaiyanButton(
                onClick = { onLogSet(weightKg, reps, null, isFailure) },
                modifier = Modifier.weight(2.5f)
            ) {
                Text("LOG SET  >>>", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun InlineRestTimer(
    secondsRemaining: Int,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PowerAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, PowerAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("⏱ ${secondsRemaining}s", color = PowerAmber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SaiyanButton(onClick = { onAdjust(-30) }) { Text("-30s", fontSize = 10.sp) }
            SaiyanButton(onClick = { onAdjust(30) }) { Text("+30s", fontSize = 10.sp) }
            SaiyanButton(onClick = onSkip) { Text("SKIP", fontSize = 10.sp) }
        }
    }
}

@Preview(showBackground = true)
@PreviewLightDark
@Composable
internal fun ActiveWorkoutEmptyPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(),
            onViewHistory = {}, onAddExerciseClicked = {}, onExerciseSelected = {},
            onExercisePickerDismissed = {}, onAddSetClicked = {},
            onLogSet = { _, _, _, _, _ -> }, onDeleteSet = { _, _ -> },
            onSkipRest = {}, onAdjustRest = {}, onFinishWorkout = {}
        )
    }
}

@Preview(showBackground = true)
@PreviewLightDark
@Composable
internal fun ActiveWorkoutWithSetsPreview() {
    val mockExercise = Exercise(
        1, "Barbell Squat", ExerciseCategory.SQUAT,
        listOf(MuscleGroup.QUADRICEPS), emptyList(), "", "muscle_squat"
    )
    val mockLog = ExerciseLog(
        1, mockExercise,
        listOf(SetLog(1, 1, 100.0, 5), SetLog(2, 2, 105.0, 5, isFailure = true)),
        0
    )
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(
                exerciseLogs = listOf(mockLog),
                previousPerformance = mapOf(1 to listOf(SetLog(0, 1, 95.0, 5), SetLog(0, 2, 100.0, 5))),
                restTimerForExerciseId = 1,
                restTimerSecondsRemaining = 62,
                expandedExerciseId = 1
            ),
            onViewHistory = {}, onAddExerciseClicked = {}, onExerciseSelected = {},
            onExercisePickerDismissed = {}, onAddSetClicked = {},
            onLogSet = { _, _, _, _, _ -> }, onDeleteSet = { _, _ -> },
            onSkipRest = {}, onAdjustRest = {}, onFinishWorkout = {}
        )
    }
}
