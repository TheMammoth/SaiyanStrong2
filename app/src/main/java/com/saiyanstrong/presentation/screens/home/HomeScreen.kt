package com.saiyanstrong.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    HomeContent(powerLevel = powerLevel, onStartWorkout = onStartWorkout, onViewHistory = onViewHistory)
}

@Composable
internal fun HomeContent(
    powerLevel: PowerLevel?,
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
            // Header
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

            // Power level bar
            powerLevel?.let {
                PowerLevelBar(
                    powerLevel = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Action buttons
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

            // Telemetry bar
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
            onStartWorkout = {},
            onViewHistory = {}
        )
    }
}
