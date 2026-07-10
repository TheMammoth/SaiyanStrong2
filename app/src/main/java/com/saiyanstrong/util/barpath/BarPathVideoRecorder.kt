package com.saiyanstrong.util.barpath

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Thin CameraX wrapper: bind preview + video capture to a [PreviewView], record silently (no
 * audio — one less permission, and audio isn't used for anything here) to a cache file. Not
 * tested against a real device this session — CameraX's binding/lifecycle behavior is exactly
 * the kind of thing that needs a real phone to confirm.
 *
 * High-speed mode requests a higher target frame rate via [Camera2Interop] on the [Preview]
 * builder — `CONTROL_AE_TARGET_FPS_RANGE` is a session-wide 3A parameter (not tied to one
 * specific surface), so applying it through Preview's interop point influences the whole bound
 * session, including what VideoCapture records. This is a *request*, not a guarantee the way
 * `CameraConstrainedHighSpeedCaptureSession` is on raw Camera2 — CameraX has no equivalent to
 * that session type, so some devices will silently record at a lower rate than requested rather
 * than fail outright. [BarPathFrameTracker] derives its actual sample interval from the
 * recorded video's real frame rate, not an assumed one, so this degrades gracefully either way.
 */
class BarPathVideoRecorder {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        highSpeedEnabled: Boolean = false,
        onHighSpeedUnavailable: () -> Unit = {}
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val tier = if (highSpeedEnabled) HighSpeedCapabilityChecker.check(provider) else HighSpeedTier.STANDARD_30

            val previewBuilder = Preview.Builder()
            if (tier != HighSpeedTier.STANDARD_30) {
                val targetFps = if (tier == HighSpeedTier.FPS_120) 120 else 60
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(targetFps, targetFps))
            }
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            provider.unbindAll()
            runCatching {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.onFailure { e ->
                Log.e("BarPathVideoRecorder", "Camera bind failed", e)
                if (tier != HighSpeedTier.STANDARD_30) {
                    onHighSpeedUnavailable()
                    bindCamera(context, lifecycleOwner, previewView, highSpeedEnabled = false)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(context: Context, onFinalized: (path: String?) -> Unit) {
        val capture = videoCapture ?: run { onFinalized(null); return }
        val outputDir = File(context.cacheDir, "bar_path").apply { mkdirs() }
        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.mp4")

        activeRecording = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(outputFile).build())
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) {
                        Log.e("BarPathVideoRecorder", "Recording error: ${event.error}")
                        onFinalized(null)
                    } else {
                        onFinalized(outputFile.absolutePath)
                    }
                }
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }
}
