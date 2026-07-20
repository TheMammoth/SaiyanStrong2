package com.saiyanstrong.presentation.screens.barpath

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.TrackedFrame
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import java.io.File
import kotlin.math.abs

/**
 * Post-rep replay: plays the recorded lift back with the tracked bar path overlaid, colour-coded
 * by velocity and synced to playback position. Built as a Compose screen (not the requested
 * Fragment/custom-View — this app is Compose-only) hosting an ExoPlayer PlayerView via AndroidView
 * with a Compose Canvas overlay on top. EPHEMERAL: only usable right after a rep, from the still-
 * cached video + in-memory [frames] — nothing about replay is persisted. Entirely unverified on a
 * real device.
 */
@OptIn(UnstableApi::class)
@Composable
fun BarPathReplayContent(
    videoPath: String,
    frames: List<TrackedFrame>,
    videoWidthPx: Int,
    videoHeightPx: Int,
    analysis: BarPathAnalysis?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            repeatMode = Player.REPEAT_MODE_ALL // loop for coaching review
            volume = 0f                          // powerlifting recordings have no useful audio
            playWhenReady = false                // paused on load; user scrubs first
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    var playbackMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    // 60fps overlay refresh — enough for the overlay, cheaper than per-video-frame precision.
    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            kotlinx.coroutines.delay(16)
        }
    }

    val hasOverlay = frames.size >= 2 && videoWidthPx > 0 && videoHeightPx > 0
    val currentIndex = if (frames.isEmpty()) -1
        else frames.indexOfLast { it.timestampMs <= playbackMs }.coerceAtLeast(0)
    val currentFrame = frames.getOrNull(currentIndex)

    Column(Modifier.fillMaxSize().background(MatteBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< BACK", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp) }
            Text("REPLAY", color = PowerAmber, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
            if (hasOverlay) {
                PathOverlay(
                    frames = frames,
                    currentIndex = currentIndex,
                    videoWidthPx = videoWidthPx,
                    videoHeightPx = videoHeightPx,
                    modifier = Modifier.fillMaxSize()
                )
                HudBox(
                    analysis = analysis,
                    currentVelocity = currentFrame?.velocityMps ?: 0.0,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
        }

        // Transport: play/pause + scrub.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            }) {
                Text(if (isPlaying) "❚❚" else "▶", color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Slider(
                value = if (durationMs > 0L) (playbackMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction ->
                    if (durationMs > 0L) {
                        val target = (fraction * durationMs).toLong()
                        exoPlayer.seekTo(target)
                        playbackMs = target
                    }
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
    }
}

/** The path overlay: static ghost + annotations are cached (rebuilt only on frames/size change);
 * the velocity-coloured progress path and cursor redraw every frame. */
@Composable
private fun PathOverlay(
    frames: List<TrackedFrame>,
    currentIndex: Int,
    videoWidthPx: Int,
    videoHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }
    }
    val peakIndex = remember(frames) { frames.indices.maxByOrNull { frames[it].velocityMps } ?: 0 }
    val stickingIndex = remember(frames) { frames.indices.minByOrNull { frames[it].velocityMps } ?: 0 }
    val startIndex = remember(frames) { frames.indexOfFirst { it.velocityMps > 0.1 }.coerceAtLeast(0) }
    val endIndex = remember(frames) { frames.indexOfLast { it.velocityMps > 0.1 }.let { if (it < 0) frames.lastIndex else it } }
    // Smoothed positions for the DRAWN path (raw tracked positions are jittery). Index-aligned to
    // `frames`; display only — velocity (peak/sticking/colour) still comes from the SG-smoothed
    // analysis numbers, unchanged.
    val pts = remember(frames) { smoothedFramePoints(frames) }

    Box(
        modifier
            // LAYER 1 (ghost) + LAYER 3 (annotations): static, cached.
            .drawWithCache {
                val rect = computeFittedVideoRect(size.width, size.height, videoWidthPx, videoHeightPx)
                fun px(i: Int) = Offset(
                    rect.left + (pts[i].first.toFloat() / videoWidthPx) * rect.width,
                    rect.top + (pts[i].second.toFloat() / videoHeightPx) * rect.height
                )
                val ghost = Path().apply {
                    pts.indices.forEach { i ->
                        val p = px(i)
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                onDrawBehind {
                    drawPath(ghost, Color.White.copy(alpha = 0.6f), style = Stroke(width = 2.dp.toPx()))
                    // Peak (green) + sticking point (red) markers with labels.
                    val peak = px(peakIndex); val sticking = px(stickingIndex)
                    drawCircle(NeonGreen, radius = 12.dp.toPx() / 2, center = peak)
                    drawCircle(DangerRed, radius = 12.dp.toPx() / 2, center = sticking)
                    val startP = px(startIndex); val endP = px(endIndex)
                    drawTriangle(startP, 8.dp.toPx(), up = true, color = Color.White)
                    drawTriangle(endP, 8.dp.toPx(), up = false, color = Color.White)
                    drawContext.canvas.nativeCanvas.apply {
                        drawText("Peak %.2f m/s".format(frames[peakIndex].velocityMps), peak.x + 14f, peak.y - 14f, labelPaint)
                        drawText("Sticking Point", sticking.x + 14f, sticking.y + 28f, labelPaint)
                    }
                }
            }
    )

    // LAYER 2 (velocity-coloured progress) + LAYER 4 (cursor): dynamic, redraw every frame.
    Canvas(modifier) {
        val rect = computeFittedVideoRect(size.width, size.height, videoWidthPx, videoHeightPx)
        fun px(i: Int) = Offset(
            rect.left + (pts[i].first.toFloat() / videoWidthPx) * rect.width,
            rect.top + (pts[i].second.toFloat() / videoHeightPx) * rect.height
        )
        for (i in 1..currentIndex) {
            drawLine(
                color = Color(velocityColorArgb(frames[i].velocityMps.toFloat())),
                start = px(i - 1), end = px(i),
                strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round
            )
        }
        if (currentIndex in pts.indices) {
            val c = px(currentIndex)
            drawCircle(Color.White, radius = 10.dp.toPx() / 2, center = c)
            drawCircle(
                Color(velocityColorArgb(frames[currentIndex].velocityMps.toFloat())),
                radius = 10.dp.toPx() / 2, center = c, style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/**
 * Moving-average smoothing of tracked positions for a clean DRAWN replay path — raw per-frame
 * tracker positions are jittery. Index-aligned to [frames]; display only (the analysis smooths the
 * raw samples separately for the velocity numbers). A short series is returned unchanged. Pure.
 */
internal fun smoothedFramePoints(frames: List<TrackedFrame>, window: Int = 9): List<Pair<Double, Double>> {
    if (frames.size < 3 || window <= 1) return frames.map { it.xPx to it.yPx }
    val half = window / 2
    return frames.indices.map { i ->
        var sx = 0.0; var sy = 0.0; var n = 0
        for (j in (i - half)..(i + half)) {
            if (j in frames.indices) { sx += frames[j].xPx; sy += frames[j].yPx; n++ }
        }
        (sx / n) to (sy / n)
    }
}

@Composable
private fun HudBox(analysis: BarPathAnalysis?, currentVelocity: Double, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        HudLine("Mean Vel", "%.2f m/s".format(analysis?.meanConcentricVelocityMs ?: 0.0))
        HudLine("Peak Vel", "%.2f m/s".format(analysis?.peakVelocityMs ?: 0.0))
        HudLine("ROM", "%.2f m".format((analysis?.rangeOfMotionCm ?: 0.0) / 100.0))
        HudLine("Current", "%.2f m/s".format(currentVelocity))
    }
}

@Composable
private fun HudLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    center: Offset, size: Float, up: Boolean, color: Color
) {
    val half = size / 2
    val tip = if (up) center.y - half else center.y + half
    val base = if (up) center.y + half else center.y - half
    val path = Path().apply {
        moveTo(center.x, tip)
        lineTo(center.x - half, base)
        lineTo(center.x + half, base)
        close()
    }
    drawPath(path, color)
}

// ── Pure, unit-testable helpers ──────────────────────────────────────────────────────────────

internal data class FittedRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * The letterboxed rect a [videoWidthPx]×[videoHeightPx] video occupies inside a
 * [containerW]×[containerH] view when scaled to fit (aspect-preserving, centered) — how
 * PlayerView's default RESIZE_MODE_FIT lays the video out. Centroid coords map into this rect.
 */
internal fun computeFittedVideoRect(containerW: Float, containerH: Float, videoWidthPx: Int, videoHeightPx: Int): FittedRect {
    if (videoWidthPx <= 0 || videoHeightPx <= 0 || containerW <= 0f || containerH <= 0f) {
        return FittedRect(0f, 0f, containerW, containerH)
    }
    val videoAspect = videoWidthPx.toFloat() / videoHeightPx
    val containerAspect = containerW / containerH
    return if (videoAspect > containerAspect) {
        // Video relatively wider → full width, letterbox top/bottom.
        val h = containerW / videoAspect
        FittedRect(0f, (containerH - h) / 2f, containerW, h)
    } else {
        // Video relatively taller → full height, pillarbox left/right.
        val w = containerH * videoAspect
        FittedRect((containerW - w) / 2f, 0f, w, containerH)
    }
}

private val VELOCITY_ANCHORS = listOf(
    0.2f to 0xFFFF0000.toInt(), // red
    0.4f to 0xFFFFA500.toInt(), // orange
    0.6f to 0xFFFFFF00.toInt(), // yellow
    0.8f to 0xFF00FF00.toInt()  // green
)

/**
 * Smooth velocity→colour mapping (opaque ARGB): red (slow / sticking) → orange → yellow → green
 * (fast / explosive), linearly interpolated between the band anchors rather than hard-switching.
 */
internal fun velocityColorArgb(velocityMps: Float): Int {
    val v = velocityMps
    if (v <= VELOCITY_ANCHORS.first().first) return VELOCITY_ANCHORS.first().second
    if (v >= VELOCITY_ANCHORS.last().first) return VELOCITY_ANCHORS.last().second
    for (i in 0 until VELOCITY_ANCHORS.size - 1) {
        val (v0, c0) = VELOCITY_ANCHORS[i]
        val (v1, c1) = VELOCITY_ANCHORS[i + 1]
        if (v in v0..v1) {
            val t = (v - v0) / (v1 - v0)
            return lerpArgb(c0, c1, t)
        }
    }
    return VELOCITY_ANCHORS.last().second
}

private fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + ((cb - ca) * tt)).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
