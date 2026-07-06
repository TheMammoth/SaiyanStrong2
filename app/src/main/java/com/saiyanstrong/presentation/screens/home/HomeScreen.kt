package com.saiyanstrong.presentation.screens.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.model.BodyWeightLog
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.ScouterGauge
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val powerLevel by viewModel.powerLevel.collectAsStateWithLifecycle()
    val dashboardStats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val thisWeekStats by viewModel.thisWeekStats.collectAsStateWithLifecycle()
    val bodyWeightLogs by viewModel.bodyWeightLogs.collectAsStateWithLifecycle()
    val dotsScore by viewModel.dotsScore.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(downloadState) {
        if (downloadState is UpdateDownloadState.Ready) {
            val uri = (downloadState as UpdateDownloadState.Ready).uri
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            viewModel.onInstallConsumed()
        }
    }

    HomeContent(
        powerLevel = powerLevel,
        dashboardStats = dashboardStats,
        thisWeekStats = thisWeekStats,
        bodyWeightLogs = bodyWeightLogs,
        dotsScore = dotsScore,
        updateAvailable = updateAvailable,
        downloadState = downloadState,
        onStartWorkout = onStartWorkout,
        onViewHistory = onViewHistory,
        onDownloadUpdate = {
            if (!viewModel.canInstallPackages()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                viewModel.onDownloadUpdate()
            }
        },
        onDismissUpdate = viewModel::onDismissUpdate,
        updateStatus = updateStatus,
        onRetryUpdateCheck = viewModel::retryUpdateCheck,
        onLogBodyWeight = viewModel::onLogBodyWeight
    )
}

