package com.saiyanstrong.presentation.screens.biomechanics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme

@Composable
fun BiomechanicsCompareScreen(
    onBack: () -> Unit,
    viewModel: BiomechanicsCompareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BiomechanicsCompareContent(uiState = uiState, onBack = onBack, onSliderChanged = viewModel::onSliderChanged)
}

@Composable
fun BiomechanicsCompareContent(
    uiState: BiomechanicsCompareViewModel.UiState,
    onBack: () -> Unit,
    onSliderChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().scanlineTexture().background(MatteBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Compare builds", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (uiState.entries.size <= 2) uiState.entries.size.coerceAtLeast(1) else 2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(uiState.entries) { entry ->
                CompareCell(archetype = entry.archetype, nodes = entry.nodes)
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
    }
}

@Composable
private fun CompareCell(archetype: Archetype, nodes: List<NodePosition>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(SaiyanGray, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Text(
            archetype.displayName(),
            color = NeonGreen,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        StickmanCanvas(
            nodes = nodes,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 4.dp)
        )
    }
}

@PreviewLightDark
@Composable
private fun BiomechanicsComparePreview() {
    SaiyanTheme {
        BiomechanicsCompareContent(
            uiState = BiomechanicsCompareViewModel.UiState(
                entries = listOf(
                    BiomechanicsCompareViewModel.CompareEntry(Archetype.LONG_FEMUR, emptyList()),
                    BiomechanicsCompareViewModel.CompareEntry(Archetype.SHORT_FEMUR, emptyList())
                ),
                isLoading = false
            ),
            onBack = {},
            onSliderChanged = {}
        )
    }
}
