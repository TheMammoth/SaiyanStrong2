package com.saiyanstrong.presentation.screens.exercises

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .background(MatteBlack)
                .padding(padding)
        ) {
            // ── Top bar ───────────────────────────────────────────────
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
                Column {
                    Text(
                        uiState.exercise?.name ?: "…",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    uiState.exercise?.let { exercise ->
                        Text(
                            "${exercise.category.name} · " + exercise.primaryMuscles.joinToString(", ") {
                                it.name.replace('_', ' ').lowercase()
                            },
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                }
            }
            HorizontalDivider(color = NeonGreen.copy(alpha = 0.25f), thickness = 1.dp)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Stat chips ────────────────────────────────────────
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailStatChip("BEST SET", WeightFormatter.format(uiState.bestWeightKg), Modifier.weight(1f))
                        DetailStatChip("EST. 1RM", WeightFormatter.formatOneRm(uiState.bestE1RmKg), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailStatChip("TOTAL SETS", "${uiState.totalSets}", Modifier.weight(1f))
                        DetailStatChip("TOTAL VOLUME", WeightFormatter.formatVolume(uiState.totalVolumeKg), Modifier.weight(1f))
                    }
                }

                // ── e1RM chart ────────────────────────────────────────
                if (uiState.chartPoints.size >= 2) {
                    item {
                        Column(
                            Modifier.fillMaxWidth()
                                .background(SaiyanGray, RoundedCornerShape(6.dp))
                                .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text("EST. 1RM PROGRESS", color = TelemetryGreen, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace)
                            E1RmLineChart(
                                points = uiState.chartPoints,
                                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 10.dp)
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatShortDate(uiState.chartPoints.first().dateMs),
                                    color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace)
                                Text(formatShortDate(uiState.chartPoints.last().dateMs),
                                    color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // ── History ───────────────────────────────────────────
                if (uiState.sessionHistory.isNotEmpty()) {
                    item {
                        Text("HISTORY", color = TelemetryGreen, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    items(uiState.sessionHistory, key = { it.dateMs }) { group ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(SaiyanGray, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(formatFullDate(group.dateMs), color = PowerAmber, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            group.sets.forEachIndexed { index, set ->
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SET ${index + 1}", color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(
                                        "${WeightFormatter.format(set.weightKg)} × ${set.reps}" +
                                            if (set.isFailure) "  [F]" else "",
                                        color = if (set.isFailure) DangerRed else Color.White,
                                        fontSize = 12.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "No sets logged yet. Train this lift to see progress here.",
                            color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = TelemetryGreen, fontSize = 9.sp, letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace)
        Text(value, color = NeonGreen, fontSize = 17.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun E1RmLineChart(points: List<E1RmChartPoint>, modifier: Modifier = Modifier) {
    val green = NeonGreen
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOf { it.e1RmKg }
        val maxVal = points.maxOf { it.e1RmKg }
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val minDate = points.first().dateMs
        val dateRange = (points.last().dateMs - minDate).takeIf { it > 0 } ?: 1L

        repeat(4) { i ->
            val y = size.height * (i / 3f)
            drawLine(green.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
        }

        val pts = points.map { point ->
            Offset(
                x = ((point.dateMs - minDate).toFloat() / dateRange) * size.width,
                y = size.height * (1f - ((point.e1RmKg - minVal) / range).toFloat() * 0.85f - 0.075f)
            )
        }

        val path = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(path, green.copy(alpha = 0.12f), style = Fill)
        for (i in 0 until pts.size - 1) {
            drawLine(green, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
        }
        pts.forEach { drawCircle(green, 3.dp.toPx(), it) }
    }
}

private fun formatShortDate(dateMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(dateMs))

private fun formatFullDate(dateMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMs))
