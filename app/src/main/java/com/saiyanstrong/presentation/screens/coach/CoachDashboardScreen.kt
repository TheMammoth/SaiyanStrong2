package com.saiyanstrong.presentation.screens.coach

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.AthleteSummary
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun CoachDashboardScreen(
    onBack: () -> Unit,
    onAthleteClick: (String) -> Unit,
    viewModel: CoachDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MatteBlack)
                .scanlineTexture()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←", color = NeonGreen, fontSize = 20.sp,
                    modifier = Modifier.clickable { onBack() }.padding(end = 16.dp)
                )
                Text(
                    "MY ATHLETES",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading athletes…", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error!!, color = DangerRed, fontSize = 13.sp)
                }
                uiState.athletes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No athletes yet. Share your invite code from Coach Mode settings.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.athletes) { athlete ->
                        AthleteSummaryCard(athlete) { onAthleteClick(athlete.athleteId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AthleteSummaryCard(athlete: AthleteSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(8.dp))
            .border(1.dp, if (athlete.isStale) DangerRed.copy(alpha = 0.4f) else NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                athlete.displayName ?: athlete.email ?: "Athlete",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            if (athlete.isStale) {
                Icon(Icons.Filled.Warning, contentDescription = "No recent session", tint = DangerRed, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            athlete.lastSessionDateMs?.let { "Last session ${daysAgoLabel(it)}" } ?: "No sessions logged yet",
            color = if (athlete.isStale) DangerRed else Color.White.copy(alpha = 0.5f), fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("THIS WEEK", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text(WeightFormatter.formatVolume(athlete.weeklyVolumeKg), color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("STAGE", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text(athlete.powerLevel.stage.label, color = PowerAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun daysAgoLabel(dateMs: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - dateMs)
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMs))
    }
}
