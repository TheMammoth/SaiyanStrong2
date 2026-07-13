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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanTheme

/** Slider range for each ratio — a first-pass default centered on the 4 fixed archetypes'
 * existing values; adjust if it turns out too tight or too loose once tried. */
private object RatioRanges {
    val thigh = 0.18f..0.32f
    val shank = 0.18f..0.32f
    val torso = 0.20f..0.36f
    val shoulderHalf = 0.06f..0.14f
    val hipHalf = 0.05f..0.14f
    val footLen = 0.06f..0.16f
}

@Composable
fun CustomProportionsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CustomProportionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CustomProportionsContent(
        uiState = uiState,
        onBack = onBack,
        onScrubChanged = viewModel::onScrubChanged,
        onThighChanged = viewModel::onThighRatioChanged,
        onShankChanged = viewModel::onShankRatioChanged,
        onTorsoChanged = viewModel::onTorsoRatioChanged,
        onShoulderChanged = viewModel::onShoulderHalfRatioChanged,
        onHipChanged = viewModel::onHipHalfRatioChanged,
        onFootChanged = viewModel::onFootLenRatioChanged,
        onSave = { viewModel.onSave(); onSaved() }
    )
}

@Composable
fun CustomProportionsContent(
    uiState: CustomProportionsViewModel.UiState,
    onBack: () -> Unit,
    onScrubChanged: (Float) -> Unit,
    onThighChanged: (Float) -> Unit,
    onShankChanged: (Float) -> Unit,
    onTorsoChanged: (Float) -> Unit,
    onShoulderChanged: (Float) -> Unit,
    onHipChanged: (Float) -> Unit,
    onFootChanged: (Float) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().scanlineTexture().background(MatteBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Custom proportions", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }

        StickmanCanvas(nodes = uiState.nodes, modifier = Modifier.fillMaxWidth().weight(0.45f))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Slider(
                value = uiState.sliderProgress,
                onValueChange = onScrubChanged,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STANDING", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("BOTTOM", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(0.55f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                RatioSliderRow("FEMUR (THIGH)", uiState.ratios.thighRatio, RatioRanges.thigh, onThighChanged)
            }
            item {
                RatioSliderRow("SHIN (SHANK)", uiState.ratios.shankRatio, RatioRanges.shank, onShankChanged)
            }
            item {
                RatioSliderRow("TORSO", uiState.ratios.torsoRatio, RatioRanges.torso, onTorsoChanged)
            }
            item {
                RatioSliderRow("SHOULDER WIDTH", uiState.ratios.shoulderHalfRatio, RatioRanges.shoulderHalf, onShoulderChanged)
            }
            item {
                RatioSliderRow("HIP WIDTH", uiState.ratios.hipHalfRatio, RatioRanges.hipHalf, onHipChanged)
            }
            item {
                RatioSliderRow("FOOT LENGTH", uiState.ratios.footLenRatio, RatioRanges.footLen, onFootChanged)
            }
            item {
                SaiyanButton(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(
                        if (uiState.justSaved) "SAVED ✓" else "SAVE",
                        fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RatioSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("%.3f".format(value), color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen)
        )
    }
}

@PreviewLightDark
@Composable
private fun CustomProportionsPreview() {
    SaiyanTheme {
        CustomProportionsContent(
            uiState = CustomProportionsViewModel.UiState(
                ratios = LimbRatios(
                    thighRatio = 0.25f, shankRatio = 0.25f, torsoRatio = 0.29f, headNeckRatio = 0.16f,
                    footLenRatio = 0.10f, shoulderHalfRatio = 0.09f, hipHalfRatio = 0.07f,
                    kneeHalfRatio = 0.05f, ankleHalfRatio = 0.045f, barRiseRatio = 0.04f, gripHalfRatio = 0.12f
                ),
                isLoading = false
            ),
            onBack = {}, onScrubChanged = {}, onThighChanged = {}, onShankChanged = {},
            onTorsoChanged = {}, onShoulderChanged = {}, onHipChanged = {}, onFootChanged = {}, onSave = {}
        )
    }
}
