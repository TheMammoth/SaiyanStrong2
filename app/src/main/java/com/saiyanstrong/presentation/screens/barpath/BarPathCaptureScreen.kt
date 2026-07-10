package com.saiyanstrong.presentation.screens.barpath

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.saiyanstrong.presentation.components.SaiyanButton
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.SaiyanGray
import com.saiyanstrong.util.barpath.BarPathVideoRecorder

private val MarkerGreen = Color(0xFF39FF14)

@Composable
fun BarPathCaptureScreen(
    onDone: () -> Unit,
    viewModel: BarPathCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onDone() }

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
                    onFinished = viewModel::onRecordingFinished,
                    onGalleryVideoPicked = viewModel::onGalleryVideoPicked
                )
                CaptureStep.CALIBRATING -> CalibrationStep(
                    uiState = uiState,
                    isStandalone = viewModel.isStandalone,
                    onTap = viewModel::onCalibrationTap,
                    onResetPoints = viewModel::onResetCalibrationPoints,
                    onReferenceLengthChanged = viewModel::onReferenceLengthChanged,
                    onWeightKgChanged = viewModel::onWeightKgChanged,
                    onConfirm = viewModel::onConfirmCalibration
                )
                CaptureStep.PROCESSING -> ProcessingStep()
                CaptureStep.RESULTS -> ResultsStep(analysis = uiState.analysis, onSave = viewModel::onSave)
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
    onFinished: (String?) -> Unit,
    onGalleryVideoPicked: (android.net.Uri) -> Unit = {}
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
    var showCamera by remember { mutableStateOf(!isStandalone) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onGalleryVideoPicked) }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Attach a bright pink/magenta marker to the bar. Point the camera so the full range of motion stays in frame.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
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
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        recorder.bindCamera(ctx, lifecycleOwner, previewView)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(360.dp)
            )
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
                    recorder.startRecording(context) { path ->
                        isRecording = false
                        onFinished(path)
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
}

@Composable
private fun CalibrationStep(
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

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(
            "Tap two points on something of known length (a plate diameter, the bar sleeve) in this frame.",
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
private fun ResultsStep(analysis: BarPathAnalysis?, onSave: () -> Unit) {
    if (analysis == null) return
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ResultRow("Peak velocity", "%.2f m/s".format(analysis.peakVelocityMs))
        ResultRow("Mean concentric velocity", "%.2f m/s".format(analysis.meanConcentricVelocityMs))
        ResultRow("Zone", analysis.velocityZone.label)
        ResultRow("Peak power", "%.0f W".format(analysis.peakPowerWatts))
        ResultRow("Mean power", "%.0f W".format(analysis.meanPowerWatts))
        ResultRow("Range of motion", "%.1f cm".format(analysis.rangeOfMotionCm))
        ResultRow("Bar path deviation", "%.1f cm".format(analysis.barPathDeviationCm))

        Spacer(Modifier.height(20.dp))
        SaiyanButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("SAVE TO SET  >>>", fontWeight = FontWeight.Black)
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
