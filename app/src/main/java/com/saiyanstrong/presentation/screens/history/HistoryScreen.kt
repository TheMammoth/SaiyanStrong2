package com.saiyanstrong.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
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
        items = uiState.items,
        onSessionClick = onSessionClick,
        onDeleteSession = viewModel::deleteSession,
        onBack = onBack
    )
}

@Composable
internal fun HistoryContent(
    items: List<HistoryItem>,
    onSessionClick: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HISTORY",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PowerAmber,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "< BACK",
                    color = NeonGreen,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp)
                )
            }

            if (items.isEmpty()) {
                Text("NO SESSIONS LOGGED YET.", color = TelemetryGreen, modifier = Modifier.padding(top = 16.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { item ->
                    when (item) {
                        is HistoryItem.MonthHeader -> "header_${item.label}"
                        is HistoryItem.SessionCard -> "session_${item.session.id}"
                    }
                }) { item ->
                    when (item) {
                        is HistoryItem.MonthHeader -> MonthHeaderRow(item)
                        is HistoryItem.SessionCard -> SwipeableSessionRow(
                            session = item.session,
                            prCount = item.prCount,
                            onClick = { onSessionClick(item.session.id) },
                            onDelete = { onDeleteSession(item.session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(header: HistoryItem.MonthHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            header.label.uppercase(),
            color = TelemetryGreen,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            "${header.count} SESSION${if (header.count != 1) "S" else ""}",
            color = TelemetryGreen.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableSessionRow(
    session: WorkoutSession,
    prCount: Int = 0,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.4f }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DangerRed)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("DELETE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        SessionCard(session = session, prCount = prCount, onClick = onClick)
    }
}

@Composable
internal fun SessionCard(session: WorkoutSession, prCount: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(4.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                session.title.ifBlank { DateFormat.getDateInstance().format(Date(session.dateMs)) },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (prCount > 0) {
                Box(
                    modifier = Modifier
                        .background(PowerAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, PowerAmber, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("$prCount PR${if (prCount > 1) "s" else ""}", color = PowerAmber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "${WeightFormatter.formatVolume(session.totalVolumeKg)}  ·  +${session.powerEarned} power",
            color = TelemetryGreen,
            style = MaterialTheme.typography.bodySmall
        )

        session.exerciseLogs.take(3).forEach { log ->
            val bestSet = log.sets.maxByOrNull { s ->
                if (s.reps == 1) s.weightKg else s.weightKg * (1.0 + s.reps / 30.0)
            }
            if (bestSet != null) {
                Text(
                    "  ${log.exercise.name} — ${WeightFormatter.format(bestSet.weightKg)} × ${bestSet.reps}",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (session.exerciseLogs.size > 3) {
            Text(
                "  +${session.exerciseLogs.size - 3} more",
                color = Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true)
@PreviewLightDark
@Composable
internal fun HistoryContentPreview() {
    SaiyanTheme {
        HistoryContent(
            items = listOf(
                HistoryItem.MonthHeader("June 2026", 3),
                HistoryItem.SessionCard(
                    WorkoutSession(1, System.currentTimeMillis(), 3_600_000, emptyList(), 4250.0, 612),
                    prCount = 2
                )
            ),
            onSessionClick = {},
            onDeleteSession = {},
            onBack = {}
        )
    }
}
