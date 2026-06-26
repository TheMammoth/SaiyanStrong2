package com.saiyanstrong.presentation.screens.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.AppUpdate
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.presentation.components.PowerLevelBar
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter
import com.saiyanstrong.BuildConfig

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val powerLevel by viewModel.powerLevel.collectAsStateWithLifecycle()
    val weeklyBars by viewModel.weeklyBars.collectAsStateWithLifecycle()
    val thisWeekStats by viewModel.thisWeekStats.collectAsStateWithLifecycle()
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
        weeklyBars = weeklyBars,
        thisWeekStats = thisWeekStats,
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
        onSettings = onSettings
    )
}

@Composable
internal fun HomeContent(
    powerLevel: PowerLevel?,
    weeklyBars: List<WeekBar>,
    thisWeekStats: WeekStats,
    updateAvailable: AppUpdate?,
    downloadState: UpdateDownloadState,
    onStartWorkout: () -> Unit,
    onViewHistory: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    updateStatus: String = "",
    onRetryUpdateCheck: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scanlineTexture()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaiyanGray)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            powerLevel?.let {
                PowerLevelBar(
                    powerLevel = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            if (thisWeekStats.sessions > 0) {
                ThisWeekRow(
                    stats = thisWeekStats,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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

@Composable
private fun ThisWeekRow(stats: WeekStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "THIS WEEK",
            color = TelemetryGreen,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniStatChip(
                label = "SESSIONS",
                value = "${stats.sessions}",
                modifier = Modifier.weight(1f)
            )
            MiniStatChip(
                label = "VOLUME",
                value = WeightFormatter.formatVolume(stats.volumeKg),
                modifier = Modifier.weight(1f)
            )
            MiniStatChip(
                label = "TOP LIFT",
                value = if (stats.topLiftKg > 0.0) WeightFormatter.format(stats.topLiftKg) else "—",
                sub = stats.topLiftName.take(8).uppercase(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String = ""
) {
    Column(
        modifier = modifier
            .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TelemetryGreen, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        Text(
            value,
            color = NeonGreen,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black
        )
        if (sub.isNotEmpty()) {
            Text(sub, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
        }
    }
}

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
            thisWeekStats = WeekStats(sessions = 3, volumeKg = 1850.0, topLiftKg = 130.0, topLiftName = "Deadlift"),
            updateAvailable = AppUpdate("v0.6.0", ""),
            downloadState = UpdateDownloadState.Idle,
            onStartWorkout = {},
            onViewHistory = {},
            onDownloadUpdate = {},
            onDismissUpdate = {}
        )
    }
}
