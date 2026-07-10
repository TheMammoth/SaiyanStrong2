package com.saiyanstrong.presentation.screens.exercises

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.saiyanstrong.domain.model.ExerciseMedia
import com.saiyanstrong.presentation.components.scanlineTexture
import kotlinx.coroutines.delay
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

private val TAB_TITLES = listOf("ABOUT", "CHARTS", "RECORDS", "HISTORY")

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }  // default ABOUT

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
                Text(
                    uiState.exercise?.name ?: "…",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }

            // ── Tabs ──────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SaiyanGray,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NeonGreen
                    )
                }
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Black else FontWeight.Normal,
                                color = if (selectedTab == index) NeonGreen else Color.White.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> AboutTab(uiState, media)
                1 -> ChartsTab(uiState)
                2 -> RecordsTab(uiState)
                else -> HistoryTab(uiState)
            }
        }
    }
}

// ── ABOUT ─────────────────────────────────────────────────────────────────────

@Composable
private fun AboutTab(uiState: ExerciseDetailUiState, media: ExerciseMedia?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (media != null && media.imageUrls.isNotEmpty()) {
            item { FlipBookImage(media.imageUrls) }
        }
        uiState.exercise?.let { exercise ->
            item {
                DetailSection("CATEGORY") {
                    Text(exercise.category.name, color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            item {
                DetailSection("PRIMARY MUSCLES") {
                    Text(
                        exercise.primaryMuscles.joinToString(", ") { it.name.pretty() },
                        color = NeonGreen, fontSize = 13.sp
                    )
                }
            }
            if (exercise.secondaryMuscles.isNotEmpty()) {
                item {
                    DetailSection("SECONDARY MUSCLES") {
                        Text(
                            exercise.secondaryMuscles.joinToString(", ") { it.name.pretty() },
                            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                        )
                    }
                }
            }
            if (!media?.instructions.isNullOrEmpty()) {
                item {
                    DetailSection("INSTRUCTIONS") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            media?.instructions?.forEachIndexed { index, step ->
                                Row {
                                    Text(
                                        "${index + 1}.",
                                        color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(step, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                                        lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                DetailSection("LIFETIME") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailStatChip("TOTAL SETS", "${uiState.totalSets}", Modifier.weight(1f))
                        DetailStatChip("TOTAL VOLUME", WeightFormatter.formatVolume(uiState.totalVolumeKg), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipBookImage(imageUrls: List<String>) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(imageUrls) {
        if (imageUrls.size < 2) return@LaunchedEffect
        while (true) {
            delay(900)
            frame = (frame + 1) % imageUrls.size
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
    ) {
        Crossfade(targetState = frame, animationSpec = tween(350), label = "flipbook") { index ->
            AsyncImage(
                model = imageUrls[index],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── CHARTS ────────────────────────────────────────────────────────────────────

@Composable
private fun ChartsTab(uiState: ExerciseDetailUiState) {
    if (uiState.e1RmChart.size < 2) {
        EmptyTabMessage("Log this lift in at least two sessions to unlock charts.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ChartCard("BEST SET (EST. 1RM)", uiState.e1RmChart) }
        item { ChartCard("BEST SET (MAX WEIGHT)", uiState.weightChart) }
        item { ChartCard("SESSION VOLUME (KG)", uiState.volumeChart) }
        if (uiState.velocityChart.size >= 2) {
            item { ChartCard("BAR SPEED (MEAN VELOCITY, M/S)", uiState.velocityChart) }
        }
    }
}

@Composable
private fun ChartCard(title: String, points: List<ChartPoint>) {
    Column(
        Modifier.fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TelemetryGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(
                "%.1f".format(points.maxOf { it.value }),
                color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        DetailLineChart(
            points = points,
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 10.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatShortDate(points.first().dateMs), color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(formatShortDate(points.last().dateMs), color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── RECORDS ───────────────────────────────────────────────────────────────────

@Composable
private fun RecordsTab(uiState: ExerciseDetailUiState) {
    if (uiState.repMaxRecords.isEmpty()) {
        EmptyTabMessage("No records yet. Log a set to set your first PR.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("PERSONAL RECORDS", color = TelemetryGreen, fontSize = 10.sp,
                letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
        }
        item { RecordRow("Estimated 1RM", WeightFormatter.formatOneRm(uiState.bestE1RmKg)) }
        item { RecordRow("Max weight", WeightFormatter.format(uiState.bestWeightKg)) }
        item { RecordRow("Max session volume", WeightFormatter.formatVolume(uiState.maxSessionVolumeKg)) }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                Text("REPS", color = TelemetryGreen, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.2f))
                Text("BEST PERFORMANCE", color = TelemetryGreen, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.Center)
                Text("EST. 1RM", color = TelemetryGreen, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.3f),
                    textAlign = TextAlign.End)
            }
        }
        items(uiState.repMaxRecords, key = { it.reps }) { record ->
            Row(
                Modifier.fillMaxWidth()
                    .background(SaiyanGray, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${record.reps}", color = PowerAmber, fontSize = 14.sp,
                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.2f))
                Column(Modifier.weight(0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${WeightFormatter.format(record.weightKg)} (×${record.reps})",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace)
                    Text(formatShortDate(record.dateMs), color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Text(WeightFormatter.formatOneRm(record.estimatedOneRmKg),
                    color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.3f))
            }
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace)
    }
}

// ── HISTORY ───────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(uiState: ExerciseDetailUiState) {
    if (uiState.sessionHistory.isEmpty()) {
        EmptyTabMessage("Not performed yet. Previous performances of this lift will show here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = TelemetryGreen, fontSize = 10.sp, letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        content()
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
private fun EmptyTabMessage(message: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DetailLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val green = NeonGreen
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOf { it.value }
        val maxVal = points.maxOf { it.value }
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
                y = size.height * (1f - ((point.value - minVal) / range).toFloat() * 0.85f - 0.075f)
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

private fun String.pretty(): String =
    replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun formatShortDate(dateMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(dateMs))

private fun formatFullDate(dateMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(dateMs))
