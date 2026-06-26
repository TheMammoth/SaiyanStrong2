package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.components.PowerLevelBar
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
fun SessionCompleteScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SessionCompleteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isDone) { if (uiState.isDone) onDone() }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }
    SessionCompleteContent(
        session = uiState.session,
        powerLevel = uiState.powerLevel,
        titleInput = uiState.titleInput,
        onTitleChange = viewModel::onTitleChange,
        onDone = viewModel::onDone,
        onDeleteSession = viewModel::onDeleteSession
    )
}

@Composable
internal fun SessionCompleteContent(
    session: WorkoutSession?,
    powerLevel: PowerLevel?,
    titleInput: String,
    onTitleChange: (String) -> Unit,
    onDone: () -> Unit,
    onDeleteSession: () -> Unit
) {
    val hudCardColors = CardDefaults.outlinedCardColors(containerColor = SaiyanGray)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .scanlineTexture()
                .padding(padding)
        ) {
            // ── Header ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "SAIYAN STRONG",
                    color = NeonGreen,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    "SESSION COMPLETE!",
                    color = NeonGreen.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Hero Row: Volume Card | Power Bar ──────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: volume + max lifts card
                        OutlinedCard(
                            colors = hudCardColors,
                                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "TOTAL VOLUME",
                                    color = TelemetryGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    if (session != null) WeightFormatter.formatVolume(session.totalVolumeKg) else "—",
                                    color = NeonGreen,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(8.dp))
                                session?.exerciseLogs?.forEach { log ->
                                    val best = log.sets.maxByOrNull { it.weightKg }
                                    if (best != null) {
                                        HudDataRow(
                                            label = log.exercise.name.take(14),
                                            value = "${WeightFormatter.format(best.weightKg)} ×${best.reps}"
                                        )
                                    }
                                }
                                if (session != null) {
                                    Spacer(Modifier.height(4.dp))
                                    HudDataRow("SETS", "${session.exerciseLogs.sumOf { it.sets.size }}")
                                    HudDataRow("DURATION", formatDuration(session.durationMs))
                                }
                            }
                        }

                        // Right: power level bar
                        powerLevel?.let {
                            Column(
                                modifier = Modifier.padding(start = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PowerLevelBar(powerLevel = it)
                            }
                        }
                    }
                }

                // ── Stat chips row ──────────────────────────────
                if (session != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatChip("POWER", "+${session.powerEarned}", PowerAmber, Modifier.weight(1f))
                            StatChip("TIME", formatDuration(session.durationMs), Color.White, Modifier.weight(1f))
                            StatChip("EXERCISES", "${session.exerciseLogs.size}", TelemetryGreen, Modifier.weight(1f))
                        }
                    }
                }

                // ── 1RM + breakdown side-by-side ───────────────
                if (session != null && session.exerciseLogs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Estimated 1RM card
                            OutlinedCard(
                                colors = hudCardColors,
                                                    shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("EST. 1RM", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                                    Spacer(Modifier.height(6.dp))
                                    session.exerciseLogs.forEach { log ->
                                        val best = log.sets.maxByOrNull { it.weightKg }
                                        val oneRm = best?.let {
                                            if (it.reps == 1) it.weightKg
                                            else it.weightKg * (1.0 + it.reps / 30.0)
                                        }
                                        HudDataRow(
                                            label = log.exercise.name.take(12),
                                            value = if (oneRm != null) WeightFormatter.formatOneRm(oneRm) else "—"
                                        )
                                    }
                                }
                            }

                            // Sets breakdown card
                            OutlinedCard(
                                colors = hudCardColors,
                                                    shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("SETS LOG", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                                    Spacer(Modifier.height(6.dp))
                                    session.exerciseLogs.forEach { log ->
                                        HudDataRow(
                                            label = log.exercise.name.take(12),
                                            value = "${log.sets.size} sets"
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val totalVol = session.exerciseLogs.sumOf { it.sets.sumOf { s -> s.volumeKg } }
                                    HudDataRow("VOLUME", WeightFormatter.formatVolume(totalVol))
                                }
                            }
                        }
                    }
                }

                // ── Session title ───────────────────────────────
                item {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = onTitleChange,
                        label = { Text("Session title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Actions ─────────────────────────────────────────
            SaiyanButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("DONE  >>>", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = onDeleteSession,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                border = BorderStroke(1.dp, DangerRed),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("DELETE SESSION", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (session != null) {
                Text(
                    "// EXERCISES: ${session.exerciseLogs.size}  |  SETS: ${session.exerciseLogs.sumOf { it.sets.size }}  |  +${session.powerEarned} POWER //",
                    color = TelemetryGreen,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HudDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@PreviewLightDark
@Composable
internal fun SessionCompletePreview() {
    SaiyanTheme {
        SessionCompleteContent(
            session = WorkoutSession(
                id = 1, dateMs = 0, durationMs = 3_720_000,
                exerciseLogs = emptyList(), totalVolumeKg = 4250.0, powerEarned = 612
            ),
            powerLevel = PowerLevel(
                current = 14_500, stage = SaiyanStage.SSJ1,
                nextStageThreshold = 50_000, progressToNext = 0.7f
            ),
            titleInput = "",
            onTitleChange = {},
            onDone = {},
            onDeleteSession = {}
        )
    }
}
