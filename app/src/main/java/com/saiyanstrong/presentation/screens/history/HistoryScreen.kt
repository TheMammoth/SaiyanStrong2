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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import com.saiyanstrong.util.WeightFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Title + full date
        Text(
            session.title.ifBlank { "Workout" },
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
                .format(Date(session.dateMs)),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp
        )

        // Sets | Best set columns
        if (session.exerciseLogs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                Text("Sets", color = TelemetryGreen, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.58f))
                Text("Best set", color = TelemetryGreen, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
            }
            session.exerciseLogs.forEach { log ->
                val bestSet = log.sets.maxByOrNull { s ->
                    if (s.reps == 1) s.weightKg else s.weightKg * (1.0 + s.reps / 30.0)
                }
                if (bestSet != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            "${log.sets.size} × ${log.exercise.name}",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.58f)
                        )
                        Text(
                            "${WeightFormatter.format(bestSet.weightKg)} × ${bestSet.reps}" +
                                if (bestSet.isFailure) " [F]" else "",
                            color = if (bestSet.isFailure) DangerRed else Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f)
                        )
                    }
                }
            }
        }

        // Footer chips: duration · volume · PRs
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterStat(Icons.Default.Schedule, formatDuration(session.durationMs))
            FooterStat(Icons.Default.FitnessCenter, WeightFormatter.formatVolume(session.totalVolumeKg))
            FooterStat(
                Icons.Default.EmojiEvents,
                "$prCount PR${if (prCount != 1) "s" else ""}",
                tint = if (prCount > 0) PowerAmber else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun FooterStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = Color.White.copy(alpha = 0.55f)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp))
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else "${s / 60}m"
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
