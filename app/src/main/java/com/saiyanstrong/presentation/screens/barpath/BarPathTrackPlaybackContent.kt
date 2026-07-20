package com.saiyanstrong.presentation.screens.barpath

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    isTracking: Boolean,
    trackingProgress: Float,
    placementFrame: android.graphics.Bitmap?,
    errorMessage: String?,
    onConfirmTrack: (centerX: Float, centerY: Float, sidePx: Float, atMs: Long) -> Unit,
    onPlaceFrame: (atMs: Long) -> Unit,
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

    var boxSize by remember { mutableStateOf(Size.Zero) }

    // The adjustable start box, in VIDEO-pixel space (survives container resize; maps straight to the
    // tracker). Null until the user taps to drop it. `placedAtMs` is the playback time it was placed
    // at — tracking starts there. Cleared on RE-MARK (isMarked/isTracking both false).
    var pendingBox by remember { mutableStateOf<PendingBox?>(null) }
    var placedAtMs by remember { mutableLongStateOf(0L) }
    val placing = !isMarked && !isTracking

    // On tracking completion, play the finished path from the mark point.
    LaunchedEffect(isMarked, samples.size) {
        if (isMarked && samples.size >= 2) {
            exoPlayer.seekTo(samples.first().timestampMs)
            exoPlayer.play()
        }
    }

    Column(Modifier.fillMaxSize().background(MatteBlack)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    isTracking -> "Tracking the plate…"
                    isMarked -> "TRACKING — watch the dot follow the plate"
                    pendingBox != null -> "Drag the box onto the hub, pinch to size it, then TRACK"
                    else -> "Scrub to the lift, then tap the bar end / sleeve hub (the bright centre)"
                },
                color = PowerAmber, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            if (isMarked) {
                TextButton(onClick = { pendingBox = null; onReMark() }) {
                    Text("RE-MARK", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
                // Tap: drop / reposition the start box (only while placing).
                .pointerInput(videoWidthPx, videoHeightPx, placing) {
                    if (!placing) return@pointerInput
                    detectTapGestures { offset ->
                        val videoPx = screenToVideoPx(
                            offset.x, offset.y, boxSize.width, boxSize.height, videoWidthPx, videoHeightPx
                        ) ?: return@detectTapGestures
                        exoPlayer.pause()
                        placedAtMs = exoPlayer.currentPosition
                        // Hub-sized default (a bar end / collar / sleeve hub) — pinch out for a plate.
                        val defaultSide = minOf(videoWidthPx, videoHeightPx) * 0.07f
                        pendingBox = PendingBox(videoPx.first, videoPx.second, pendingBox?.side ?: defaultSide)
                        onPlaceFrame(exoPlayer.currentPosition)
                    }
                }
                // Drag to move, pinch to resize the box (only while placing and a box exists).
                .pointerInput(videoWidthPx, videoHeightPx, placing) {
                    if (!placing) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        val box = pendingBox ?: return@detectTransformGestures
                        val rect = computeFittedVideoRect(boxSize.width, boxSize.height, videoWidthPx, videoHeightPx)
                        if (rect.width <= 0f || rect.height <= 0f) return@detectTransformGestures
                        val dxVideo = pan.x * (videoWidthPx / rect.width)
                        val dyVideo = pan.y * (videoHeightPx / rect.height)
                        pendingBox = box.copy(
                            cx = box.cx + dxVideo,
                            cy = box.cy + dyVideo,
                            side = (box.side * zoom).coerceIn(24f, minOf(videoWidthPx, videoHeightPx).toFloat())
                        )
                    }
                }
        ) {
            AndroidView(
                // RESIZE_MODE_FIT = aspect-preserving letterbox, matching computeFittedVideoRect /
                // screenToVideoPx, so a tap maps to the right video pixel and overlays line up.
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
                if (isMarked) {
                    TrackTrailOverlay(
                        samples = samples,
                        playbackMs = playbackMs,
                        videoWidthPx = videoWidthPx,
                        videoHeightPx = videoHeightPx,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (placing) {
                    pendingBox?.let { box ->
                        StartBoxOverlay(box, videoWidthPx, videoHeightPx, Modifier.fillMaxSize())
                    }
                }
            }
            // Magnifier loupe — zoomed crop of the paused frame under the box centre, so a small
            // hub can be landed precisely. Pinned top-start, away from where the box usually sits.
            if (placing && placementFrame != null) {
                pendingBox?.let { box ->
                    PlacementLoupe(
                        frame = placementFrame,
                        centerX = box.cx, centerY = box.cy,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    )
                }
            }
            if (isTracking) {
                Column(
                    Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { trackingProgress.coerceIn(0f, 1f) },
                        color = NeonGreen, modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "Tracking… ${(trackingProgress * 100).toInt()}%",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
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

        when {
            placing && pendingBox != null -> {
                SaiyanButton(
                    onClick = { pendingBox?.let { onConfirmTrack(it.cx, it.cy, it.side, placedAtMs) } },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text("TRACK THIS PLATE  >>>", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
            isMarked && samples.size >= 2 -> {
                SaiyanButton(onClick = onGetVelocityNumbers, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("GET VELOCITY NUMBERS  >>>", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

/** The adjustable start box, centre + side in VIDEO-pixel space. */
private data class PendingBox(val cx: Float, val cy: Float, val side: Float)

/** Draws the movable/resizable start box over the paused frame: neon rounded-rect outline + centre
 * crosshair. Mapped from video-px through the same fitted-rect letterbox math as everything else. */
@Composable
private fun StartBoxOverlay(
    box: PendingBox,
    videoWidthPx: Int,
    videoHeightPx: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val rect = computeFittedVideoRect(size.width, size.height, videoWidthPx, videoHeightPx)
        val scaleX = rect.width / videoWidthPx
        val scaleY = rect.height / videoHeightPx
        val cx = rect.left + box.cx * scaleX
        val cy = rect.top + box.cy * scaleY
        val halfW = box.side * scaleX / 2f
        val halfH = box.side * scaleY / 2f
        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset(cx - halfW, cy - halfH),
            size = Size(halfW * 2f, halfH * 2f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
        val cross = 8.dp.toPx()
        drawLine(NeonGreen, Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 2.dp.toPx())
        drawLine(NeonGreen, Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 2.dp.toPx())
    }
}

/**
 * Circular magnifier of the paused frame around the box centre ([centerX],[centerY] in bitmap-px),
 * zoomed 3×, with a crosshair on the exact tracked point — so a small hub can be centred precisely.
 * The crosshair is placed at the box centre's position WITHIN the (edge-clamped) magnified window,
 * so it stays accurate even when the window shifts near a frame edge.
 */
@Composable
private fun PlacementLoupe(
    frame: android.graphics.Bitmap,
    centerX: Float,
    centerY: Float,
    modifier: Modifier = Modifier
) {
    val image = remember(frame) { frame.asImageBitmap() }
    Canvas(modifier.size(110.dp)) {
        val loupePx = size.minDimension.toInt()
        val src = loupeSource(centerX.toDouble(), centerY.toDouble(), frame.width, frame.height, loupePx, 3f)
            ?: return@Canvas
        val circle = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        clipPath(circle) {
            drawImage(
                image = image,
                srcOffset = IntOffset(src.x, src.y),
                srcSize = IntSize(src.size, src.size),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(loupePx, loupePx)
            )
        }
        drawCircle(NeonGreen, radius = size.minDimension / 2f, style = Stroke(width = 3.dp.toPx()))
        // Box centre mapped into the loupe (accounts for edge-clamping of the source window).
        val cxL = ((centerX - src.x) / src.size) * loupePx
        val cyL = ((centerY - src.y) / src.size) * loupePx
        val cross = 10.dp.toPx()
        drawLine(NeonGreen, Offset(cxL - cross, cyL), Offset(cxL + cross, cyL), strokeWidth = 2.dp.toPx())
        drawLine(NeonGreen, Offset(cxL, cyL - cross), Offset(cxL, cyL + cross), strokeWidth = 2.dp.toPx())
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
internal fun smoothedPathPoints(samples: List<BarPathSample>, window: Int = 9): List<Pair<Double, Double>> {
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

/** Source crop for the placement magnifier loupe, in bitmap pixels (integer offset + size). */
internal data class LoupeSrc(val x: Int, val y: Int, val size: Int)

/**
 * The square region of the paused frame to magnify in the loupe: a [loupePx]/[zoom]-sized window
 * centred on the box centre ([centerX], [centerY] in bitmap px), shifted (not shrunk) to stay
 * inside the [bitmapW]×[bitmapH] bitmap so it never reads out of bounds near an edge. Null for a
 * degenerate bitmap. Pure/unit-tested; the Compose drawImage src→dst is the shell.
 */
internal fun loupeSource(
    centerX: Double,
    centerY: Double,
    bitmapW: Int,
    bitmapH: Int,
    loupePx: Int,
    zoom: Float
): LoupeSrc? {
    if (bitmapW <= 0 || bitmapH <= 0 || loupePx <= 0 || zoom <= 0f) return null
    val src = (loupePx / zoom).toInt().coerceIn(1, minOf(bitmapW, bitmapH))
    val half = src / 2.0
    val x = (centerX - half).toInt().coerceIn(0, bitmapW - src)
    val y = (centerY - half).toInt().coerceIn(0, bitmapH - src)
    return LoupeSrc(x, y, src)
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
