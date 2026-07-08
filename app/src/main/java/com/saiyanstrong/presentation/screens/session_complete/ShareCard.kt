package com.saiyanstrong.presentation.screens.session_complete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.PowerLevel
import com.saiyanstrong.domain.model.SaiyanStage
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.presentation.components.ScouterGauge
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.presentation.theme.TelemetryGreen
import com.saiyanstrong.util.WeightFormatter
import java.text.DateFormat
import java.util.Date

const val SHARE_CARD_WIDTH_PX = 1080
const val SHARE_CARD_HEIGHT_PX = 1350

private fun SaiyanStage.glowColor(): Color = when (this) {
    SaiyanStage.BASE -> Color.White
    SaiyanStage.SSJ1, SaiyanStage.SSJ2, SaiyanStage.SSJ3 -> PowerAmber
    SaiyanStage.SSJ_GOD -> DangerRed
    SaiyanStage.ULTRA -> NeonGreen
}

private data class TopLift(val name: String, val bestKg: Double, val bestReps: Int)

private fun topLiftsByVolume(exerciseLogs: List<ExerciseLog>): List<TopLift> =
    exerciseLogs
        .filter { it.sets.isNotEmpty() }
        .sortedByDescending { log -> log.sets.sumOf { it.volumeKg } }
        .take(3)
        .map { log ->
            val best = log.sets.maxBy { it.weightKg }
            TopLift(name = log.exercise.name, bestKg = best.weightKg, bestReps = best.reps)
        }

/**
 * Rendered offscreen at exactly [SHARE_CARD_WIDTH_PX]x[SHARE_CARD_HEIGHT_PX] px (caller wraps
 * this in a Density(1f) override so 1.dp == 1px and the captured bitmap comes out pixel-exact
 * regardless of the device's actual screen density).
 */
@Composable
fun ShareCardContent(session: WorkoutSession, powerLevel: PowerLevel?) {
    val topLifts = topLiftsByVolume(session.exerciseLogs)
    val glowColor = powerLevel?.stage?.glowColor() ?: NeonGreen

    Box(
        Modifier
            .fillMaxSize()
            .background(MatteBlack)
    ) {
        // Subtle background scouter-gauge accent, bleeding off the top-right corner
        ScouterGauge(
            powerCurrent = powerLevel?.current ?: 0,
            stageLabel = "",
            progressToNext = powerLevel?.progressToNext ?: 0f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-60).dp)
                .alpha(0.10f)
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(56.dp)
        ) {
            // ── Logo header ──────────────────────────────────────
            Text(
                "⚡ SAIYAN STRONG",
                color = PowerAmber,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(28.dp))

            // ── Title + date ─────────────────────────────────────
            Text(
                session.title.ifBlank { "TRAINING SESSION" }.uppercase(),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                DateFormat.getDateInstance(DateFormat.LONG).format(Date(session.dateMs)),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(48.dp))

            // ── Hero: volume + power earned ──────────────────────
            Text(
                "TOTAL VOLUME",
                color = TelemetryGreen,
                fontSize = 15.sp,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                WeightFormatter.format(session.totalVolumeKg),
                color = NeonGreen,
                fontSize = 92.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                "POWER EARNED +${session.powerEarned}",
                color = PowerAmber,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(36.dp))

            // ── Power Level + stage ───────────────────────────────
            powerLevel?.let { level ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SaiyanGray, RoundedCornerShape(10.dp))
                        .border(2.dp, glowColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    Text(
                        level.stage.label.uppercase(),
                        color = glowColor,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "%,d POWER LEVEL".format(level.current),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Top lifts ─────────────────────────────────────────
            if (topLifts.isNotEmpty()) {
                Text(
                    "TOP LIFTS",
                    color = TelemetryGreen,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                topLifts.forEach { lift ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            lift.name.uppercase(),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${WeightFormatter.format(lift.bestKg)} × ${lift.bestReps}",
                            color = NeonGreen,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Footer ────────────────────────────────────────────
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "TRACKED WITH SAIYANSTRONG",
                color = TelemetryGreen,
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
