package com.saiyanstrong.presentation.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.presentation.components.PowerLevelBar
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val powerLevel by viewModel.powerLevel.collectAsStateWithLifecycle()
    val weeklyBars by viewModel.weeklyBars.collectAsStateWithLifecycle()
    HomeContent(
        powerLevel = powerLevel,
        weeklyBars = weeklyBars,
        onStartWorkout = onStartWorkout,
        onViewHistory = onViewHistory
    )
}

@Composable
internal fun HomeContent(
    powerLevel: PowerLevel?,
    weeklyBars: List<WeekBar>,
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit
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
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(
                    "SAIYAN STRONG",
                    color = PowerAmber,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    powerLevel?.stage?.label?.uppercase() ?: "INITIALIZING...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            powerLevel?.let {
                PowerLevelBar(
                    powerLevel = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            if (weeklyBars.isNotEmpty()) {
                Text(
                    "WORKOUTS / WEEK",
                    color = TelemetryGreen,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                WorkoutsPerWeekChart(
                    weekBars = weeklyBars,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaiyanButton(
                    onClick = onStartWorkout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "BEGIN TRAINING",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                SaiyanButton(
                    onClick = onViewHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SESSION HISTORY", letterSpacing = 1.sp)
                }
            }

            Text(
                "// POWER LEVEL: ${powerLevel?.current ?: "---"} //",
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

@Composable
private fun WorkoutsPerWeekChart(weekBars: List<WeekBar>, modifier: Modifier = Modifier) {
    val maxCount = weekBars.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            val slotWidth = size.width / weekBars.size
            val barWidth = slotWidth * 0.55f
            val barMargin = slotWidth * 0.225f

            weekBars.forEachIndexed { i, bar ->
                val barHeight = (bar.count.toFloat() / maxCount) * size.height
                if (barHeight > 0f) {
                    drawRect(
                        color = NeonGreen,
                        topLeft = Offset(i * slotWidth + barMargin, size.height - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                } else {
                    // Zero-height bar: draw a 2px floor line
                    drawRect(
                        color = NeonGreen.copy(alpha = 0.2f),
                        topLeft = Offset(i * slotWidth + barMargin, size.height - 2f),
                        size = Size(barWidth, 2f)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            weekBars.forEach { bar ->
                Text(
                    bar.label,
                    color = TelemetryGreen.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Preview(showBackground = true)
@PreviewLightDark
@Composable
internal fun HomeContentPreview() {
    SaiyanTheme {
        HomeContent(
            powerLevel = PowerLevel(
                current = 9_613,
                stage = SaiyanStage.BASE,
                nextStageThreshold = 20_000,
                progressToNext = 0.48f
            ),
            weeklyBars = listOf(
                WeekBar("6/2", 3), WeekBar("6/9", 5), WeekBar("6/16", 2),
                WeekBar("6/23", 4), WeekBar("6/30", 1), WeekBar("7/7", 3),
                WeekBar("7/14", 0), WeekBar("7/21", 2)
            ),
            onStartWorkout = {},
            onViewHistory = {}
        )
    }
}
