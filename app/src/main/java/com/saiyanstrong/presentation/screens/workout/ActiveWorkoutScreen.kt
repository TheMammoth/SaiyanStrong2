package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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

private val CompletedSetBg   = Color(0xFF1A3A1A)
private val CompletedCheckBg = Color(0xFF2E7D32)
private val PendingSetBg     = Color(0xFF111111)
private val RestLabelColor   = Color(0xFF4CAF50)

private fun Double.fmtKg(): String = WeightFormatter.format(this).replace(" kg", "")

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: (Long) -> Unit,
    onViewHistory: () -> Unit,
    workoutViewModel: ActiveWorkoutViewModel = hiltViewModel()
) {
    val uiState by workoutViewModel.uiState.collectAsStateWithLifecycle()
    val elapsedSeconds by workoutViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completedSessionId) { uiState.completedSessionId?.let(onWorkoutFinished) }
    ActiveWorkoutContent(
        uiState = uiState, elapsedSeconds = elapsedSeconds,
        restDurationSeconds = workoutViewModel.restDurationSeconds,
        onViewHistory = onViewHistory,
        onAddExerciseClicked = workoutViewModel::onAddExerciseClicked,
        onExerciseSelected = workoutViewModel::onExerciseSelected,
        onExercisePickerDismissed = workoutViewModel::onExercisePickerDismissed,
        onAddSetClicked = workoutViewModel::onAddSetClicked,
        onLogSet = workoutViewModel::onLogSet,
        onEditSet = workoutViewModel::onEditSet,
        onDeleteSet = workoutViewModel::onDeleteSet,
        onSkipRest = workoutViewModel::onSkipRest,
        onAdjustRest = workoutViewModel::onAdjustRestTimer,
        onFinishWorkout = workoutViewModel::onFinishWorkout
    )
}

