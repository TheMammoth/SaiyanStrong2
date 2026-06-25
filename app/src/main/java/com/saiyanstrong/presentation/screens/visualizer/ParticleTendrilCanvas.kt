package com.saiyanstrong.presentation.screens.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import kotlin.math.cos
import kotlin.math.sin

private const val TENDRIL_COUNT = 12

@Composable
fun ParticleTendrilCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "tendrils")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tendrilProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f
        repeat(TENDRIL_COUNT) { index ->
            val angle = (2 * Math.PI * index / TENDRIL_COUNT).toFloat()
            val radius = maxRadius * progress
            val end = Offset(
                x = center.x + radius * cos(angle),
                y = center.y + radius * sin(angle)
            )
            drawLine(
                color = if (index % 2 == 0) NeonGreen else PowerAmber,
                start = center,
                end = end,
                strokeWidth = 4f,
                alpha = 1f - progress
            )
        }
    }
}
