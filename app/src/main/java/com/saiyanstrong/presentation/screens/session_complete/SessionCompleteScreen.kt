package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.R
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.SegmentedBar
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter

private val BG = Color(0xFF0D0D0D)

@Composable
fun SessionCompleteScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SessionCompleteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val weeklyBars by viewModel.weeklyBars.collectAsStateWithLifecycle()
    val strengthPct by viewModel.strengthProgressPct.collectAsStateWithLifecycle()
    val exerciseRows by viewModel.exerciseRows.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.isDone) { if (uiState.isDone) onDone() }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }
    SessionCompleteContent(
        session = uiState.session,
        powerLevel = uiState.powerLevel,
        weeklyBars = weeklyBars,
        strengthProgressPct = strengthPct,
        exerciseRows = exerciseRows,
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
    weeklyBars: List<Pair<String, Int>>,
    strengthProgressPct: Float,
    exerciseRows: List<ExerciseRow>,
    titleInput: String,
    onTitleChange: (String) -> Unit,
    onDone: () -> Unit,
    onDeleteSession: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BG).scanlineTexture()) {
        val panelH = maxHeight * 0.58f
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Saiyan Strong",
                color = PowerAmber,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Session Complete!",
                color = PowerAmber.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))

            // ── 3-column HUD row ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelH)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // LEFT — chart + table
                HudPanel(Modifier.weight(0.30f).fillMaxHeight()) {
                    StrengthLineChart(weeklyBars, Modifier.height(110.dp).fillMaxWidth())
                    HudDivider()
                    Spacer(Modifier.height(4.dp))
                    HudTableHeader("LIFT", "1RM", "REPS")
                    exerciseRows.take(6).forEach { row ->
                        HudTableRow(row.name.take(8), WeightFormatter.formatOneRm(row.estOneRmKg), "${row.totalReps}")
                    }
                }

                // CENTER — volume + power bar
                HudPanel(Modifier.weight(0.40f).fillMaxHeight()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        // Volume & mini chart
                        Column(Modifier.weight(0.55f)) {
                            VolumeHeroBox(session, exerciseRows, strengthProgressPct, weeklyBars)
                        }
                        Spacer(Modifier.height(4.dp))
                        // Power bar column
                        Column(
                            Modifier.weight(0.45f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (powerLevel != null) {
                                SegmentedBar(
                                    progress = powerLevel.progressToNext,
                                    flameSize = 44.dp,
                                    barWidth = 38.dp,
                                    barHeight = 120.dp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    powerLevel.stage.label.uppercase(),
                                    color = NeonGreen,
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "POWER LEVEL",
                                    color = PowerAmber,
                                    fontSize = 6.sp,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // RIGHT — 1RM + time tables
                HudPanel(Modifier.weight(0.30f).fillMaxHeight()) {
                    HudTableHeader("EXERCISE", "1RM")
                    exerciseRows.take(5).forEach { row ->
                        HudTableRow(row.name.take(8), WeightFormatter.formatOneRm(row.estOneRmKg))
                    }
                    HudDivider()
                    Spacer(Modifier.height(4.dp))
                    HudTableHeader("EXERCISE", "~TIME")
                    exerciseRows.take(5).forEach { row ->
                        val mins = row.totalSets * 3
                        val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "~${mins}m"
                        HudTableRow(row.name.take(8), timeStr)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Decorative barbell ──────────────────────────────
            androidx.compose.foundation.Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(130.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Session title input ─────────────────────────────
            OutlinedTextField(
                value = titleInput,
                onValueChange = onTitleChange,
                label = { Text("Session title (optional)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Action buttons ──────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteSession,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = BorderStroke(1.dp, DangerRed),
                    modifier = Modifier.weight(0.38f)
                ) { Text("DELETE", fontSize = 12.sp) }
                SaiyanButton(onClick = onDone, modifier = Modifier.weight(0.62f)) {
                    Text("DONE  >>>", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            // ── Telemetry bar ───────────────────────────────────
            if (session != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "// EX:${session.exerciseLogs.size}  SETS:${session.exerciseLogs.sumOf { it.sets.size }}  +${session.powerEarned} PWR //",
                    color = TelemetryGreen,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// ── HUD Panel ───────────────────────────────────────────────────────────────

@Composable
private fun HudPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .drawBehind {
                repeat(3) { i ->
                    val spread = (i + 1) * 3.dp.toPx()
                    drawRoundRect(
                        color = NeonGreen.copy(alpha = 0.07f * (3 - i)),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2)
                    )
                }
            }
            .border(1.5.dp, NeonGreen.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .background(BG, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        content()
    }
}

// ── Canvas line chart ────────────────────────────────────────────────────────

@Composable
private fun StrengthLineChart(bars: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val green = NeonGreen
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val maxVal = bars.maxOf { it.second }.coerceAtLeast(1).toFloat()
        val n = bars.size
        val stepX = if (n > 1) size.width / (n - 1).toFloat() else size.width / 2f
        repeat(4) { i ->
            val y = size.height * (i / 3f)
            drawLine(green.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
        }
        val pts = bars.mapIndexed { i, (_, count) ->
            Offset(i * stepX, size.height * (1f - count / maxVal).coerceIn(0f, 1f))
        }
        if (pts.size >= 2) {
            // Fill gradient under line
            val path = Path().apply {
                moveTo(pts.first().x, size.height)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, size.height)
                close()
            }
            drawPath(path, green.copy(alpha = 0.15f), style = Fill)
            for (i in 0 until pts.size - 1) {
                drawLine(green, pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
            }
        }
        pts.forEach { drawCircle(green, 3.dp.toPx(), it) }
    }
}

// ── Volume hero card (inside center panel) ───────────────────────────────────

@Composable
private fun VolumeHeroBox(
    session: WorkoutSession?,
    exerciseRows: List<ExerciseRow>,
    strengthPct: Float,
    bars: List<Pair<String, Int>>
) {
    Column {
        // Inner highlighted volume box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonGreen.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(6.dp)
        ) {
            Text("TOTAL VOLUME:", color = TelemetryGreen, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(
                if (session != null) WeightFormatter.formatVolume(session.totalVolumeKg) else "—",
                color = NeonGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(5.dp))
        // Exercise stats
        exerciseRows.take(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.name.take(10), color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(WeightFormatter.format(row.bestWeightKg), color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }
        if (session != null) {
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DURATION", color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp)
                Text(formatDuration(session.durationMs), color = Color.White, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(5.dp))
        // Mini progress chart
        val sign = if (strengthPct >= 0f) "+" else ""
        Text(
            "Strength Progress: ${sign}${"%.1f".format(strengthPct)}% Wk/Wk",
            color = PowerAmber,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace
        )
        StrengthLineChart(bars, Modifier.fillMaxWidth().height(48.dp))
    }
}

// ── Table helpers ────────────────────────────────────────────────────────────

@Composable
private fun HudTableHeader(vararg cols: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        cols.forEachIndexed { i, col ->
            Text(
                col,
                color = NeonGreen,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = if (i == 0) Modifier.weight(1f) else Modifier
            )
        }
    }
    HudDivider()
}

@Composable
private fun HudTableRow(vararg cols: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        cols.forEachIndexed { i, col ->
            Text(
                col,
                color = if (i == 0) Color.White.copy(alpha = 0.7f) else Color.White,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (i == 0) Modifier.weight(1f) else Modifier
            )
        }
    }
}

@Composable
private fun HudDivider() {
    HorizontalDivider(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        thickness = 0.5.dp,
        color = NeonGreen.copy(alpha = 0.3f)
    )
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60)
    else "%dm %02ds".format(s / 60, s % 60)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
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
            weeklyBars = listOf(Pair("6/1", 2), Pair("6/8", 3), Pair("6/15", 1), Pair("6/22", 4)),
            strengthProgressPct = 4.0f,
            exerciseRows = listOf(
                ExerciseRow("Bench Press", 100.0, 112.5, 45, 4),
                ExerciseRow("Squat", 140.0, 155.0, 36, 3),
                ExerciseRow("Deadlift", 160.0, 176.0, 20, 2)
            ),
            titleInput = "",
            onTitleChange = {},
            onDone = {},
            onDeleteSession = {}
        )
    }
}
