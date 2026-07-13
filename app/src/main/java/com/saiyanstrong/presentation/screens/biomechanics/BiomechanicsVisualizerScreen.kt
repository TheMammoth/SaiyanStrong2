package com.saiyanstrong.presentation.screens.biomechanics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme

@Composable
fun BiomechanicsVisualizerScreen(
    onBack: () -> Unit,
    onCompare: () -> Unit,
    viewModel: BiomechanicsVisualizerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BiomechanicsVisualizerContent(
        uiState = uiState,
        onBack = onBack,
        onCompare = onCompare,
        onSliderChanged = viewModel::onSliderChanged
    )
}

@Composable
fun BiomechanicsVisualizerContent(
    uiState: BiomechanicsVisualizerViewModel.UiState,
    onBack: () -> Unit,
    onCompare: () -> Unit,
    onSliderChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().scanlineTexture().background(MatteBlack)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "${uiState.archetypeName} · ${uiState.liftName}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }

        StickmanCanvas(
            nodes = uiState.nodes,
            modifier = Modifier.fillMaxWidth().weight(0.6f)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Slider(
                value = uiState.sliderProgress,
                onValueChange = onSliderChanged,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STANDING", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("BOTTOM", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "What's happening mechanically",
                    color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(uiState.mechanicalFacts) { fact ->
                Text("•  $fact", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            item { HorizontalDivider(color = SaiyanGray, modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Text("Coaching cue that doesn't apply here", color = PowerAmber, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            item {
                Text(
                    uiState.irrelevantCue,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            item { HorizontalDivider(color = SaiyanGray, modifier = Modifier.padding(bottom = 8.dp)) }
            item {
                Text("Try this instead", color = DangerRed, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            item {
                Text(
                    uiState.stanceCue,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
            item {
                OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text("COMPARE WITH ANOTHER BUILD →", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun BiomechanicsVisualizerPreview() {
    SaiyanTheme {
        BiomechanicsVisualizerContent(
            uiState = BiomechanicsVisualizerViewModel.UiState(
                archetypeName = "Long Femur",
                liftName = "Squat",
                mechanicalFacts = listOf(
                    "Forward torso lean is greater than standard. This is structurally necessary, not a form error.",
                    "Knee travel is more forward to keep the bar over mid-foot.",
                    "Hip crease reaches parallel later in the descent arc."
                ),
                stanceCue = "Lifters with this structure typically find a stance slightly wider than hip-width works well.",
                irrelevantCue = "\"Keep your torso upright\" → With long femurs, an upright torso moves the bar forward of mid-foot.",
                isLoading = false
            ),
            onBack = {},
            onCompare = {},
            onSliderChanged = {}
        )
    }
}
