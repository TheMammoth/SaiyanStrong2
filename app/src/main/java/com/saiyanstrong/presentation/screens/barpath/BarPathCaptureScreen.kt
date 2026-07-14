package com.saiyanstrong.presentation.screens.barpath

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.BarPathSample
import com.saiyanstrong.domain.util.GyroTimeline
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.util.barpath.BarPathVideoRecorder

private val MarkerGreen = Color(0xFF39FF14)

/**
 * The VBT flow: record (or import) a set → tap the marker → WATCH the marker tracked over playback
 * with its path highlighted (no scale needed) → optionally add a plate-scale + weight for real
 * velocity numbers → save. Watching the tracked playback is the immediate reward for marking the
 * bar and doubles as the tracking-verification tool; see SPEC.md.
 */
@Composable
fun BarPathCaptureScreen(
    onDone: () -> Unit,
    viewModel: BarPathCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tipsDismissed by viewModel.tipsDismissed.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onDone() }

    // Velocity-coloured replay (reached from RESULTS after GET VELOCITY NUMBERS).
    if (uiState.showReplay && uiState.videoPath != null) {
        BarPathReplayContent(
            videoPath = uiState.videoPath!!,
            frames = uiState.trackedFrames,
            videoWidthPx = uiState.videoWidthPx,
            videoHeightPx = uiState.videoHeightPx,
            analysis = uiState.analysis,
            onBack = viewModel::onHideReplay
        )
        return
    }

    // Mark-then-watch tracked playback — full-screen, single-colour trail, no scale/weight.
    if (uiState.step == CaptureStep.PLAYBACK && uiState.videoPath != null) {
        BarPathTrackPlaybackContent(
            videoPath = uiState.videoPath!!,
            samples = uiState.trackedSamples,
            videoWidthPx = uiState.videoWidthPx,
            videoHeightPx = uiState.videoHeightPx,
            onBack = viewModel::onReMark,
            onGetVelocityNumbers = viewModel::onGetVelocityNumbers
        )
        return
    }

    Box(Modifier.fillMaxSize().background(MatteBlack)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "BAR PATH — SET ANALYSIS",
                color = PowerAmber, fontSize = 15.sp, fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp, modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            when (uiState.step) {
                CaptureStep.RECORDING -> RecordingStep(
                    isStandalone = viewModel.isStandalone,
                    tipsDismissed = tipsDismissed,
                    onDismissTips = viewModel::onDismissTips,
                    onFinished = viewModel::onRecordingFinished,
                    onGalleryVideoPicked = viewModel::onGalleryVideoPicked
                )
                CaptureStep.MARKING -> MarkingStep(
                    uiState = uiState,
                    onTap = viewModel::onMarkerTap,
                    onReMark = viewModel::onReMark,
                    onTrackAndPlay = viewModel::onTrackAndPlay
                )
                CaptureStep.SCALE -> ScaleStep(
                    uiState = uiState,
                    isStandalone = viewModel.isStandalone,
                    onTap = viewModel::onScaleTap,
                    onResetPoints = viewModel::onResetScalePoints,
                    onReferenceLengthChanged = viewModel::onReferenceLengthChanged,
                    onWeightKgChanged = viewModel::onWeightKgChanged,
                    onConfirm = viewModel::onConfirmScale
                )
                CaptureStep.PLAYBACK -> Unit // handled full-screen above
                CaptureStep.PROCESSING -> ProcessingStep()
                CaptureStep.RESULTS -> ResultsStep(
                    analysis = uiState.analysis,
                    calibrationFrame = uiState.calibrationFrame,
                    trackedSamples = uiState.trackedSamples,
                    canReplay = uiState.trackedFrames.size >= 2 && uiState.videoPath != null &&
                        uiState.videoWidthPx > 0,
                    onReplay = viewModel::onShowReplay,
                    canShare = uiState.trackedFrames.size >= 2,
                    onShareRep = viewModel::onShareRep,
                    onSave = viewModel::onSave
                )
                CaptureStep.ERROR -> ErrorStep(message = uiState.errorMessage, onRetry = viewModel::onRetry)
            }
        }

        if (uiState.isPreparingVideo) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Preparing video…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RecordingStep(
    isStandalone: Boolean,
    tipsDismissed: Boolean,
    onDismissTips: () -> Unit,
    onFinished: (String?, GyroTimeline?, Double, Double, Long, String?) -> Unit,
    onGalleryVideoPicked: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { BarPathVideoRecorder() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var isRecording by remember { mutableStateOf(false) }
    // bindCamera() is async — gate RECORD on real bind completion, not permission alone, so a fast
    // tap can't fire startRecording() before videoCapture is set and hit a spurious "failed".
    var isCameraBound by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(!isStandalone) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onGalleryVideoPicked) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Attach a bright, distinctly-colored marker to the bar. Point the camera so the full range of motion stays in frame.",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (!tipsDismissed) {
                BarPathTipsCard(onDismiss = onDismissTips, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (isStandalone) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCamera = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (showCamera) NeonGreen else Color.White.copy(alpha = 0.6f))
                    ) { Text("RECORD", fontSize = 12.sp, fontWeight = FontWeight.Black) }
                    OutlinedButton(
                        onClick = {
                            showCamera = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (!showCamera) NeonGreen else Color.White.copy(alpha = 0.6f))
                    ) { Text("IMPORT FROM GALLERY", fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
            }
            if (!showCamera) return@Column

            if (hasPermission) {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                recorder.bindCamera(
                                    ctx, lifecycleOwner, previewView,
                                    onError = { msg -> snackbarMessage = msg },
                                    onBound = { isCameraBound = true }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(360.dp).background(SaiyanGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission required", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    if (!isRecording) {
                        isRecording = true
                        recorder.startRecording(context) { path, timeline, focal, sensor, startUptime, errorDetail ->
                            onFinished(path, timeline, focal, sensor, startUptime, errorDetail)
                            isRecording = false
                        }
                    } else {
                        recorder.stopRecording()
                    }
                },
                enabled = hasPermission && (isRecording || isCameraBound),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isRecording) DangerRed else NeonGreen)
            ) {
                Text(
                    if (isRecording) "STOP" else if (isCameraBound) "RECORD" else "STARTING CAMERA…",
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BarPathTipsCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SaiyanGray, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TIPS FOR AN ACCURATE READING", color = PowerAmber, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f)
            )
            Text(
                "✕", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp,
                modifier = Modifier.padding(4.dp).pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        listOf(
            "Marker color should stand out from everything else in frame — avoid backgrounds with similar colors.",
            "Film roughly side-on and perpendicular to the bar's path, camera still, full range of motion in frame.",
            "You'll scale using a weight plate (about 45 cm across) in the recorded frame — keep one clearly visible."
        ).forEach { tip ->
            Text(
                "•  $tip", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** Frame + tap-to-sample the marker color, then TRACK & PLAY. No scale/weight here — the whole
 * point is to get to the tracked playback with the least setup. Tapping again re-samples. */
@Composable
private fun MarkingStep(
    uiState: BarPathCaptureUiState,
    onTap: (TapPoint) -> Unit,
    onReMark: () -> Unit,
    onTrackAndPlay: () -> Unit
) {
    val frame = uiState.calibrationFrame
    var boxWidthPx by remember { mutableStateOf(1f) }
    var boxHeightPx by remember { mutableStateOf(1f) }
    val hasMark = uiState.markerSamplePoint != null

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            if (hasMark) "Tap again to re-mark, or TRACK & PLAY to watch it track."
            else "Tap the marker on the bar.",
            color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        if (frame != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                    .onSizeChanged { size -> boxWidthPx = size.width.toFloat(); boxHeightPx = size.height.toFloat() }
                    .pointerInput(frame) {
                        detectTapGestures { offset ->
                            val scaleX = frame.width / boxWidthPx
                            val scaleY = frame.height / boxHeightPx
                            onTap(TapPoint(offset.x * scaleX, offset.y * scaleY))
                        }
                    }
            ) {
                Image(bitmap = frame.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val scaleX = boxWidthPx / frame.width
                    val scaleY = boxHeightPx / frame.height
                    uiState.markerSamplePoint?.let {
                        drawCircle(color = PowerAmber, radius = 14f, center = Offset(it.xPx * scaleX, it.yPx * scaleY))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SaiyanButton(onClick = onTrackAndPlay, modifier = Modifier.fillMaxWidth()) {
            Text("TRACK & PLAY  >>>", fontWeight = FontWeight.Black)
        }

        uiState.errorMessage?.let {
            Text(it, color = DangerRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Optional scale step (reached from playback via GET VELOCITY NUMBERS): tap two plate edges +
 * reference length + weight, then ANALYZE the already-tracked samples. */
@Composable
private fun ScaleStep(
    uiState: BarPathCaptureUiState,
    isStandalone: Boolean,
    onTap: (TapPoint) -> Unit,
    onResetPoints: () -> Unit,
    onReferenceLengthChanged: (String) -> Unit,
    onWeightKgChanged: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val frame = uiState.calibrationFrame
    var boxWidthPx by remember { mutableStateOf(1f) }
    var boxHeightPx by remember { mutableStateOf(1f) }

    val instruction = when {
        uiState.calibrationPoint1 == null -> "Tap one edge of a weight plate."
        uiState.calibrationPoint2 == null -> "Now tap the opposite edge of that same plate."
        else -> "Ready — RESET POINTS to redo, or ANALYZE."
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            instruction,
            color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        if (frame != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                    .onSizeChanged { size -> boxWidthPx = size.width.toFloat(); boxHeightPx = size.height.toFloat() }
                    .pointerInput(frame) {
                        detectTapGestures { offset ->
                            val scaleX = frame.width / boxWidthPx
                            val scaleY = frame.height / boxHeightPx
                            onTap(TapPoint(offset.x * scaleX, offset.y * scaleY))
                        }
                    }
            ) {
                Image(bitmap = frame.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val scaleX = boxWidthPx / frame.width
                    val scaleY = boxHeightPx / frame.height
                    uiState.calibrationPoint1?.let {
                        drawCircle(color = MarkerGreen, radius = 12f, center = Offset(it.xPx * scaleX, it.yPx * scaleY))
                    }
                    uiState.calibrationPoint2?.let {
                        drawCircle(color = MarkerGreen, radius = 12f, center = Offset(it.xPx * scaleX, it.yPx * scaleY))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.referenceLengthCm,
            onValueChange = onReferenceLengthChanged,
            label = { Text("Reference length in cm (a plate is ~45)", fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isStandalone) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.weightKgInput,
                onValueChange = onWeightKgChanged,
                label = { Text("Weight lifted (kg)", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onResetPoints, modifier = Modifier.weight(1f)) {
                Text("RESET POINTS", fontSize = 12.sp)
            }
            SaiyanButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("ANALYZE", fontWeight = FontWeight.Black)
            }
        }

        uiState.errorMessage?.let {
            Text(it, color = DangerRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ProcessingStep() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = NeonGreen)
        Spacer(Modifier.height(16.dp))
        Text("Tracking the marker…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

@Composable
private fun ResultsStep(
    analysis: BarPathAnalysis?,
    calibrationFrame: Bitmap? = null,
    trackedSamples: List<BarPathSample> = emptyList(),
    canReplay: Boolean = false,
    onReplay: () -> Unit = {},
    canShare: Boolean = false,
    onShareRep: () -> Unit = {},
    onSave: () -> Unit
) {
    if (analysis == null) return
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        if (calibrationFrame != null && trackedSamples.size >= 2) {
            Text(
                "TRACKED PATH — should follow the bar in a clean line", color = PowerAmber, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
            TrackedPathPreview(
                frame = calibrationFrame,
                samples = trackedSamples,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        ResultRow("Peak velocity", "%.2f m/s".format(analysis.peakVelocityMs))
        ResultRow("Mean concentric velocity", "%.2f m/s".format(analysis.meanConcentricVelocityMs))
        ResultRow("Zone", analysis.velocityZone.label)
        ResultRow("Peak power", "%.0f W".format(analysis.peakPowerWatts))
        ResultRow("Mean power", "%.0f W".format(analysis.meanPowerWatts))
        ResultRow("Range of motion", "%.1f cm".format(analysis.rangeOfMotionCm))
        ResultRow("Bar path deviation", "%.1f cm".format(analysis.barPathDeviationCm))

        Spacer(Modifier.height(20.dp))
        if (canReplay) {
            OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                Text("▶ REPLAY WITH BAR PATH", fontWeight = FontWeight.Black, color = NeonGreen)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (canShare) {
            OutlinedButton(onClick = onShareRep, modifier = Modifier.fillMaxWidth()) {
                Text("SHARE REP CARD", fontWeight = FontWeight.Black, color = PowerAmber)
            }
            Spacer(Modifier.height(8.dp))
        }
        SaiyanButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("SAVE TO SET  >>>", fontWeight = FontWeight.Black)
        }
    }
}

/**
 * The tracked marker path drawn over the calibration frame — the user's own at-a-glance check that
 * tracking actually followed the bar. A clean, roughly-vertical line means good tracking; a jagged
 * or teleporting line means the tracker latched onto the wrong thing and the numbers shouldn't be
 * trusted. Start = green, end = red.
 */
@Composable
private fun TrackedPathPreview(frame: Bitmap, samples: List<BarPathSample>, modifier: Modifier = Modifier) {
    var boxWidthPx by remember { mutableStateOf(1f) }
    var boxHeightPx by remember { mutableStateOf(1f) }

    Box(
        modifier
            .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
            .onSizeChanged { size -> boxWidthPx = size.width.toFloat(); boxHeightPx = size.height.toFloat() }
    ) {
        Image(bitmap = frame.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val scaleX = boxWidthPx / frame.width
            val scaleY = boxHeightPx / frame.height
            val points = samples.map { Offset(it.xPx.toFloat() * scaleX, it.yPx.toFloat() * scaleY) }

            for (i in 0 until points.size - 1) {
                drawLine(
                    color = NeonGreen.copy(alpha = 0.75f),
                    start = points[i], end = points[i + 1],
                    strokeWidth = 4f
                )
            }
            points.firstOrNull()?.let { drawCircle(color = NeonGreen, radius = 8f, center = it) }
            points.lastOrNull()?.let { drawCircle(color = DangerRed, radius = 8f, center = it) }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ErrorStep(message: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message ?: "Something went wrong.", color = DangerRed, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("TRY AGAIN") }
    }
}
