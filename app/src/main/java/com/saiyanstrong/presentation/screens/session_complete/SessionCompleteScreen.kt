package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.components.ConfirmDialog
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionCompleteScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SessionCompleteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exerciseRows by viewModel.exerciseRows.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isDone) { if (uiState.isDone) onDone() }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }
    SessionCompleteContent(
        session = uiState.session,
        powerLevel = uiState.powerLevel,
        exerciseRows = exerciseRows,
        titleInput = uiState.titleInput,
        isTemplateSaved = uiState.isTemplateSaved,
        onTitleChange = viewModel::onTitleChange,
        onDone = viewModel::onDone,
        onDeleteSession = viewModel::onDeleteSession,
        onSaveAsTemplate = viewModel::onSaveAsTemplate
    )
}

@Composable
internal fun SessionCompleteContent(
    session: WorkoutSession?,
    powerLevel: PowerLevel?,
    exerciseRows: List<ExerciseRow>,
    titleInput: String,
    isTemplateSaved: Boolean = false,
    onTitleChange: (String) -> Unit,
    onDone: () -> Unit,
    onDeleteSession: () -> Unit,
    onSaveAsTemplate: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteBlack)
            .scanlineTexture()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Header ──────────────────────────────────────────────
            Text(
                "SESSION COMPLETE",
                color = PowerAmber,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                session?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.dateMs)) } ?: "",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // ── Volume hero ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray, RoundedCornerShape(6.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TOTAL VOLUME", color = TelemetryGreen, fontSize = 10.sp,
                        letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        session?.let { WeightFormatter.formatVolume(it.totalVolumeKg) } ?: "—",
                        color = NeonGreen, fontSize = 32.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("POWER EARNED", color = TelemetryGreen, fontSize = 9.sp,
                        letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        "+${session?.powerEarned ?: 0}",
                        color = PowerAmber, fontSize = 22.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Stat tiles ──────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompleteStatTile("DURATION",
                    session?.let { formatDuration(it.durationMs) } ?: "—", Modifier.weight(1f))
                CompleteStatTile("SETS",
                    "${session?.exerciseLogs?.sumOf { it.sets.size } ?: 0}", Modifier.weight(1f))
                CompleteStatTile("EXERCISES",
                    "${session?.exerciseLogs?.size ?: 0}", Modifier.weight(1f))
            }

            // ── Power stage ─────────────────────────────────────────
            powerLevel?.let { level ->
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(level.stage.label.uppercase(), color = PowerAmber, fontSize = 12.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                            modifier = Modifier.weight(1f))
                        Text("%,d PWR".format(level.current), color = NeonGreen, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(8.dp))
                    StageProgressBar(
                        progress = level.progressToNext,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
            }

            // ── Results ─────────────────────────────────────────────
            if (exerciseRows.isNotEmpty()) {
                Text(
                    "RESULTS",
                    color = TelemetryGreen, fontSize = 10.sp, letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp)
                ) {
                    exerciseRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.name, color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${row.totalSets} sets · ${row.totalReps} reps",
                                    color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("BEST ${WeightFormatter.format(row.bestWeightKg)}",
                                    color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace)
                                Text("e1RM ${WeightFormatter.formatOneRm(row.estOneRmKg)}",
                                    color = PowerAmber, fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Title + template ────────────────────────────────────
            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleChange,
                label = { Text("Session title (optional)", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonGreen,
                    focusedLabelColor = NeonGreen,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSaveAsTemplate,
                enabled = !isTemplateSaved,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PowerAmber,
                    disabledContentColor = NeonGreen
                ),
                border = BorderStroke(1.dp, if (isTemplateSaved) NeonGreen.copy(alpha = 0.4f) else PowerAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isTemplateSaved) "TEMPLATE SAVED ✓" else "SAVE AS TEMPLATE",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── Pinned actions ──────────────────────────────────────────
        var showDeleteConfirm by remember { mutableStateOf(false) }
        if (showDeleteConfirm) {
            ConfirmDialog(
                title = "DELETE SESSION?",
                message = "This workout and all its sets will be permanently deleted.",
                onConfirm = onDeleteSession,
                onDismiss = { showDeleteConfirm = false }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                border = BorderStroke(1.dp, DangerRed),
                modifier = Modifier.weight(0.35f)
            ) { Text("DELETE", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            SaiyanButton(onClick = onDone, modifier = Modifier.weight(0.65f)) {
                Text("DONE  >>>", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }

        session?.let {
            Text(
                "// EX:${it.exerciseLogs.size}  SETS:${it.exerciseLogs.sumOf { log -> log.sets.size }}  +${it.powerEarned} PWR //",
                color = TelemetryGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun CompleteStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TelemetryGreen, fontSize = 9.sp, letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace)
        Text(value, color = NeonGreen, fontSize = 15.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun StageProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = MatteBlack, cornerRadius = radius)
        val width = size.width * progress.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(
                color = PowerAmber,
                size = Size(width, size.height),
                cornerRadius = radius
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60)
    else "%dm %02ds".format(s / 60, s % 60)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
internal fun SessionCompletePreview() {
    SaiyanTheme {
        SessionCompleteContent(
            session = WorkoutSession(
                id = 1, dateMs = 1_700_000_000_000, durationMs = 3_720_000,
                exerciseLogs = emptyList(), totalVolumeKg = 4250.0, powerEarned = 612
            ),
            powerLevel = PowerLevel(
                current = 14_500, stage = SaiyanStage.SSJ1,
                nextStageThreshold = 50_000, progressToNext = 0.7f
            ),
            exerciseRows = listOf(
                ExerciseRow("Bench Press (Barbell)", 100.0, 112.5, 45, 4),
                ExerciseRow("Squat (Barbell)", 140.0, 155.0, 36, 3),
                ExerciseRow("Deadlift", 160.0, 176.0, 20, 2)
            ),
            titleInput = "",
            onTitleChange = {},
            onDone = {},
            onDeleteSession = {}
        )
    }
}
