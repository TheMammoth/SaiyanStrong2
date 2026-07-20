package com.saiyanstrong.presentation.screens.barpath

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import java.io.File

/**
 * The live player: the video plays and loops immediately; the user scrubs to where the lift is and
 * taps the bar to mark it ([onMarkTap] with the tap already in video-pixel space). Tracking runs in
 * the background from the mark point and streams positions into [samples], so the dot follows the
 * bar and a single-colour trail grows behind it — filling in over the first loop, fully live after.
 *
 * Single neon colour (not velocity-coloured): there's no scale in this mode, so a speed colour would
 * imply a real reading it doesn't have (the velocity-coloured [BarPathReplayContent] comes later,
 * after GET VELOCITY NUMBERS). Reuses [computeFittedVideoRect]/[screenToVideoPx] for the letterbox
 * mapping. Ephemeral; this screen IS the on-device tracking-verification tool.
 */
@OptIn(UnstableApi::class)
@Composable
fun BarPathTrackPlaybackContent(
    videoPath: String,
    samples: List<BarPathSample>,
    videoWidthPx: Int,
    videoHeightPx: Int,
    isMarked: Boolean,
    errorMessage: String?,
    onMarkTap: (videoX: Float, videoY: Float, atMs: Long) -> Unit,
    onReMark: () -> Unit,
    onGetVelocityNumbers: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            repeatMode = Player.REPEAT_MODE_ALL // loop for review
            volume = 0f
            playWhenReady = true                 // play immediately — the point is to watch
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    var playbackMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            kotlinx.coroutines.delay(16)
        }
    }

    var boxSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Column(Modifier.fillMaxSize().background(MatteBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isMarked) "TRACKING — watch the dot follow the plate" else "Scrub to the lift, then tap a weight plate on the bar",
                color = PowerAmber, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            if (isMarked) {
                TextButton(onClick = onReMark) { Text("RE-MARK", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { boxSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(videoWidthPx, videoHeightPx) {
                    detectTapGestures { offset ->
                        val videoPx = screenToVideoPx(
                            offset.x, offset.y, boxSize.width, boxSize.height, videoWidthPx, videoHeightPx
                        ) ?: return@detectTapGestures
                        exoPlayer.pause()
                        onMarkTap(videoPx.first, videoPx.second, exoPlayer.currentPosition)
                    }
                }
        ) {
            AndroidView(
                // RESIZE_MODE_FIT = aspect-preserving letterbox, matching computeFittedVideoRect /
                // screenToVideoPx, so the tap maps to the right video pixel and the dot lands where
                // tapped. (Any other resize mode would offset the overlay from the video.)
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (videoWidthPx > 0 && videoHeightPx > 0) {
                TrackTrailOverlay(
                    samples = samples,
                    playbackMs = playbackMs,
                    videoWidthPx = videoWidthPx,
                    videoHeightPx = videoHeightPx,
                    modifier = Modifier.fillMaxSize()
                )
            }
            errorMessage?.let {
                Text(
                    it, color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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

        if (isMarked && samples.size >= 2) {
            SaiyanButton(onClick = onGetVelocityNumbers, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("GET VELOCITY NUMBERS  >>>", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

/** Single-colour growing trail + a marker dot at the current playback moment. Draws nothing until
 * playback reaches a tracked sample (tracking starts at the mark time), so no phantom dot appears
 * before the marked point. Nothing cached — sample counts are small (one rep), redraw is cheap. */
@Composable
private fun TrackTrailOverlay(
    samples: List<BarPathSample>,
    playbackMs: Long,
    videoWidthPx: Int,
    videoHeightPx: Int,
    modifier: Modifier = Modifier
) {
    // Smoothed once per sample-list change; index-aligned to `samples`. Draws a clean line instead
    // of the jittery raw per-frame points.
    val smoothed = remember(samples) { smoothedPathPoints(samples) }
    Canvas(modifier) {
        // Latest sample at/before the current playback time; -1 means playback hasn't reached the
        // first tracked frame yet (before the mark) — draw nothing.
        val currentIndex = samples.indexOfLast { it.timestampMs <= playbackMs }
        if (currentIndex < 0 || currentIndex >= smoothed.size) return@Canvas
        val rect = computeFittedVideoRect(size.width, size.height, videoWidthPx, videoHeightPx)
        fun px(p: Pair<Double, Double>) = Offset(
            rect.left + (p.first.toFloat() / videoWidthPx) * rect.width,
            rect.top + (p.second.toFloat() / videoHeightPx) * rect.height
        )
        for (i in 1..currentIndex) {
            drawLine(
                color = NeonGreen.copy(alpha = 0.9f),
                start = px(smoothed[i - 1]), end = px(smoothed[i]),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
        }
        val c = px(smoothed[currentIndex])
        drawCircle(Color.White, radius = 11.dp.toPx() / 2, center = c)
        drawCircle(NeonGreen, radius = 11.dp.toPx() / 2, center = c, style = Stroke(width = 3.dp.toPx()))
    }
}

/**
 * Which tracked sample corresponds to a given playback time: the latest sample at or before
 * [playbackMs]. -1 for an empty list; clamped to 0 before the first sample. Pure/unit-tested.
 */
internal fun currentSampleIndex(samples: List<BarPathSample>, playbackMs: Long): Int {
    if (samples.isEmpty()) return -1
    return samples.indexOfLast { it.timestampMs <= playbackMs }.coerceAtLeast(0)
}

/**
 * Moving-average smoothing of the tracked positions for a clean DRAWN trail (raw per-frame points
 * are inherently jittery). Index-aligned to [samples]; display only — the analysis smooths the raw
 * samples separately (Savitzky-Golay). A short series is returned unchanged. Pure/unit-tested.
 */
internal fun smoothedPathPoints(samples: List<BarPathSample>, window: Int = 5): List<Pair<Double, Double>> {
    if (samples.size < 3 || window <= 1) return samples.map { it.xPx to it.yPx }
    val half = window / 2
    return samples.indices.map { i ->
        var sx = 0.0; var sy = 0.0; var n = 0
        for (j in (i - half)..(i + half)) {
            if (j in samples.indices) { sx += samples[j].xPx; sy += samples[j].yPx; n++ }
        }
        (sx / n) to (sy / n)
    }
}

/**
 * Maps a tap in the player-view's pixel space to the underlying video's pixel space — the inverse
 * of [computeFittedVideoRect]'s letterbox layout. Returns null for a tap in the letterbox/pillarbox
 * margin (outside the actual video image), so a stray tap there doesn't sample a bogus point. Pure/
 * unit-tested. (videoX, videoY) in full-resolution video pixels.
 */
internal fun screenToVideoPx(
    tapX: Float,
    tapY: Float,
    containerW: Float,
    containerH: Float,
    videoWidthPx: Int,
    videoHeightPx: Int
): Pair<Float, Float>? {
    if (videoWidthPx <= 0 || videoHeightPx <= 0 || containerW <= 0f || containerH <= 0f) return null
    val rect = computeFittedVideoRect(containerW, containerH, videoWidthPx, videoHeightPx)
    if (tapX < rect.left || tapX > rect.left + rect.width || tapY < rect.top || tapY > rect.top + rect.height) {
        return null
    }
    val vx = (tapX - rect.left) / rect.width * videoWidthPx
    val vy = (tapY - rect.top) / rect.height * videoHeightPx
    return vx to vy
}
