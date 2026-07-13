package com.saiyanstrong.presentation.screens.biomechanics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeInfo
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.SaiyanTheme

@Composable
fun ArchetypeSelectionScreen(
    onArchetypeChosen: (Archetype) -> Unit,
    onCompareAll: () -> Unit,
    viewModel: ArchetypeSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArchetypeSelectionContent(
        uiState = uiState,
        onArchetypeClick = { archetype ->
            viewModel.onArchetypeSelected(archetype)
            onArchetypeChosen(archetype)
        },
        onCompareAll = onCompareAll,
        onDisclaimerDismissed = viewModel::onDisclaimerDismissed
    )
}

@Composable
fun ArchetypeSelectionContent(
    uiState: ArchetypeSelectionViewModel.UiState,
    onArchetypeClick: (Archetype) -> Unit,
    onCompareAll: () -> Unit,
    onDisclaimerDismissed: () -> Unit
) {
    if (uiState.showDisclaimer) {
        AlertDialog(
            onDismissRequest = onDisclaimerDismissed,
            title = { Text("About this tool") },
            text = {
                Text(
                    "This tool shows how body proportions affect movement mechanics. It is not " +
                        "medical or coaching advice. Consult a qualified coach for individual programming."
                )
            },
            confirmButton = {
                TextButton(onClick = onDisclaimerDismissed) { Text("Got it", color = NeonGreen) }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().scanlineTexture().background(MatteBlack),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "How is your body built?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Pick the proportion that feels most like yours. You can change this anytime.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }

        items(uiState.archetypes) { info ->
            ArchetypeCard(
                info = info,
                standingNodes = uiState.standingNodes[info.archetype].orEmpty(),
                selected = uiState.selectedArchetype == info.archetype,
                onClick = { onArchetypeClick(info.archetype) }
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Not sure? Compare all four →",
                color = NeonGreen,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable(onClick = onCompareAll)
            )
        }
    }
}

@Composable
private fun ArchetypeCard(
    info: ArchetypeInfo,
    standingNodes: List<NodePosition>,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SaiyanGray)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) NeonGreen else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        StickmanCanvas(
            nodes = standingNodes,
            showBar = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)
        )
        Text(
            info.name,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            info.description,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@PreviewLightDark
@Composable
private fun ArchetypeSelectionPreview() {
    SaiyanTheme {
        ArchetypeSelectionContent(
            uiState = ArchetypeSelectionViewModel.UiState(
                archetypes = listOf(
                    ArchetypeInfo(Archetype.LONG_FEMUR, "Long Femur", "Long legs, shorter torso"),
                    ArchetypeInfo(Archetype.SHORT_FEMUR, "Short Femur", "Compact legs, longer torso"),
                    ArchetypeInfo(Archetype.PROPORTIONAL, "Proportional", "Balanced proportions"),
                    ArchetypeInfo(Archetype.WIDE_HIP, "Wide Hip", "Wider hip relative to shoulders")
                ),
                selectedArchetype = Archetype.PROPORTIONAL,
                isLoading = false
            ),
            onArchetypeClick = {},
            onCompareAll = {},
            onDisclaimerDismissed = {}
        )
    }
}
