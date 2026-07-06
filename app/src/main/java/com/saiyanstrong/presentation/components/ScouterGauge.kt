package com.saiyanstrong.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 150f   // gauge opening faces down
private const val SWEEP_MAX   = 240f

/**
 * Scouter-style arc gauge: power level reading in the center, arc sweep =
 * progress toward the next Saiyan stage, radial ticks like a scanner reticle.
 */
@Composable
fun ScouterGauge(
    powerCurrent: Int,
    stageLabel: String,
    progressToNext: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressToNext.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gaugeProgress"
    )

    Box(modifier = modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth * 1.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val arcTopLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = arcSize.width / 2f

            // Radial ticks along the sweep — every 5th is brighter and longer
            repeat(25) { i ->
                val angleRad = Math.toRadians((START_ANGLE + SWEEP_MAX * i / 24f).toDouble())
                val major = i % 4 == 0
                val outer = radius + strokeWidth * if (major) 1.5f else 1.1f
                val inner = radius + strokeWidth * 0.55f
                val dir = Offset(cos(angleRad).toFloat(), sin(angleRad).toFloat())
                drawLine(
                    color = NeonGreen.copy(alpha = if (major) 0.5f else 0.18f),
                    start = center + dir * inner,
                    end = center + dir * outer,
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Track
            drawArc(
                color = SaiyanGray,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_MAX,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress
            val sweep = SWEEP_MAX * animatedProgress
            if (sweep > 0f) {
                drawArc(
                    color = NeonGreen,
                    startAngle = START_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                // Amber reticle dot at the sweep tip
                val tipRad = Math.toRadians((START_ANGLE + sweep).toDouble())
                val tip = center + Offset(cos(tipRad).toFloat(), sin(tipRad).toFloat()) * radius
                drawCircle(PowerAmber.copy(alpha = 0.35f), radius = strokeWidth * 1.1f, center = tip)
                drawCircle(PowerAmber, radius = strokeWidth * 0.55f, center = tip)
            }

            // Inner hairline ring
            drawCircle(
                color = NeonGreen.copy(alpha = 0.15f),
                radius = radius - strokeWidth * 1.6f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "POWER LEVEL",
                color = TelemetryGreen,
                fontSize = 9.sp,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "%,d".format(powerCurrent),
                color = NeonGreen,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                stageLabel.uppercase(),
                color = PowerAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}
