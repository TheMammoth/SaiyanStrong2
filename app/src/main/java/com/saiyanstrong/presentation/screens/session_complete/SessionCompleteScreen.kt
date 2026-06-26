package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
                Text(
                    "POWER LEVEL ACQUIRED",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (session != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatCard(
                                label = "VOLUME",
                                value = WeightFormatter.formatVolume(session.totalVolumeKg),
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "POWER",
                                value = "+${session.powerEarned}",
                                valueColor = PowerAmber,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "TIME",
                                value = formatDuration(session.durationMs),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    powerLevel?.let {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SaiyanGray, RoundedCornerShape(8.dp))
                                .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                "POWER LEVEL",
                                color = TelemetryGreen,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            PowerLevelBar(powerLevel = it)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = onTitleChange,
                        label = { Text("Session title (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                session?.exerciseLogs?.takeIf { it.isNotEmpty() }?.let {
                    item {
                        Text(
                            "EXERCISE BREAKDOWN",
                            color = TelemetryGreen,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    items(it, key = { log -> log.exercise.id }) { exerciseLog ->
                        ExerciseResultCard(exerciseLog)
                    }
                }
            }

            SaiyanButton(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("DONE  >>>", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = onDeleteSession,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                border = BorderStroke(1.dp, DangerRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("DELETE SESSION", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (session != null) {
                Text(
                    "// EXERCISES: ${session.exerciseLogs.size}  |  SETS: ${session.exerciseLogs.sumOf { it.sets.size }} //",
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
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TelemetryGreen, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ExerciseResultCard(exerciseLog: ExerciseLog) {
    val bestSet = exerciseLog.sets.maxByOrNull { it.volumeKg }
    val estimated1Rm = bestSet?.let {
        if (it.reps == 1) it.weightKg else it.weightKg * (1.0 + it.reps / 30.0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                exerciseLog.exercise.name.uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            if (estimated1Rm != null) {
                Text(
                    "1RM ~${WeightFormatter.formatOneRm(estimated1Rm)}",
                    color = PowerAmber,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        exerciseLog.sets.forEach { set ->
            Text(
                "SET ${set.setNumber}  ${WeightFormatter.format(set.weightKg)} x ${set.reps}",
                color = TelemetryGreen,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Preview(showBackground = true)
@PreviewLightDark
@Composable
internal fun SessionCompleteContentPreview() {
    SaiyanTheme {
        SessionCompleteContent(
            session = WorkoutSession(
                id = 1, dateMs = 0, durationMs = 3_600_000,
                exerciseLogs = emptyList(), totalVolumeKg = 4250.0, powerEarned = 612
            ),
            powerLevel = PowerLevel(
                current = 9_613, stage = SaiyanStage.BASE,
                nextStageThreshold = 20_000, progressToNext = 0.48f
            ),
            titleInput = "",
            onTitleChange = {},
            onDone = {},
            onDeleteSession = {}
        )
    }
}
