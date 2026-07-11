package com.saiyanstrong.presentation.screens.barpath

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * The live, velocity-colored bar-path trail — drawn only while the lift is MOVING (per the
 * caller's gating), frozen once it completes. [points] are in the same downsampled
 * analysis-frame pixel space as [LockOnReticle]'s marker position, carrying the same
 * unverified-on-device crop/aspect-ratio caveat when mapped onto [boxSize].
 *
 * Reuses [velocityColorArgb] (built for [BarPathReplayContent]'s calibrated post-hoc replay,
 * anchored at real m/s values 0.2-0.8) purely as a red->orange->yellow->green gradient shape —
 * this trail's velocities are UNCALIBRATED relative numbers (see BarPathCaptureViewModel's
 * "Continuous live rep session" section), so the color is a reasonable-looking relative cue, not
 * a claim that these are the same real velocity zones the offline replay screen shows.
 */
@Composable
fun LiveTrailOverlay(
    points: List<BarPathCaptureViewModel.TrailPoint>,
    frameWidthPx: Int,
    frameHeightPx: Int,
    boxSize: Size,
    modifier: Modifier = Modifier
) {
    if (points.size < 2 || frameWidthPx <= 0 || frameHeightPx <= 0 || boxSize.width <= 0f || boxSize.height <= 0f) return

    Canvas(modifier.fillMaxSize()) {
        val strokeWidthPx = 4.dp.toPx()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val start = Offset(a.xPx / frameWidthPx * boxSize.width, a.yPx / frameHeightPx * boxSize.height)
            val end = Offset(b.xPx / frameWidthPx * boxSize.width, b.yPx / frameHeightPx * boxSize.height)
            drawLine(
                color = Color(velocityColorArgb(b.velocityMps)),
                start = start,
                end = end,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
    }
}
