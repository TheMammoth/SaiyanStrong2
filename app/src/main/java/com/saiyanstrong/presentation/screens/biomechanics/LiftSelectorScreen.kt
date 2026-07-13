package com.saiyanstrong.presentation.screens.biomechanics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.presentation.components.scanlineTexture
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanTheme

@Composable
fun LiftSelectorScreen(
    archetype: Archetype,
    onLiftChosen: (LiftType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().scanlineTexture().background(MatteBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Now pick a lift to explore",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            archetype.displayName(),
            color = NeonGreen,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
        OutlinedButton(
            onClick = { onLiftChosen(LiftType.SQUAT) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SQUAT", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }

        // Deadlift keyframe content doesn't exist yet (Phase 1 ships squat only, per spec's
        // acceptance criteria) — shown disabled rather than omitted, so it reads as "coming
        // soon" instead of quietly missing.
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text("DEADLIFT (SOON)", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
    }
}

@PreviewLightDark
@Composable
private fun LiftSelectorPreview() {
    SaiyanTheme {
        LiftSelectorScreen(archetype = Archetype.LONG_FEMUR, onLiftChosen = {})
    }
}