// ── Content (stateless) ───────────────────────────────────────────────────────

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
    onEditSet: (Int, Int, Double, Int, Boolean) -> Unit = { _, _, _, _, _ -> },
    onDeleteSet: (Int, Int) -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onFinishWorkout: () -> Unit
) {
    val totalSets = uiState.exerciseLogs.sumOf { it.sets.size }
    val totalVolumeKg = uiState.exerciseLogs.sumOf { log -> log.sets.sumOf { it.volumeKg } }
    val elapsed   = formatElapsed(elapsedSeconds)
    val restLabel = formatRestLabel(restDurationSeconds)

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().scanlineTexture().padding(padding)) {

            // ── Top bar ──────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(SaiyanGray).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onViewHistory) {
                    Icon(Icons.Default.ExpandMore, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(elapsed, color = Color.White, fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinishWorkout, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("FINISH", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                }
            }

            // ── Workout header ───────────────────────────────────
            Column(Modifier.fillMaxWidth().background(SaiyanGray).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("TRAINING SESSION", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(elapsed, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }

            // ── Exercise list ────────────────────────────────────
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.exerciseLogs, key = { it.exercise.id }) { log ->
                    ExerciseLogCard(
                        exerciseLog = log,
                        previousSets = uiState.previousPerformance[log.exercise.id] ?: emptyList(),
                        pendingCount = uiState.pendingSetCounts[log.exercise.id] ?: 0,
                        restTimerSecondsRemaining = if (log.exercise.id == uiState.restTimerForExerciseId)
                            uiState.restTimerSecondsRemaining else null,
                        restLabel = restLabel,
                        onAddSetClicked = { onAddSetClicked(log.exercise.id) },
                        onLogSet = { kg, reps, rpe, fail -> onLogSet(log.exercise.id, kg, reps, rpe, fail) },
                        onEditSet = { idx, kg, reps, fail -> onEditSet(log.exercise.id, idx, kg, reps, fail) },
                        onDeleteSet = { idx -> onDeleteSet(log.exercise.id, idx) },
                        onSkipRest = onSkipRest,
                        onAdjustRest = onAdjustRest
                    )
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = onAddExerciseClicked) {
                            Text("ADD EXERCISE", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }

            // ── Telemetry bar ────────────────────────────────────
            Text(
                "// SETS: $totalSets  |  VOL: ${WeightFormatter.formatVolume(totalVolumeKg)} //",
                color = TelemetryGreen, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 6.dp)
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

// ── Exercise log card ─────────────────────────────────────────────────────────

@Composable
internal fun ExerciseLogCard(
    exerciseLog: ExerciseLog,
    previousSets: List<SetLog> = emptyList(),
    pendingCount: Int = 0,
    restTimerSecondsRemaining: Int? = null,
    restLabel: String = "1:30",
    onAddSetClicked: () -> Unit = {},
    onLogSet: (Double, Int, Float?, Boolean) -> Unit = { _, _, _, _ -> },
    onEditSet: (Int, Double, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteSet: (Int) -> Unit = {},
    onSkipRest: () -> Unit = {},
    onAdjustRest: (Int) -> Unit = {}
) {
    val lastWeight = exerciseLog.sets.lastOrNull()?.weightKg ?: 60.0
    val lastReps   = exerciseLog.sets.lastOrNull()?.reps ?: 5

    Column(
        Modifier.fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = if (pendingCount > 0) 0.6f else 0.22f), RoundedCornerShape(6.dp))
    ) {
        // Exercise header
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(exerciseLog.exercise.name, color = NeonGreen, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Link, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            }
        }

        // Column headers
        if (exerciseLog.sets.isNotEmpty() || pendingCount > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp)) {
                Text("SET",      color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.width(28.dp))
                Text("PREVIOUS", color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                Text("KG",       color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS",     color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("✓",        color = TelemetryGreen, fontSize = 10.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            }
        }

        // Completed sets
        exerciseLog.sets.forEachIndexed { idx, set ->
            CompletedSetRow(
                set = set, setIndex = idx,
                previousSet = previousSets.getOrNull(idx),
                onEdit = { kg, reps, fail -> onEditSet(idx, kg, reps, fail) },
                onDelete = { onDeleteSet(idx) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
            when {
                idx < exerciseLog.sets.size - 1 -> RestLabel(restLabel)
                restTimerSecondsRemaining != null ->
                    RestTimerBar(restTimerSecondsRemaining, onSkipRest = onSkipRest, onAdjust = onAdjustRest,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                pendingCount > 0 -> RestLabel(restLabel)
            }
        }

        // Pending sets
        repeat(pendingCount) { pendingIdx ->
            if (pendingIdx > 0) RestLabel(restLabel)
            PendingSetRow(
                setNumber = exerciseLog.sets.size + pendingIdx + 1,
                previousSet = previousSets.getOrNull(exerciseLog.sets.size + pendingIdx),
                initialWeightKg = lastWeight,
                initialReps = lastReps,
                onLogSet = onLogSet,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }

        // ADD SET
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onAddSetClicked) {
                Text("ADD SET ($restLabel)", color = NeonGreen, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}

// ── Completed set row ─────────────────────────────────────────────────────────

@Composable
private fun CompletedSetRow(
    set: SetLog,
    setIndex: Int,
    previousSet: SetLog?,
    onEdit: (Double, Int, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kgTfv    by remember(set.id, setIndex) { mutableStateOf(TextFieldValue(set.weightKg.fmtKg())) }
    var repsTfv  by remember(set.id, setIndex) { mutableStateOf(TextFieldValue("${set.reps}")) }
    var editFail by remember(set.id, setIndex) { mutableStateOf(set.isFailure) }
    val kgFocus   = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focusMgr  = LocalFocusManager.current

    val prevText = previousSet?.let {
        "${it.weightKg.fmtKg()} × ${it.reps}${if (it.isFailure) " [F]" else ""}"
    } ?: "—"

    fun confirm() {
        val kg = kgTfv.text.toDoubleOrNull() ?: set.weightKg
        val r  = repsTfv.text.toIntOrNull()  ?: set.reps
        onEdit(kg, r, editFail)
        focusMgr.clearFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth()
            .background(CompletedSetBg, RoundedCornerShape(4.dp))
            .pointerInput(onDelete) { detectTapGestures(onLongPress = { onDelete() }) }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SET — tap to toggle failure
        Text(
            if (editFail) "F" else "${set.setNumber}",
            color = if (editFail) DangerRed else Color.White,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp).clickable {
                val f = !editFail; editFail = f
                onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, repsTfv.text.toIntOrNull() ?: set.reps, f)
            }
        )
        Text(prevText, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        SetCell(
            value = kgTfv, onValueChange = { kgTfv = it },
            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
            onImeAction = { repsFocus.requestFocus() },
            focusRequester = kgFocus, modifier = Modifier.weight(1f)
        )
        SetCell(
            value = repsTfv, onValueChange = { repsTfv = it },
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
            onImeAction = { confirm() },
            focusRequester = repsFocus, modifier = Modifier.weight(0.8f)
        )
        // ✓ visual only
        Column(
            Modifier.width(44.dp).height(32.dp).clip(RoundedCornerShape(4.dp)).background(CompletedCheckBg),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) { Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black) }
    }
}

// ── Pending set row ───────────────────────────────────────────────────────────

@Composable
private fun PendingSetRow(
    setNumber: Int,
    previousSet: SetLog?,
    initialWeightKg: Double,
    initialReps: Int,
    onLogSet: (Double, Int, Float?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var kgTfv     by remember { mutableStateOf(TextFieldValue(initialWeightKg.fmtKg())) }
    var repsTfv   by remember { mutableStateOf(TextFieldValue("$initialReps")) }
    var isFailure by remember { mutableStateOf(false) }
    val kgFocus   = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focusMgr  = LocalFocusManager.current

    val prevText = previousSet?.let { "${it.weightKg.fmtKg()} × ${it.reps}" } ?: "—"

    fun logSet() {
        val kg = (kgTfv.text.toDoubleOrNull() ?: initialWeightKg).coerceAtLeast(0.0)
        val r  = (repsTfv.text.toIntOrNull()  ?: initialReps).coerceAtLeast(1)
        onLogSet(kg, r, null, isFailure)
        focusMgr.clearFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth()
            .background(PendingSetBg, RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SET — tap to toggle failure
        Text(
            if (isFailure) "F" else "$setNumber",
            color = if (isFailure) DangerRed else Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp).clickable { isFailure = !isFailure }
        )
        Text(prevText, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        SetCell(
            value = kgTfv, onValueChange = { kgTfv = it },
            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
            onImeAction = { repsFocus.requestFocus() },
            focusRequester = kgFocus, modifier = Modifier.weight(1f)
        )
        SetCell(
            value = repsTfv, onValueChange = { repsTfv = it },
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
            onImeAction = { logSet() },
            focusRequester = repsFocus, modifier = Modifier.weight(0.8f)
        )
        // ✓ — logs the set
        Column(
            Modifier.width(44.dp).height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NeonGreen.copy(alpha = 0.2f))
                .border(1.dp, NeonGreen.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .clickable { logSet() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) { Text("✓", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Black) }
    }
}

// ── Rest timer bar ────────────────────────────────────────────────────────────

@Composable
private fun RestTimerBar(
    secondsRemaining: Int,
    onSkipRest: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PowerAmber)) {
        Text(
            formatElapsed(secondsRemaining), color = Color.Black, fontSize = 28.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onAdjust(-30) }) { Text("-30s", color = Color.Black, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { onAdjust(30) })  { Text("+30s", color = Color.Black, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onSkipRest)        { Text("SKIP",  color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp) }
        }
    }
}

// ── Inline set cell (BasicTextField) ─────────────────────────────────────────

@Composable
private fun SetCell(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val source  = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)

    // Select all text the moment the field gains focus so first keystroke replaces it
    LaunchedEffect(focused) {
        if (focused) {
            val v = latestValue
            latestOnChange(v.copy(selection = TextRange(0, v.text.length)))
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .border(1.dp, if (focused) NeonGreen else Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .background(if (focused) NeonGreen.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        textStyle = TextStyle(
            color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
            onGo   = { onImeAction() }
        ),
        interactionSource = source,
        cursorBrush = SolidColor(NeonGreen)
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun RestLabel(label: String) =
    Text(label, color = RestLabelColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600; val m = (totalSeconds % 3600) / 60; val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatRestLabel(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return if (s == 0) "$m:00" else "%d:%02d".format(m, s)
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
internal fun ActiveWorkoutEmptyPreview() {
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(), elapsedSeconds = 42,
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
    val ex = Exercise(1, "Deadlift (Barbell)", ExerciseCategory.HINGE, listOf(MuscleGroup.ERECTOR_SPINAE), emptyList(), "", "")
    val log = ExerciseLog(1, ex, listOf(SetLog(1, 1, 150.0, 2), SetLog(2, 2, 160.0, 3)), 0)
    SaiyanTheme {
        ActiveWorkoutContent(
            uiState = ActiveWorkoutUiState(
                exerciseLogs = listOf(log),
                previousPerformance = mapOf(1 to listOf(SetLog(0, 1, 140.0, 3), SetLog(0, 2, 150.0, 3))),
                restTimerForExerciseId = 1, restTimerSecondsRemaining = 238,
                pendingSetCounts = mapOf(1 to 1)
            ),
            elapsedSeconds = 610,
            onViewHistory = {}, onAddExerciseClicked = {}, onExerciseSelected = {},
            onExercisePickerDismissed = {}, onAddSetClicked = {},
            onLogSet = { _, _, _, _, _ -> }, onDeleteSet = { _, _ -> },
            onSkipRest = {}, onAdjustRest = {}, onFinishWorkout = {}
        )
    }
}
