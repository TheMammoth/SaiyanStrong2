package com.saiyanstrong.presentation.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import com.saiyanstrong.presentation.components.ConfirmDialog
import com.saiyanstrong.presentation.components.RpeBottomSheet
import com.saiyanstrong.presentation.components.SetCell
import com.saiyanstrong.presentation.components.StepperRow
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
    val defaultRestSeconds by workoutViewModel.defaultRestSeconds.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completedSessionId) { uiState.completedSessionId?.let(onWorkoutFinished) }
    ActiveWorkoutContent(
        uiState = uiState, elapsedSeconds = elapsedSeconds,
        restDurationSeconds = defaultRestSeconds,
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
        onFinishWorkout = workoutViewModel::onFinishWorkout,
        onRemoveExercise = workoutViewModel::onRemoveExercise,
        onSetExerciseRestTimer = workoutViewModel::onSetExerciseRestTimer
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
    onEditSet: (Int, Int, Double, Int, Float?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteSet: (Int, Int) -> Unit,
    onSkipRest: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onFinishWorkout: () -> Unit,
    onRemoveExercise: (Int) -> Unit = {},
    onSetExerciseRestTimer: (Int, Int?) -> Unit = { _, _ -> }
) {
    val totalSets = uiState.exerciseLogs.sumOf { it.sets.size }
    val totalVolumeKg = uiState.exerciseLogs.sumOf { log -> log.sets.sumOf { it.volumeKg } }
    val elapsed   = formatElapsed(elapsedSeconds)

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
            val workoutTitle = remember { timeOfDayWorkoutTitle() }
            Column(Modifier.fillMaxWidth().background(SaiyanGray).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(workoutTitle, color = Color.White,
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
                        restTimerTotalSeconds = uiState.restTimerTotalSeconds,
                        restLabel = formatRestLabel(log.exercise.restTimerSec ?: restDurationSeconds),
                        defaultRestSeconds = restDurationSeconds,
                        onAddSetClicked = { onAddSetClicked(log.exercise.id) },
                        onLogSet = { kg, reps, rpe, fail -> onLogSet(log.exercise.id, kg, reps, rpe, fail) },
                        onEditSet = { idx, kg, reps, rpe, fail -> onEditSet(log.exercise.id, idx, kg, reps, rpe, fail) },
                        onDeleteSet = { idx -> onDeleteSet(log.exercise.id, idx) },
                        onSkipRest = onSkipRest,
                        onAdjustRest = onAdjustRest,
                        onRemoveExercise = { onRemoveExercise(log.exercise.id) },
                        onSetRestTimer = { seconds -> onSetExerciseRestTimer(log.exercise.id, seconds) }
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
    restTimerTotalSeconds: Int = 90,
    restLabel: String = "1:30",
    defaultRestSeconds: Int = 90,
    onAddSetClicked: () -> Unit = {},
    onLogSet: (Double, Int, Float?, Boolean) -> Unit = { _, _, _, _ -> },
    onEditSet: (Int, Double, Int, Float?, Boolean) -> Unit = { _, _, _, _, _ -> },
    onDeleteSet: (Int) -> Unit = {},
    onSkipRest: () -> Unit = {},
    onAdjustRest: (Int) -> Unit = {},
    onRemoveExercise: () -> Unit = {},
    onSetRestTimer: (Int?) -> Unit = {}
) {
    val lastWeight = exerciseLog.sets.lastOrNull()?.weightKg ?: 60.0
    val lastReps   = exerciseLog.sets.lastOrNull()?.reps ?: 5
    val stepKg = if (exerciseLog.exercise.name.contains("Dumbbell", true) ||
        exerciseLog.exercise.name.contains("Kettlebell", true)) 2.0 else 2.5

    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showRestDialog by remember { mutableStateOf(false) }

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
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("REST TIMER ($restLabel)", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Timer, null, tint = PowerAmber, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; showRestDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("REMOVE EXERCISE", color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerRed, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; showRemoveConfirm = true }
                    )
                }
            }
        }

        if (showRemoveConfirm) {
            ConfirmDialog(
                title = "REMOVE EXERCISE?",
                message = "${exerciseLog.exercise.name} and its ${exerciseLog.sets.size} logged set${if (exerciseLog.sets.size != 1) "s" else ""} will be removed from this workout.",
                confirmLabel = "REMOVE",
                onConfirm = onRemoveExercise,
                onDismiss = { showRemoveConfirm = false }
            )
        }

        if (showRestDialog) {
            RestTimerDialog(
                exerciseName = exerciseLog.exercise.name,
                currentSeconds = exerciseLog.exercise.restTimerSec,
                defaultSeconds = defaultRestSeconds,
                onSave = { seconds -> onSetRestTimer(seconds) },
                onDismiss = { showRestDialog = false }
            )
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
                stepKg = stepKg,
                onEdit = { kg, reps, rpe, fail -> onEditSet(idx, kg, reps, rpe, fail) },
                onDelete = { onDeleteSet(idx) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
            when {
                idx < exerciseLog.sets.size - 1 -> RestLabel(restLabel)
                restTimerSecondsRemaining != null ->
                    RestTimerBar(restTimerSecondsRemaining, restTimerTotalSeconds,
                        onSkipRest = onSkipRest, onAdjust = onAdjustRest,
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
                stepKg = stepKg,
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
    stepKg: Double = 2.5,
    onEdit: (Double, Int, Float?, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var kgTfv    by remember(set.id, setIndex) { mutableStateOf(TextFieldValue(set.weightKg.fmtKg())) }
    var repsTfv  by remember(set.id, setIndex) { mutableStateOf(TextFieldValue("${set.reps}")) }
    var editFail by remember(set.id, setIndex) { mutableStateOf(set.isFailure) }
    var editRpe  by remember(set.id, setIndex) { mutableStateOf(set.rpe) }
    var focusedCell by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRpeSheet by remember { mutableStateOf(false) }
    val kgFocus   = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focusMgr  = LocalFocusManager.current

    val prevText = previousSet?.let {
        "${it.weightKg.fmtKg()} × ${it.reps}${if (it.isFailure) " [F]" else ""}"
    } ?: "—"

    fun confirm() {
        val kg = kgTfv.text.toDoubleOrNull() ?: set.weightKg
        val r  = repsTfv.text.toIntOrNull()  ?: set.reps
        onEdit(kg, r, editRpe, editFail)
        focusMgr.clearFocus()
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "DELETE SET ${set.setNumber}?",
            message = "${set.weightKg.fmtKg()} kg × ${set.reps} will be removed.",
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showRpeSheet) {
        RpeBottomSheet(
            currentRpe = editRpe,
            onSelect = { value ->
                editRpe = value
                onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, repsTfv.text.toIntOrNull() ?: set.reps, value, editFail)
                showRpeSheet = false
            },
            onDismiss = { showRpeSheet = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(CompletedSetBg, RoundedCornerShape(4.dp))
                .pointerInput(Unit) { detectTapGestures(onLongPress = { showDeleteConfirm = true }) }
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
                    onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, repsTfv.text.toIntOrNull() ?: set.reps, editRpe, f)
                }
            )
            Text(prevText, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.weight(1.2f))
            SetCell(
                value = kgTfv, onValueChange = { kgTfv = it },
                keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next,
                onImeAction = { repsFocus.requestFocus() },
                focusRequester = kgFocus,
                onFocusChanged = { if (it) focusedCell = "kg" else if (focusedCell == "kg") focusedCell = null },
                modifier = Modifier.weight(1f)
            )
            SetCell(
                value = repsTfv, onValueChange = { repsTfv = it },
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onImeAction = { confirm() },
                focusRequester = repsFocus,
                onFocusChanged = { if (it) focusedCell = "reps" else if (focusedCell == "reps") focusedCell = null },
                modifier = Modifier.weight(0.8f)
            )
            // ✓ visual only
            Column(
                Modifier.width(44.dp).height(32.dp).clip(RoundedCornerShape(4.dp)).background(CompletedCheckBg),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) { Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black) }
        }

        RpeChip(rpe = editRpe, onClick = { showRpeSheet = true })

        when (focusedCell) {
            "kg" -> StepperRow(stepLabel = stepKg.fmtKg(), onStep = { direction ->
                val kg = ((kgTfv.text.toDoubleOrNull() ?: set.weightKg) + direction * stepKg).coerceAtLeast(0.0)
                kgTfv = TextFieldValue(kg.fmtKg())
                onEdit(kg, repsTfv.text.toIntOrNull() ?: set.reps, editRpe, editFail)
            })
            "reps" -> StepperRow(stepLabel = "1", onStep = { direction ->
                val reps = ((repsTfv.text.toIntOrNull() ?: set.reps) + direction).coerceAtLeast(1)
                repsTfv = TextFieldValue("$reps")
                onEdit(kgTfv.text.toDoubleOrNull() ?: set.weightKg, reps, editRpe, editFail)
            })
        }
    }
}

// ── Pending set row ───────────────────────────────────────────────────────────

@Composable
private fun PendingSetRow(
    setNumber: Int,
    previousSet: SetLog?,
    initialWeightKg: Double,
    initialReps: Int,
    stepKg: Double = 2.5,
    onLogSet: (Double, Int, Float?, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var kgTfv     by remember { mutableStateOf(TextFieldValue(initialWeightKg.fmtKg())) }
    var repsTfv   by remember { mutableStateOf(TextFieldValue("$initialReps")) }
    var isFailure by remember { mutableStateOf(false) }
    var rpe       by remember { mutableStateOf<Float?>(null) }
    var focusedCell by remember { mutableStateOf<String?>(null) }
    var showRpeSheet by remember { mutableStateOf(false) }
    val kgFocus   = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    val focusMgr  = LocalFocusManager.current

    val prevText = previousSet?.let { "${it.weightKg.fmtKg()} × ${it.reps}" } ?: "—"

    fun logSet() {
        val kg = (kgTfv.text.toDoubleOrNull() ?: initialWeightKg).coerceAtLeast(0.0)
        val r  = (repsTfv.text.toIntOrNull()  ?: initialReps).coerceAtLeast(1)
        onLogSet(kg, r, rpe, isFailure)
        focusMgr.clearFocus()
    }

    if (showRpeSheet) {
        RpeBottomSheet(
            currentRpe = rpe,
            onSelect = { value -> rpe = value; showRpeSheet = false },
            onDismiss = { showRpeSheet = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
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
                focusRequester = kgFocus,
                onFocusChanged = { if (it) focusedCell = "kg" else if (focusedCell == "kg") focusedCell = null },
                modifier = Modifier.weight(1f)
            )
            SetCell(
                value = repsTfv, onValueChange = { repsTfv = it },
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onImeAction = { logSet() },
                focusRequester = repsFocus,
                onFocusChanged = { if (it) focusedCell = "reps" else if (focusedCell == "reps") focusedCell = null },
                modifier = Modifier.weight(0.8f)
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

        RpeChip(rpe = rpe, onClick = { showRpeSheet = true })

        when (focusedCell) {
            "kg" -> StepperRow(stepLabel = stepKg.fmtKg(), onStep = { direction ->
                val kg = ((kgTfv.text.toDoubleOrNull() ?: initialWeightKg) + direction * stepKg).coerceAtLeast(0.0)
                kgTfv = TextFieldValue(kg.fmtKg())
            })
            "reps" -> StepperRow(stepLabel = "1", onStep = { direction ->
                val reps = ((repsTfv.text.toIntOrNull() ?: initialReps) + direction).coerceAtLeast(1)
                repsTfv = TextFieldValue("$reps")
            })
        }
    }
}

// ── RPE chip (tap to open RpeBottomSheet) ────────────────────────────────────

@Composable
private fun RpeChip(rpe: Float?, onClick: () -> Unit) {
    val label = rpe?.let { if (it == it.toInt().toFloat()) "RPE ${it.toInt()}" else "RPE $it" } ?: "+ RPE"
    Text(
        label,
        color = if (rpe != null) NeonGreen else Color.White.copy(alpha = 0.35f),
        fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 2.dp, start = 30.dp).clickable(onClick = onClick)
    )
}

// ── Rest timer bar ────────────────────────────────────────────────────────────

@Composable
private fun RestTimerBar(
    secondsRemaining: Int,
    totalSeconds: Int,
    onSkipRest: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction = secondsRemaining.toFloat() / totalSeconds.coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        // Slim draining bar with the countdown centered on top (Strong-style)
        Box(
            Modifier.fillMaxWidth().height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SaiyanGray)
                .border(1.dp, PowerAmber.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(PowerAmber)
            )
            Text(
                formatElapsed(secondsRemaining),
                color = if (fraction > 0.45f) Color.Black else PowerAmber,
                fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onAdjust(-30) }) { Text("-30s", color = PowerAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            TextButton(onClick = { onAdjust(30) })  { Text("+30s", color = PowerAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            TextButton(onClick = onSkipRest)        { Text("SKIP",  color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        }
    }
}

// ── Per-exercise rest timer dialog ───────────────────────────────────────────

@Composable
private fun RestTimerDialog(
    exerciseName: String,
    currentSeconds: Int?,
    defaultSeconds: Int,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var seconds by remember { mutableStateOf(currentSeconds ?: defaultSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SaiyanGray,
        titleContentColor = Color.White,
        title = { Text("REST TIMER", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(exerciseName, color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { seconds = (seconds - 15).coerceAtLeast(10) }) {
                        Text("−15s", color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    Text(
                        formatRestLabel(seconds),
                        color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = { seconds = (seconds + 15).coerceAtMost(600) }) {
                        Text("+15s", color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
                Text(
                    "default: ${formatRestLabel(defaultSeconds)} (Settings)",
                    color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(seconds); onDismiss() }) {
                Text("SAVE", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(null); onDismiss() }) {
                Text("USE DEFAULT", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
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

private fun timeOfDayWorkoutTitle(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5   -> "NIGHT WORKOUT"
        hour < 12  -> "MORNING WORKOUT"
        hour < 17  -> "AFTERNOON WORKOUT"
        else       -> "EVENING WORKOUT"
    }
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
