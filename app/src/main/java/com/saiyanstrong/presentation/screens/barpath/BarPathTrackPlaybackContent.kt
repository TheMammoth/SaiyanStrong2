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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import java.io.File

/**
 * "Mark then watch": plays the video with the tracked marker drawn on the bar and a growing trail
 * of where it's been, synced to playback — the immediate reward for marking the bar, requiring NO
 * scale or weight. The path is a single neon colour (not velocity-coloured) because there's no
 * scale here, so a speed-based colouring would imply a real reading it doesn't have (the
 * velocity-coloured [BarPathReplayContent] is what you get later, after GET VELOCITY NUMBERS).
 *
 * Architecture: the marker was tracked once up front (in the ViewModel); this plays the video and
 * overlays those pre-computed pixel positions, with a cursor that follows the current playback
 * position. That looks identical to "live tracking" but is smooth and never drops the dot on a
 * frame that failed to track. Reuses [computeFittedVideoRect] from [BarPathReplayContent] for the
 * letterbox mapping. Ephemeral — nothing here is persisted; unverified on a real device (this
 * screen IS the on-device verification tool).
 */
@OptIn(UnstableApi::class)
@Composable
fun BarPathTrackPlaybackContent(
    videoPath: String,
    samples: List<BarPathSample>,
    videoWidthPx: Int,
    videoHeightPx: Int,
    onBack: () -> Unit,
    onGetVelocityNumbers: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            repeatMode = Player.REPEAT_MODE_ALL // loop for review
            volume = 0f
            playWhenReady = true                 // start playing immediately — the point is to watch
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

    val hasOverlay = samples.size >= 2 && videoWidthPx > 0 && videoHeightPx > 0
    val currentIndex = currentSampleIndex(samples, playbackMs)

    Column(Modifier.fillMaxSize().background(MatteBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< RE-MARK", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp) }
            Text("TRACKING — watch the dot follow the bar", color = PowerAmber, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
            if (hasOverlay) {
                TrackTrailOverlay(
                    samples = samples,
                    currentIndex = currentIndex,
                    videoWidthPx = videoWidthPx,
                    videoHeightPx = videoHeightPx,
                    modifier = Modifier.fillMaxSize()
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

        SaiyanButton(onClick = onGetVelocityNumbers, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("GET VELOCITY NUMBERS  >>>", fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
    }
}

/** Single-colour growing trail + a marker dot at the current playback moment. Nothing cached — the
 * trail length changes every frame; sample counts here are small (one rep) so the per-frame redraw
 * is cheap. */
@Composable
private fun TrackTrailOverlay(
    samples: List<BarPathSample>,
    currentIndex: Int,
    videoWidthPx: Int,
    videoHeightPx: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val rect = computeFittedVideoRect(size.width, size.height, videoWidthPx, videoHeightPx)
        fun px(s: BarPathSample) = Offset(
            rect.left + (s.xPx.toFloat() / videoWidthPx) * rect.width,
            rect.top + (s.yPx.toFloat() / videoHeightPx) * rect.height
        )
        for (i in 1..currentIndex) {
            drawLine(
                color = NeonGreen.copy(alpha = 0.9f),
                start = px(samples[i - 1]), end = px(samples[i]),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round
            )
        }
        samples.getOrNull(currentIndex)?.let { s ->
            val c = px(s)
            drawCircle(Color.White, radius = 11.dp.toPx() / 2, center = c)
            drawCircle(NeonGreen, radius = 11.dp.toPx() / 2, center = c, style = Stroke(width = 3.dp.toPx()))
        }
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
