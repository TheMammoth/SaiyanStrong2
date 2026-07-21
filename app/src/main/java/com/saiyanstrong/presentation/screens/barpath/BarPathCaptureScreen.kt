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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

    // Live player — full-screen: video plays immediately, tap the bar to mark + track live.
    if (uiState.step == CaptureStep.PLAYER && uiState.videoPath != null) {
        val liveSamples by viewModel.liveSamples.collectAsStateWithLifecycle()
        BarPathTrackPlaybackContent(
            videoPath = uiState.videoPath!!,
            samples = liveSamples,
            videoWidthPx = uiState.videoWidthPx,
            videoHeightPx = uiState.videoHeightPx,
            isMarked = uiState.trackedSamples.size >= 2 && !uiState.isTracking,
            isTracking = uiState.isTracking,
            trackingProgress = uiState.trackingProgress,
            placementFrame = uiState.placementFrame,
            plateSelectionA = uiState.markA?.selection,
            plateSelectionB = uiState.markB?.selection,
            errorMessage = uiState.errorMessage,
            onSegmentTap = viewModel::onSegmentTap,
            onConfirmTrack = viewModel::onConfirmTrack,
            onReMark = viewModel::onReMark,
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
                    onGalleryVideoPicked = viewModel::onGalleryVideoPicked,
                    onCalibrated = viewModel::onMarkerCalibrated
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
                CaptureStep.PLAYER -> Unit // handled full-screen above
                CaptureStep.PROCESSING -> ProcessingStep(progress = uiState.trackingProgress)
                CaptureStep.RESULTS -> ResultsStep(
                    analysis = uiState.analysis,
                    repResults = uiState.repResults,
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
    onGalleryVideoPicked: (Uri) -> Unit,
    onCalibrated: (com.saiyanstrong.util.barpath.MarkerColorProfile) -> Unit
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

    // Pre-record marker calibration ("train the marker"): live Preview+Analysis session, tap the
    // marker → mask/region overlay + clash warning, START RECORDING gated on a stable lock. On
    // transition, the recording camera (Preview+Video) rebinds on the same PreviewView.
    var calibrating by remember { mutableStateOf(true) }
    var calib by remember { mutableStateOf<com.saiyanstrong.util.barpath.CalibrationFrameResult?>(null) }
    var previewRef by remember { mutableStateOf<PreviewView?>(null) }
    var tapFlash by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(tapFlash) { if (tapFlash != null) { kotlinx.coroutines.delay(700); tapFlash = null } }
    LaunchedEffect(calibrating) {
        if (!calibrating) {
            previewRef?.let { pv ->
                isCameraBound = false
                recorder.bindCamera(
                    context, lifecycleOwner, pv,
                    onError = { snackbarMessage = it }, onBound = { isCameraBound = true }
                )
            }
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

            if (calibrating) {
                calib?.advice?.let { CalibrationAdviceBanner(it, modifier = Modifier.padding(bottom = 8.dp)) }
            }

            if (hasPermission) {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewRef = previewView
                                // Start in CALIBRATION (Preview + Analysis). Recording rebinds this
                                // same PreviewView once the marker is locked (LaunchedEffect above).
                                recorder.bindCalibration(
                                    ctx, lifecycleOwner, previewView,
                                    onResult = { calib = it },
                                    onError = { msg -> snackbarMessage = msg }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (calibrating) {
                        CalibrationOverlay(
                            result = calib,
                            tapFlash = tapFlash,
                            modifier = Modifier.fillMaxSize().pointerInput(previewRef) {
                                detectTapGestures { offset ->
                                    previewRef?.let { pv ->
                                        val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                        recorder.requestCalibrationSample(point.x, point.y)
                                        tapFlash = offset
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(360.dp).background(SaiyanGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera permission required", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (calibrating) {
                CalibrationControls(
                    result = calib,
                    onRetap = { recorder.resetCalibration(); calib = null },
                    onStartRecording = {
                        calib?.profile?.let(onCalibrated)
                        calibrating = false
                    }
                )
            } else {
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

/**
 * The live calibration overlay drawn over the camera preview: the detected marker region glows
 * green, background clashes of the same color glow red, and a white flash marks the last tap. This
 * IS the "see it isolate" feedback — a marker that lights up cleanly with a dark background tracks
 * well; red patches elsewhere mean the color clashes.
 *
 * Regions are in normalized [0,1] analysis-frame coords; they're scaled onto the preview box
 * assuming Preview and ImageAnalysis share a crop (the known CameraX caveat, unverified on device).
 */
@Composable
private fun CalibrationOverlay(
    result: com.saiyanstrong.util.barpath.CalibrationFrameResult?,
    tapFlash: Offset?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        result?.clashRegions?.forEach { r ->
            drawRect(
                color = DangerRed.copy(alpha = 0.30f),
                topLeft = Offset(r.nx * size.width, r.ny * size.height),
                size = Size(r.nw * size.width, r.nh * size.height)
            )
        }
        result?.markerRegion?.let { r ->
            val topLeft = Offset(r.nx * size.width, r.ny * size.height)
            val boxSize = Size(r.nw * size.width, r.nh * size.height)
            drawRect(color = NeonGreen.copy(alpha = 0.30f), topLeft = topLeft, size = boxSize)
            drawRect(color = NeonGreen, topLeft = topLeft, size = boxSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        tapFlash?.let { drawCircle(color = Color.White, radius = 26f, center = it, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)) }
    }
}

/** Maps a nameable palette colour to a representative swatch colour for the recommendation UI.
 * These are data swatches (showing which real-world colour to use), not theme chrome — a fixed,
 * intentional mapping, not a hardcoded-colour rule violation. */
private fun swatchColor(name: String): Color = when (name) {
    "Blue" -> Color(0xFF2979FF)
    "Purple" -> Color(0xFFAA00FF)
    "Magenta" -> Color(0xFFFF00AA)
    "Cyan" -> Color(0xFF00E5FF)
    "Orange" -> Color(0xFFFF9100)
    "Red" -> Color(0xFFFF1744)
    "Yellow" -> Color(0xFFFFEA00)
    "Green" -> Color(0xFF00E676)
    else -> Color.White
}

/** The "read the room" banner: which marker colour to use here (most absent from the scene) and
 * which to avoid (already present). Live on the calibrate screen, before/regardless of any tap. */
@Composable
private fun CalibrationAdviceBanner(
    advice: com.saiyanstrong.util.barpath.MarkerAdvice,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().background(SaiyanGray, RoundedCornerShape(6.dp)).padding(10.dp)
    ) {
        Text(
            "BEST MARKER COLOUR HERE", color = PowerAmber, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            advice.recommended.forEach { c -> ColourSwatch(c.name, big = true) }
        }
        if (advice.avoid.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("avoid:", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                advice.avoid.forEach { c -> ColourSwatch(c.name, big = false) }
            }
        }
    }
}

@Composable
private fun ColourSwatch(name: String, big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(if (big) 16.dp else 11.dp)) {
            drawCircle(color = swatchColor(name))
        }
        Spacer(Modifier.size(5.dp))
        Text(
            name, color = Color.White.copy(alpha = if (big) 0.9f else 0.5f),
            fontSize = if (big) 13.sp else 11.sp,
            fontWeight = if (big) FontWeight.Black else FontWeight.Normal
        )
    }
}

/** Status + RE-TAP + START RECORDING for the calibration step. START enables only on a stable lock;
 * the clash warning + marker grade are advisory (do not block recording), per SPEC.md. */
@Composable
private fun CalibrationControls(
    result: com.saiyanstrong.util.barpath.CalibrationFrameResult?,
    onRetap: () -> Unit,
    onStartRecording: () -> Unit
) {
    val hasProfile = result?.profile != null
    val locked = result?.locked == true
    val status = when {
        !hasProfile -> "Point at your marker and tap it to train the color."
        !locked -> "Locking on… hold steady."
        else -> "Marker locked ✓ — ready to record."
    }
    val topPick = result?.advice?.recommended?.firstOrNull()?.name
    Column(Modifier.fillMaxWidth()) {
        Text(
            status,
            color = if (locked) NeonGreen else PowerAmber, fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        result?.markerGrade?.let { grade ->
            val (label, color) = when (grade) {
                com.saiyanstrong.util.barpath.MarkerGrade.GOOD -> "Marker: GOOD ✓ — stands out from the scene" to NeonGreen
                com.saiyanstrong.util.barpath.MarkerGrade.OK -> "Marker: OK — usable, but not ideal" to PowerAmber
                com.saiyanstrong.util.barpath.MarkerGrade.BAD ->
                    ("Marker: BAD — clashes with the scene" + (topPick?.let { ", try $it" } ?: "")) to DangerRed
            }
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (result?.clash == true) {
            Text(
                "⚠ This color also shows up in the background (red). Move it out of frame or use a different marker color.",
                color = DangerRed, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetap, enabled = hasProfile, modifier = Modifier.weight(1f)) {
                Text("RE-TAP", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            SaiyanButton(onClick = onStartRecording, enabled = locked, modifier = Modifier.weight(1f)) {
                Text("START RECORDING", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
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
private fun ProcessingStep(progress: Float = 0f) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // A determinate ring once tracking reports progress, so a slow extraction never looks
        // frozen; indeterminate before the first frame lands (and for the fast analyze pass).
        if (progress > 0f) {
            CircularProgressIndicator(progress = { progress }, color = NeonGreen)
        } else {
            CircularProgressIndicator(color = NeonGreen)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (progress > 0f) "Tracking the marker… ${(progress * 100).toInt()}%" else "Tracking the marker…",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
        )
    }
}

@Composable
private fun ResultsStep(
    analysis: BarPathAnalysis?,
    repResults: List<RepResult> = emptyList(),
    calibrationFrame: Bitmap? = null,
    trackedSamples: List<BarPathSample> = emptyList(),
    canReplay: Boolean = false,
    onReplay: () -> Unit = {},
    canShare: Boolean = false,
    onShareRep: () -> Unit = {},
    onSave: () -> Unit
) {
    if (analysis == null) return
    val isSet = repResults.size >= 2
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
        if (isSet) {
            SetResultsSection(repResults)
            Spacer(Modifier.height(16.dp))
            Text(
                "BEST REP", color = PowerAmber, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp)
            )
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

/** Per-rep list + a velocity-drop chart for a multi-rep set — the set's velocity curve is the core
 * VBT signal (velocity falling across reps = fatigue). */
@Composable
private fun SetResultsSection(reps: List<RepResult>) {
    Text(
        "SET — ${reps.size} REPS", color = NeonGreen, fontSize = 13.sp,
        fontWeight = FontWeight.Black, letterSpacing = 1.sp
    )
    Spacer(Modifier.height(8.dp))
    VelocityDropChart(
        reps.map { it.analysis.meanConcentricVelocityMs.toFloat() },
        modifier = Modifier.fillMaxWidth().height(120.dp)
    )
    Spacer(Modifier.height(8.dp))
    reps.forEach { rep ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rep ${rep.index}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("%.2f m/s".format(rep.analysis.meanConcentricVelocityMs), color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("pk %.2f".format(rep.analysis.peakVelocityMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(0.9f))
            Text("%.0f cm".format(rep.analysis.rangeOfMotionCm), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.weight(0.8f))
        }
    }
}

/** Mean concentric velocity per rep — bars + a connecting line so the drop across the set is obvious. */
@Composable
private fun VelocityDropChart(meanVels: List<Float>, modifier: Modifier = Modifier) {
    if (meanVels.isEmpty()) return
    val maxV = meanVels.max().coerceAtLeast(0.01f)
    Canvas(modifier) {
        val n = meanVels.size
        val gap = 6.dp.toPx()
        val barW = ((size.width - gap * (n + 1)) / n).coerceAtLeast(1f)
        val usableH = size.height * 0.9f
        fun barX(i: Int) = gap + i * (barW + gap)
        meanVels.forEachIndexed { i, v ->
            val h = (v / maxV) * usableH
            drawRect(color = NeonGreen.copy(alpha = 0.55f), topLeft = Offset(barX(i), size.height - h), size = Size(barW, h))
        }
        val path = Path()
        meanVels.forEachIndexed { i, v ->
            val cx = barX(i) + barW / 2f
            val cy = size.height - (v / maxV) * usableH
            if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        }
        drawPath(path, PowerAmber, style = Stroke(width = 2.dp.toPx()))
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
