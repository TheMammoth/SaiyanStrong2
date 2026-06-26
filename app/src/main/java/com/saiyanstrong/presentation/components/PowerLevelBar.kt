package com.saiyanstrong.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanTheme
import com.saiyanstrong.presentation.theme.TelemetryGreen
import kotlin.math.roundToInt

private const val SEGMENT_COUNT = 10
private val GAP_DP = 2.dp
private val CANVAS_WIDTH_DP = 60.dp
private val CANVAS_HEIGHT_DP = 200.dp

@Composable
fun PowerLevelBar(powerLevel: PowerLevel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SegmentedBar(
            progress = powerLevel.progressToNext,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            powerLevel.stage.label.uppercase(),
            color = PowerAmber,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "POWER: ${powerLevel.current}",
            color = TelemetryGreen,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SegmentedBar(progress: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current

    // Hoist brush — stable for the lifetime of this composition
    val canvasHeightPx = remember(density) { with(density) { CANVAS_HEIGHT_DP.toPx() } }
    val activeBrush: Brush = remember(density) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF3B3B), // DangerRed — top
                Color(0xFFF5A623)  // PowerAmber — bottom
            ),
            startY = 0f,
            endY = canvasHeightPx
        )
    }
    val inactiveBrush: Brush = remember { SolidColor(Color(0xFF1A1A1A)) }

    val litSegments = remember(progress) {
        (progress * SEGMENT_COUNT).roundToInt().coerceIn(0, SEGMENT_COUNT)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TODO: Insert Lottie Flame Animation here.
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            val infiniteTransition = rememberInfiniteTransition(label = "flame_pulse")
            val flameAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "flame_alpha"
            )
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = Color(0xFFF5A623).copy(alpha = flameAlpha),
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(
            modifier = Modifier
                .width(CANVAS_WIDTH_DP)
                .height(CANVAS_HEIGHT_DP)
        ) {
            val gapPx = GAP_DP.toPx()
            val totalGapPx = (SEGMENT_COUNT - 1) * gapPx
            val segmentHeight = (size.height - totalGapPx) / SEGMENT_COUNT

            for (i in 0 until SEGMENT_COUNT) {
                // i=0 → bottom segment, i=9 → top segment
                val yTop = size.height - (i + 1) * segmentHeight - i * gapPx

                // Bar tapers from full width at bottom to 65% at top
                val widthFraction = 1f - 0.35f * i / (SEGMENT_COUNT - 1).toFloat()
                val segWidth = size.width * widthFraction
                val xStart = (size.width - segWidth) / 2f

                drawRect(
                    brush = if (i < litSegments) activeBrush else inactiveBrush,
                    topLeft = Offset(xStart, yTop),
                    size = Size(segWidth, segmentHeight)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun PowerLevelBarPreview() {
    SaiyanTheme {
        PowerLevelBar(
            powerLevel = PowerLevel(
                current = 14_500,
                stage = SaiyanStage.SSJ1,
                nextStageThreshold = 50_000,
                progressToNext = 0.7f
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun SegmentedBarFullPreview() {
    SaiyanTheme {
        SegmentedBar(progress = 1f)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun SegmentedBarHalfPreview() {
    SaiyanTheme {
        SegmentedBar(progress = 0.5f)
    }
}
