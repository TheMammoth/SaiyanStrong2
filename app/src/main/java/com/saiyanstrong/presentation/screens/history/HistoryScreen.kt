package com.saiyanstrong.presentation.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryContent(
        sessions = uiState.sessions,
        onSessionClick = onSessionClick,
        onBack = onBack
    )
}

@Composable
private fun HistoryContent(
    sessions: List<WorkoutSession>,
    onSessionClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("History", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack) { Text("Back") }
            }

            if (sessions.isEmpty()) {
                Text("No sessions logged yet.", modifier = Modifier.padding(top = 16.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session = session, onClick = { onSessionClick(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkoutSession, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            DateFormat.getDateInstance().format(Date(session.dateMs)),
            style = MaterialTheme.typography.titleMedium
        )
        Text("${WeightFormatter.formatVolume(session.totalVolumeKg)} · +${session.powerEarned} power")
    }
}

@PreviewLightDark
@Composable
private fun HistoryContentPreview() {
    SaiyanTheme {
        HistoryContent(
            sessions = listOf(
                WorkoutSession(
                    id = 1,
                    dateMs = System.currentTimeMillis(),
                    durationMs = 3_600_000,
                    exerciseLogs = emptyList(),
                    totalVolumeKg = 4250.0,
                    powerEarned = 612
                )
            ),
            onSessionClick = {},
            onBack = {}
        )
    }
}
