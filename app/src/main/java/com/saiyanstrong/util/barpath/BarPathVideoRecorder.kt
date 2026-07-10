package com.saiyanstrong.util.barpath

import android.content.Context
import android.util.Log
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
 */
class BarPathVideoRecorder {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
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
            }.onFailure { e -> Log.e("BarPathVideoRecorder", "Camera bind failed", e) }
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
