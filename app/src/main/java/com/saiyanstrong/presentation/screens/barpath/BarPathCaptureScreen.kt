package com.saiyanstrong.presentation.screens.barpath

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.util.barpath.BarPathVideoRecorder
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.util.barpath.HighSpeedCapabilityChecker
import com.saiyanstrong.util.barpath.HighSpeedTier
import com.saiyanstrong.util.barpath.LiveFrameResult
import kotlinx.coroutines.launch

private val MarkerGreen = Color(0xFF39FF14)
private val MarkerBlue = Color(0xFF2E9EFF)

@Composable
fun BarPathCaptureScreen(
    onDone: () -> Unit,
    viewModel: BarPathCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tipsDismissed by viewModel.tipsDismissed.collectAsStateWithLifecycle()
    val highSpeedPreference by viewModel.highSpeedModeEnabled.collectAsStateWithLifecycle()
    val liveTracking by viewModel.liveTracking.collectAsStateWithLifecycle()
    val liveVelocity by viewModel.liveVelocity.collectAsStateWithLifecycle()
    val livePhase by viewModel.livePhase.collectAsStateWithLifecycle()
    val liveColorLockedOn by viewModel.liveColorLockedOn.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onDone() }

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
                    highSpeedPreference = highSpeedPreference,
                    onHighSpeedPreferenceChanged = viewModel::onHighSpeedModeChanged,
                    liveTracking = liveTracking,
                    liveVelocity = liveVelocity,
                    livePhase = livePhase,
                    liveColorLockedOn = liveColorLockedOn,
                    onLiveResult = viewModel::onLiveResult,
                    onRetapColor = viewModel::onRetapColor,
                    onFinished = viewModel::onRecordingFinished,
                    onGalleryVideoPicked = viewModel::onGalleryVideoPicked
                )
                CaptureStep.CALIBRATING -> CalibrationStep(
                    uiState = uiState,
                    isStandalone = viewModel.isStandalone,
                    onTap = viewModel::onCalibrationTap,
                    onResetPoints = viewModel::onResetCalibrationPoints,
                    onDualMarkerModeChanged = viewModel::onDualMarkerModeChanged,
                    onReferenceDistanceChanged = viewModel::onReferenceDistanceChanged,
                    onReferenceLengthChanged = viewModel::onReferenceLengthChanged,
                    onWeightKgChanged = viewModel::onWeightKgChanged,
                    onConfirm = viewModel::onConfirmCalibration
                )
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
    highSpeedPreference: Boolean?,
    onHighSpeedPreferenceChanged: (Boolean) -> Unit,
    liveTracking: Boolean = false,
    liveVelocity: Float = 0f,
    livePhase: LiftPhase = LiftPhase.IDLE,
    liveColorLockedOn: Boolean = false,
    onLiveResult: (LiveFrameResult) -> Unit = {},
    onRetapColor: () -> Unit = {},
    onFinished: (String?, com.saiyanstrong.domain.util.GyroTimeline?, Double, Double, Long) -> Unit,
    onGalleryVideoPicked: (android.net.Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember { BarPathVideoRecorder() }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var tapHighlightOffset by remember { mutableStateOf<Offset?>(null) }
    val tapHighlightAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

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
    var showCamera by remember { mutableStateOf(!isStandalone) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onGalleryVideoPicked) }

    // CameraConstrainedHighSpeedCaptureSession (raw Camera2) isn't reachable through this app's
    // CameraX pipeline — this checks CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES instead, to see if a
    // high frame rate can be requested via Camera2Interop on a normal session. See
    // HighSpeedCapabilityChecker/BarPathVideoRecorder for the full tradeoff.
    var highSpeedTier by remember { mutableStateOf<HighSpeedTier?>(null) }
    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            highSpeedTier = HighSpeedCapabilityChecker.check(providerFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }
    val deviceSupportsHighSpeed = highSpeedTier != null && highSpeedTier != HighSpeedTier.STANDARD_30
    // Defaults to OFF: forcing a fixed high frame rate via Camera2Interop is the most device-fragile
    // part of the capture path and a suspected crash source on devices that only advertise a
    // *variable* fps range. Opt-in only — the user must flip the toggle to enable it.
    val effectiveHighSpeedEnabled = highSpeedPreference ?: false

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

        if (deviceSupportsHighSpeed) {
            HighSpeedToggleRow(
                tier = highSpeedTier!!,
                enabled = effectiveHighSpeedEnabled,
                onToggle = onHighSpeedPreferenceChanged,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (hasPermission) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            previewViewRef = previewView
                            recorder.bindCamera(
                                ctx, lifecycleOwner, previewView,
                                highSpeedEnabled = effectiveHighSpeedEnabled,
                                onHighSpeedUnavailable = {
                                    snackbarMessage = "High-speed mode unavailable on this device, using 30fps"
                                },
                                onError = { msg -> snackbarMessage = msg },
                                onLiveResult = onLiveResult
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Tap-to-lock marker color: only listens while not yet locked on, so an accidental
                // tap mid-recording can't silently re-sample. RE-TAP re-arms it explicitly.
                if (!liveColorLockedOn) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val pv = previewViewRef
                                if (pv != null && pv.width > 0 && pv.height > 0) {
                                    val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                                    recorder.requestColorSample(point.x, point.y)
                                    tapHighlightOffset = offset
                                    coroutineScope.launch {
                                        tapHighlightAlpha.snapTo(1f)
                                        tapHighlightAlpha.animateTo(0f, animationSpec = tween(1500))
                                    }
                                }
                            }
                        }
                    )
                }
                tapHighlightOffset?.let { point ->
                    if (tapHighlightAlpha.value > 0f) {
                        Canvas(Modifier.fillMaxSize()) {
                            val half = 15.dp.toPx()
                            drawRect(
                                color = Color.White.copy(alpha = tapHighlightAlpha.value),
                                topLeft = Offset(point.x - half, point.y - half),
                                size = Size(half * 2, half * 2),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            )
                        }
                    }
                }
                LiveTrackingReadout(
                    tracking = liveTracking,
                    velocity = liveVelocity,
                    phase = livePhase,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )
                if (liveColorLockedOn) {
                    OutlinedButton(
                        onClick = {
                            onRetapColor()
                            tapHighlightOffset = null
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PowerAmber)
                    ) { Text("RE-TAP", fontSize = 11.sp, fontWeight = FontWeight.Black) }
                }
                if (livePhase == LiftPhase.IDLE || livePhase == LiftPhase.COMPLETE) {
                    SaiyanButton(
                        onClick = { recorder.startRep() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    ) {
                        Text("START REP", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
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

        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                if (!isRecording) {
                    isRecording = true
                    recorder.startRecording(context) { path, timeline, focal, sensor, startUptime ->
                        onFinished(path, timeline, focal, sensor, startUptime)
                        isRecording = false
                    }
                } else {
                    recorder.stopRecording()
                }
            },
            enabled = hasPermission,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isRecording) DangerRed else NeonGreen)
        ) {
            Text(if (isRecording) "STOP" else "RECORD", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Live analysis readout during recording (slice 1 of the live loop). Shows whether the marker is
 * being tracked frame-to-frame and its smoothed speed. The speed is UNCALIBRATED — a relative
 * value, not true m/s, until pre-record scale calibration exists — so it's labelled "~" and
 * "rel", not "m/s", to avoid implying a real physical reading.
 */
@Composable
private fun LiveTrackingReadout(tracking: Boolean, velocity: Float, phase: LiftPhase, modifier: Modifier = Modifier) {
    val (phaseLabel, phaseColor) = when (phase) {
        LiftPhase.IDLE -> "TAP START REP" to Color.White.copy(alpha = 0.6f)
        LiftPhase.SETTLING -> "SETTLING…" to PowerAmber
        LiftPhase.READY -> "● READY" to NeonGreen
        LiftPhase.MOVING -> "▲ MOVING" to NeonGreen
        LiftPhase.COMPLETE -> "REP COMPLETE" to PowerAmber
    }
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(phaseLabel, color = phaseColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text(
            if (tracking) "● tracking" else "○ searching",
            color = if (tracking) NeonGreen.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )
        // Velocity only meaningful in MOVING (gated by the phase machine). Uncalibrated → "rel".
        if (phase == LiftPhase.MOVING) {
            Text(
                "~%.2f (rel. speed)".format(velocity),
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HighSpeedToggleRow(
    tier: HighSpeedTier,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetFps = if (tier == HighSpeedTier.FPS_120) 120 else 60
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "HIGH-SPEED MODE (${targetFps}FPS)", color = Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Requests a higher frame rate for finer velocity detail — not guaranteed on every device.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = NeonGreen.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun BarPathTipsCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
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
            "Calibrate against something rigid, in the same plane as the bar's travel (a plate diameter or bar sleeve).",
            "Keep the camera still, perpendicular to the bar's path, with the full range of motion in frame."
        ).forEach { tip ->
            Text(
                "•  $tip", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun CalibrationStep(
    uiState: BarPathCaptureUiState,
    isStandalone: Boolean,
    onTap: (TapPoint) -> Unit,
    onResetPoints: () -> Unit,
    onDualMarkerModeChanged: (Boolean) -> Unit,
    onReferenceDistanceChanged: (String) -> Unit,
    onReferenceLengthChanged: (String) -> Unit,
    onWeightKgChanged: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val frame = uiState.calibrationFrame
    var boxWidthPx by remember { mutableStateOf(1f) }
    var boxHeightPx by remember { mutableStateOf(1f) }

    val instruction = when {
        uiState.markerSamplePoint == null -> "Tap the primary marker on the bar in this frame."
        uiState.useDualMarkerMode && uiState.markerBSamplePoint == null ->
            "Tap the second reference marker on the bar (other end of the sleeve)."
        uiState.useDualMarkerMode -> "Ready — RESET POINTS to redo, or ANALYZE to continue."
        uiState.calibrationPoint1 == null || uiState.calibrationPoint2 == null ->
            "Now tap two points of known length (a plate diameter, the bar sleeve)."
        else -> "Ready — RESET POINTS to redo, or ANALYZE to continue."
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onDualMarkerModeChanged(true) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (uiState.useDualMarkerMode) NeonGreen else Color.White.copy(alpha = 0.6f)
                )
            ) { Text("DUAL-MARKER (RECOMMENDED)", fontSize = 10.sp, fontWeight = FontWeight.Black) }
            OutlinedButton(
                onClick = { onDualMarkerModeChanged(false) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (!uiState.useDualMarkerMode) NeonGreen else Color.White.copy(alpha = 0.6f)
                )
            ) { Text("MANUAL CALIBRATION", fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }

        Text(
            instruction,
            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)
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
                    uiState.markerBSamplePoint?.let {
                        drawCircle(color = MarkerBlue, radius = 14f, center = Offset(it.xPx * scaleX, it.yPx * scaleY))
                    }
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

        if (uiState.useDualMarkerMode) {
            OutlinedTextField(
                value = uiState.referenceDistanceCm,
                onValueChange = onReferenceDistanceChanged,
                label = { Text("Distance between markers (cm)", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = uiState.referenceLengthCm,
                onValueChange = onReferenceLengthChanged,
                label = { Text("Reference length (cm)", fontSize = 12.sp) },
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
        Text("Tracking the marker and computing velocity...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
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
                "TRACKED PATH", color = PowerAmber, fontSize = 11.sp,
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

        val ppmPoints = trackedSamples.mapNotNull { it.perFramePixelsPerMeter }
        if (ppmPoints.size >= 2) {
            Spacer(Modifier.height(16.dp))
            Text(
                "SCALE (PX/M) OVER REP — FLAT = NO DEPTH DRIFT", color = PowerAmber, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 6.dp)
            )
            PpmChart(points = ppmPoints, modifier = Modifier.fillMaxWidth().height(80.dp))
        }

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

/** Small line chart of dual-marker per-frame pixels-per-meter over the rep — a flat line means
 * no depth drift was detected; a curve means it was detected and corrected for. */
@Composable
private fun PpmChart(points: List<Double>, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(SaiyanGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        if (points.size < 2) return@Canvas
        val minVal = points.min()
        val maxVal = points.max()
        val range = (maxVal - minVal).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)

        val offsets = points.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - minVal) / range * size.height).toFloat())
        }
        for (i in 0 until offsets.size - 1) {
            drawLine(color = NeonGreen, start = offsets[i], end = offsets[i + 1], strokeWidth = 3f)
        }
    }
}

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
