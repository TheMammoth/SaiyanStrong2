package com.saiyanstrong.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.saiyanstrong.presentation.theme.TelemetryGreen
import kotlinx.coroutines.delay

private const val CHAR_DELAY_MS = 18L

@Composable
fun TelemetryLog(message: String, modifier: Modifier = Modifier) {
    var visibleText by remember(message) { mutableStateOf("") }

    LaunchedEffect(message) {
        visibleText = ""
        for (charCount in 1..message.length) {
            visibleText = message.substring(0, charCount)
            delay(CHAR_DELAY_MS)
        }
    }

    Text(
        text = visibleText,
        color = TelemetryGreen,
        fontFamily = FontFamily.Monospace,
        modifier = modifier.padding(8.dp)
    )
}
