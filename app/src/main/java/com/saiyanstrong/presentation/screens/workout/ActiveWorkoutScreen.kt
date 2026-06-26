package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Exercise
import com.saiyanstrong.domain.model.ExerciseCategory
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.MuscleGroup
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter

private val CompletedSetBg = Color(0xFF0D2B14)   // dark green tint
private val CompletedCheckBg = Color(0xFF2E7D32)  // filled green for ✓
private val PendingSetBg = Color(0xFF111111)
private val RestLabelColor = Color(0xFF4CAF50)    // teal-green

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: (Long) -> Unit,
    onViewHistory: () -> Unit,
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val uiState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    val elapsedSeconds by workoutViewModel.elapsedSeconds.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let(onWorkoutFinished)
    }

    ActiveWorkoutContent(
        uiState = uiState,
        elapsedSeconds = elapsedSeconds,
        restDurationSeconds = workoutViewModel.restDurationSeconds,
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
    elapsedSeconds: Int = 0,
    restDurationSeconds: Int = 90,
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
    val elapsedFormatted = formatElapsed(elapsedSeconds)
    val restLabel = formatRestLabel(restDurationSeconds)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .padding(padding)
        ) {
            // ── Top bar ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("←", color = Color.White, fontSize = 20.sp,
                    modifier = Modifier.clickable { onViewHistory() }.padding(4.dp))
                Spacer(Modifier.weight(1f))
                Text(elapsedFormatted, color = Color.White, fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinishWorkout, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("FINISH", color = NeonGreen, fontWeight = FontWeight.Black,
                        fontSize = 15.sp, letterSpacing = 1.sp)
                }
            }

            // ── Workout title ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "TRAINING SESSION",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(elapsedFormatted, color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }

            // ── Exercise cards ──────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { exerciseLog ->
                    ExerciseLogCard(
                        exerciseLog = exerciseLog,
                        previousSets = uiState.previousPerformance[exerciseLog.exercise.id] ?: emptyList(),
                        isSetInputExpanded = exerciseLog.exercise.id == uiState.expandedExerciseId,
                        restTimerSecondsRemaining = if (exerciseLog.exercise.id == uiState.restTimerForExerciseId)
                            uiState.restTimerSecondsRemaining else null,
                        restLabel = restLabel,
                        onAddSetClicked = { onAddSetClicked(exerciseLog.exercise.id) },
                        onLogSet = { weightKg, reps, rpe, isFailure ->
                            onLogSet(exerciseLog.exercise.id, weightKg, reps, rpe, isFailure)
                        },
                        onDeleteSet = { setIndex -> onDeleteSet(exerciseLog.exercise.id, setIndex) },
                        onSkipRest = onSkipRest,
                        onAdjustRest = onAdjustRest
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(onClick = onAddExerciseClicked) {
                            Text("ADD EXERCISE", color = NeonGreen, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // ── Telemetry bar ───────────────────────────────────
            Text(
                "// SETS: $totalSets  |  VOL: ${WeightFormatter.formatVolume(totalVolumeKg)} //",
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

// ── Exercise card ────────────────────────────────────────────────────────────

@Composable
internal fun ExerciseLogCard(
    exerciseLog: ExerciseLog,
    previousSets: List<SetLog> = emptyList(),
    isSetInputExpanded: Boolean = false,
    restTimerSecondsRemaining: Int? = null,
    restLabel: String = "1:30",
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
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = if (isSetInputExpanded) 0.6f else 0.22f), RoundedCornerShape(6.dp))
    ) {
        // ── Exercise header ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                exerciseLog.exercise.name,
                color = NeonGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Column headers ──────────────────────────────────
        if (exerciseLog.sets.isNotEmpty() || isSetInputExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                Text("PREVIOUS", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                Text("KG", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("✓", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            }
        }

        // ── Completed sets ──────────────────────────────────
        exerciseLog.sets.forEachIndexed { index, set ->
            CompletedSetRow(
                set = set,
                previousSet = previousSets.getOrNull(index),
                onDelete = { onDeleteSet(index)},
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
            val showRestLabel = index < exerciseLog.sets.size - 1 || restTimerSecondsRemaining != null || isSetInputExpanded
            if (showRestLabel) {
                Text(
                    restLabel,
                    color = RestLabelColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }

        // ── Active rest timer bar ───────────────────────────
        if (restTimerSecondsRemaining != null) {
            RestTimerBar(
                secondsRemaining = restTimerSecondsRemaining,
                onSkip = onSkipRest,
                onAdjust = onAdjustRest,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // ── Pending set row ─────────────────────────────────
        if (isSetInputExpanded) {
            PendingSetRow(
                setNumber = exerciseLog.sets.size + 1,
                previousSet = previousSets.getOrNull(exerciseLog.sets.size),
                initialWeightKg = lastWeight,
                initialReps = lastReps,
                weightStepKg = stepKg,
                onLogSet = onLogSet,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        // ── ADD SET ─────────────────────────────────────────
        if (restTimerSecondsRemaining == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(onClick = onAddSetClicked) {
                    Text("ADD SET ($restLabel)", color = NeonGreen,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

// ── Completed set row ────────────────────────────────────────────────────────

@Composable
private fun CompletedSetRow(set: SetLog, previousSet: SetLog?, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val prevText = previousSet?.let {
        "${WeightFormatter.format(it.weightKg)} × ${it.reps}${if (it.isFailure) " [F]" else ""}"
    } ?: "—"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CompletedSetBg, RoundedCornerShape(4.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (set.isFailure) "F" else "${set.setNumber}",
            color = if (set.isFailure) DangerRed else Color.White,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Text(prevText, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.weight(1.2f))
        Text(
            WeightFormatter.format(set.weightKg).replace(" kg", ""),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        Text(
            "${set.reps}",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center
        )
        Column(
            modifier = Modifier
                .width(36.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CompletedCheckBg)
                .clickable { onDelete() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ── Pending set row ──────────────────────────────────────────────────────────

@Composable
private fun PendingSetRow(
    setNumber: Int,
    previousSet: SetLog?,
    initialWeightKg: Double,
    initialReps: Int,
    weightStepKg: Double,
    onLogSet: (Double, Int, Float?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightKg by remember { mutableStateOf(initialWeightKg) }
    var reps by remember { mutableIntStateOf(initialReps) }
    var isFailure by remember { mutableStateOf(false) }
    val prevText = previousSet?.let { "${WeightFormatter.format(it.weightKg)} × ${it.reps}" } ?: "—"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PendingSetBg, RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isFailure) "F" else "$setNumber",
            color = if (isFailure) DangerRed else Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Text(prevText, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.weight(1.2f))

        // KG stepper
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("−", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { weightKg = (weightKg - weightStepKg).coerceAtLeast(0.0) }.padding(horizontal = 4.dp))
            Text(
                weightKg.let { if (it == it.toLong().toDouble()) "${it.toLong()}" else "%.1f".format(it) },
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(36.dp)
            )
            Text("+", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { weightKg += weightStepKg }.padding(horizontal = 4.dp))
        }

        // REPS stepper
        Row(modifier = Modifier.weight(0.8f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("−", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { if (reps > 1) reps-- }.padding(horizontal = 3.dp))
            Text("$reps", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            Text("+", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clickable { reps++ }.padding(horizontal = 3.dp))
        }

        // LOG ✓ button
        Column(
            modifier = Modifier
                .width(40.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NeonGreen.copy(alpha = 0.25f))
                .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clickable { onLogSet(weightKg, reps, null, isFailure) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✓", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }

    // Failure toggle below
    TextButton(onClick = { isFailure = !isFailure }, modifier = Modifier.fillMaxWidth()) {
        Text("[F] FAILURE SET", color = if (isFailure) DangerRed else Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Rest timer bar ───────────────────────────────────────────────────────────

@Composable
private fun RestTimerBar(
    secondsRemaining: Int,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(PowerAmber)
    ) {
        Text(
            formatElapsed(secondsRemaining),
            color = Color.Black,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onAdjust(-30) }) { Text("-30s", color = Color.Black, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { onAdjust(30) }) { Text("+30s", color = Color.Black, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onSkip) { Text("SKIP", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

private fun formatRestLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0) "$m:00" else "%d:%02d".format(m, s)
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
internal fun ActiveWorkoutEmptyPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(),
            elapsedSeconds = 10,
            onViewHistory = {}, onAddExerciseClicked = {}, onExerciseSelected = {},
            onExercisePickerDismissed = {}, onAddSetClicked = {},
            onLogSet = { _, _, _, _, _ -> }, onDeleteSet = { _, _ -> },
            onSkipRest = {}, onAdjustRest = {}, onFinishWorkout = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
internal fun ActiveWorkoutWithSetsPreview() {
    val mockExercise = Exercise(
        1, "Deadlift (Barbell)", ExerciseCategory.SQUAT,
        listOf(MuscleGroup.QUADRICEPS), emptyList(), "", "muscle_squat"
    )
    val mockLog = ExerciseLog(
        1, mockExercise,
        listOf(
            SetLog(1, 1, 150.0, 2),
            SetLog(2, 2, 160.0, 2),
            SetLog(3, 3, 180.0, 5)
        ),
        0
    )
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(
                exerciseLogs = listOf(mockLog),
                previousPerformance = mapOf(1 to listOf(
                    SetLog(0, 1, 150.0, 2), SetLog(0, 2, 160.0, 2), SetLog(0, 3, 180.0, 5)
                )),
                restTimerForExerciseId = 1,
                restTimerSecondsRemaining = 238,
                expandedExerciseId = 1
            ),
            elapsedSeconds = 610,
            onViewHistory = {}, onAddExerciseClicked = {}, onExerciseSelected = {},
            onExercisePickerDismissed = {}, onAddSetClicked = {},
            onLogSet = { _, _, _, _, _ -> }, onDeleteSet = { _, _ -> },
            onSkipRest = {}, onAdjustRest = {}, onFinishWorkout = {}
        )
    }
}
