package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.components.PowerLevelBar
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.util.WeightFormatter

@Composable
fun SessionCompleteScreen(
    onDone: () -> Unit,
    viewModel: SessionCompleteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionCompleteContent(
        session = uiState.session,
        powerLevel = uiState.powerLevel,
        onDone = onDone
    )
}

@Composable
private fun SessionCompleteContent(
    session: WorkoutSession?,
    powerLevel: PowerLevel?,
    onDone: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Workout Complete", style = MaterialTheme.typography.headlineMedium)

            if (session != null) {
                Text("Total Volume: ${WeightFormatter.formatVolume(session.totalVolumeKg)}")
                Text("Power Earned: +${session.powerEarned}", color = MaterialTheme.colorScheme.secondary)
            }

            powerLevel?.let { PowerLevelBar(powerLevel = it) }

            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@PreviewLightDark
@Composable
private fun SessionCompleteContentPreview() {
    SaiyanTheme {
        SessionCompleteContent(
            session = WorkoutSession(
                id = 1,
                dateMs = 0,
                durationMs = 3_600_000,
                exerciseLogs = emptyList(),
                totalVolumeKg = 4250.0,
                powerEarned = 612
            ),
            powerLevel = PowerLevel(
                current = 9_613,
                stage = SaiyanStage.BASE,
                nextStageThreshold = 20_000,
                progressToNext = 0.48f
            ),
            onDone = {}
        )
    }
}