@Composable
internal fun HomeContent(
    powerLevel: PowerLevel?,
    dashboardStats: DashboardStats,
    thisWeekStats: WeekStats,
    updateAvailable: AppUpdate?,
    downloadState: UpdateDownloadState,
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    updateStatus: String = "",
    onRetryUpdateCheck: () -> Unit = {},
    bodyWeightLogs: List<BodyWeightLog> = emptyList(),
    dotsScore: Double? = null,
    onLogBodyWeight: (Double) -> Unit = {}
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
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SAIYAN STRONG",
                        color = PowerAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "SCOUTER ONLINE",
                        color = TelemetryGreen,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                AnimatedVisibility(
                    visible = updateAvailable != null,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it }
                ) {
                    UpdateBanner(
                        tagName = updateAvailable?.tagName ?: "",
                        downloadState = downloadState,
                        onDownload = onDownloadUpdate,
                        onDismiss = onDismissUpdate
                    )
                }

                // ── Scouter gauge (hero) ────────────────────────────
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ScouterGauge(
                        powerCurrent = powerLevel?.current ?: 0,
                        stageLabel = powerLevel?.stage?.label ?: "SCANNING…",
                        progressToNext = powerLevel?.progressToNext ?: 0f
                    )
                }
                powerLevel?.let { level ->
                    val next = SaiyanStage.entries.firstOrNull { it.threshold > level.current }
                    Text(
                        if (next != null)
                            "NEXT: ${next.label.uppercase()} · ${"%,d".format(next.threshold)}"
                        else "MAXIMUM STAGE REACHED",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // ── Stat tiles ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatTile(
                        label = "STREAK",
                        value = "${dashboardStats.streakWeeks}w",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "THIS WEEK",
                        value = if (thisWeekStats.sessions > 0)
                            WeightFormatter.formatVolume(thisWeekStats.volumeKg) else "—",
                        sub = if (thisWeekStats.sessions > 0) "${thisWeekStats.sessions} SESSIONS" else "",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "DOTS",
                        value = dotsScore?.let { "%.1f".format(it) } ?: "—",
                        valueColor = PowerAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Big three ───────────────────────────────────────
                if (dashboardStats.bigThree.any { it.bestE1RmKg > 0.0 }) {
                    SectionLabel("BIG THREE · EST. 1RM")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dashboardStats.bigThree.forEach { lift ->
                            LiftChip(lift = lift, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ── Consistency heat ────────────────────────────────
                SectionLabel("CONSISTENCY · 12 WEEKS")
                HeatStrip(
                    heat = dashboardStats.heat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onViewHistory() }
                )

                Spacer(Modifier.height(12.dp))

                BodyWeightCard(
                    logs = bodyWeightLogs,
                    dotsScore = null,  // DOTS already shown in the tile row
                    onLogBodyWeight = onLogBodyWeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(16.dp))
            }

            // ── CTA + telemetry (pinned) ────────────────────────────
            SaiyanButton(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    "▶  BEGIN TRAINING",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Text(
                "// PWR: ${powerLevel?.current ?: "---"}  |  v${BuildConfig.VERSION_NAME}  |  $updateStatus  [tap to retry] //",
                color = TelemetryGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clickable(onClick = onRetryUpdateCheck)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

// ── Dashboard pieces ─────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TelemetryGreen,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String = "",
    valueColor: Color = NeonGreen
) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, NeonGreen.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TelemetryGreen, fontSize = 9.sp, letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace, maxLines = 1)
        if (sub.isNotEmpty()) {
            Text(sub, color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LiftChip(lift: LiftStat, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .border(1.dp, PowerAmber.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lift.label, color = PowerAmber, fontSize = 11.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            SparkLine(values = lift.spark, modifier = Modifier.width(40.dp).height(16.dp))
        }
        Text(
            if (lift.bestE1RmKg > 0.0) WeightFormatter.formatOneRm(lift.bestE1RmKg) else "—",
            color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace, maxLines = 1
        )
    }
}

@Composable
private fun SparkLine(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1).toFloat()
        val pts = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height * (1f - ((v - minVal) / range).toFloat() * 0.8f - 0.1f))
        }
        for (i in 0 until pts.size - 1) {
            drawLine(NeonGreen.copy(alpha = 0.8f), pts[i], pts[i + 1], strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
private fun HeatStrip(heat: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        heat.forEachIndexed { index, count ->
            val alpha = when {
                count <= 0 -> 0.07f
                count == 1 -> 0.35f
                count == 2 -> 0.6f
                else -> 0.9f
            }
            val isCurrentWeek = index == heat.lastIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
                    .background(NeonGreen.copy(alpha = alpha), RoundedCornerShape(3.dp))
                    .then(
                        if (isCurrentWeek)
                            Modifier.border(1.dp, PowerAmber.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                        else Modifier
                    )
            )
        }
    }
}

// ── Bodyweight card ──────────────────────────────────────────────────────────

@Composable
private fun BodyWeightCard(
    logs: List<BodyWeightLog>,
    dotsScore: Double?,
    onLogBodyWeight: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInput by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val latest = logs.firstOrNull()
    val previous = logs.getOrNull(1)

    fun save() {
        input.replace(',', '.').toDoubleOrNull()?.let { weightKg ->
            onLogBodyWeight(weightKg)
            input = ""
            showInput = false
        }
    }

    Column(
        modifier = modifier
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BODYWEIGHT", color = TelemetryGreen, style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        latest?.let { WeightFormatter.format(it.weightKg) } ?: "—",
                        color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    if (latest != null && previous != null) {
                        val delta = latest.weightKg - previous.weightKg
                        Text(
                            "  ${if (delta >= 0) "+" else ""}${"%.1f".format(delta)}",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
            if (dotsScore != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 12.dp)) {
                    Text("DOTS", color = TelemetryGreen, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("%.1f".format(dotsScore), color = PowerAmber, fontSize = 16.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
            BodyWeightSparkline(
                logs = logs,
                modifier = Modifier.width(64.dp).height(28.dp).padding(end = 8.dp)
            )
            TextButton(onClick = { showInput = !showInput }) {
                Text(if (showInput) "✕" else "LOG", color = NeonGreen,
                    fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }

        if (showInput) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    cursorBrush = SolidColor(NeonGreen),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Text("kg", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { save() }) {
                    Text("SAVE", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BodyWeightSparkline(logs: List<BodyWeightLog>, modifier: Modifier = Modifier) {
    // logs come newest-first; sparkline reads oldest → newest
    val points = remember(logs) { logs.take(15).reversed() }
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOf { it.weightKg }
        val maxVal = points.maxOf { it.weightKg }
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1).toFloat()
        val offsets = points.mapIndexed { i, log ->
            Offset(i * stepX, size.height * (1f - ((log.weightKg - minVal) / range).toFloat() * 0.8f - 0.1f))
        }
        for (i in 0 until offsets.size - 1) {
            drawLine(NeonGreen.copy(alpha = 0.8f), offsets[i], offsets[i + 1], strokeWidth = 1.5.dp.toPx())
        }
    }
}

// ── Update banner ────────────────────────────────────────────────────────────

@Composable
private fun UpdateBanner(
    tagName: String,
    downloadState: UpdateDownloadState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PowerAmber.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.SystemUpdate,
            contentDescription = null,
            tint = PowerAmber,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "UPDATE AVAILABLE",
                color = PowerAmber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(tagName, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        when (downloadState) {
            is UpdateDownloadState.Downloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = PowerAmber,
                    strokeWidth = 2.dp
                )
            }
            else -> {
                TextButton(onClick = onDownload) {
                    Text("UPDATE", color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = DangerRed, fontSize = 12.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
internal fun HomeContentPreview() {
    SaiyanTheme {
        HomeContent(
            powerLevel = PowerLevel(
                current = 32_450,
                stage = SaiyanStage.SSJ1,
                nextStageThreshold = 50_000,
                progressToNext = 0.62f
            ),
            dashboardStats = DashboardStats(
                streakWeeks = 4,
                bigThree = listOf(
                    LiftStat("SQ", 172.5, listOf(150.0, 155.0, 160.0, 158.0, 165.0, 172.5)),
                    LiftStat("BP", 117.5, listOf(105.0, 110.0, 108.0, 112.5, 117.5)),
                    LiftStat("DL", 210.0, listOf(180.0, 190.0, 195.0, 205.0, 210.0))
                ),
                heat = listOf(1, 2, 0, 3, 2, 2, 1, 3, 2, 4, 3, 2)
            ),
            thisWeekStats = WeekStats(sessions = 3, volumeKg = 12_400.0, topLiftKg = 210.0, topLiftName = "Deadlift"),
            bodyWeightLogs = listOf(
                BodyWeightLog(2, 1_700_100_000_000, 84.2),
                BodyWeightLog(1, 1_700_000_000_000, 84.8)
            ),
            dotsScore = 312.4,
            updateAvailable = null,
            downloadState = UpdateDownloadState.Idle,
            onStartWorkout = {},
            onViewHistory = {},
            onDownloadUpdate = {},
            onDismissUpdate = {}
        )
    }
}
