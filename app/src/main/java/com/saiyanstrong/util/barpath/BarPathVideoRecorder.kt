package com.saiyanstrong.util.barpath

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import com.saiyanstrong.domain.util.GyroTimeline

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
    private var analyzerExecutor: ExecutorService? = null
    private var liveAnalyzer: BarPathLiveAnalyzer? = null

    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var gyroListener: SensorEventListener? = null
    private var currentGyroTimeline: GyroTimeline? = null

    private var cumulativeGyroX = 0.0
    private var cumulativeGyroY = 0.0
    private var lastGyroTimestampNs = 0L

    /**
     * @param onLiveResult when non-null, binds a live [ImageAnalysis] loop ([BarPathLiveAnalyzer])
     * alongside preview + video and reports per-frame marker tracking. This is best-effort and
     * fully guarded: on some devices binding a third camera stream (preview + video + analysis)
     * exceeds the supported combination, so if that bind fails it falls back to preview + video
     * only — recording always survives, the live loop is what degrades. Null (the default) leaves
     * the recording path exactly as it was, with no live analysis.
     *
     * @param onBound fires once [videoCapture] is actually set and usable — this whole method
     * runs async (`ProcessCameraProvider.getInstance` resolves on a later main-executor tick, not
     * synchronously with this call), so a caller that enables RECORD on permission alone can call
     * [startRecording] before [videoCapture] is non-null, hitting the
     * `videoCapture ?: run { onFinalized(null, ...) }` guard below and surfacing "Recording
     * failed" even though nothing actually went wrong — this callback lets the UI gate RECORD on
     * real bind completion instead of just permission.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        highSpeedEnabled: Boolean = false,
        onHighSpeedUnavailable: () -> Unit = {},
        onError: (String) -> Unit = {},
        onBound: () -> Unit = {},
        onLiveResult: ((LiveFrameResult) -> Unit)? = null
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
          try {
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

            // Resolve to whatever this device actually supports rather than demanding HD — a
            // fixed HD selector that the bound camera can't satisfy is a common cause of an
            // immediate "recording failed" at finalize on some devices.
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            provider.unbindAll()
            analyzerExecutor?.shutdown()
            analyzerExecutor = null
            liveAnalyzer = null

            val imageAnalysis = onLiveResult?.let { callback ->
                val executor = Executors.newSingleThreadExecutor().also { analyzerExecutor = it }
                val analyzer = BarPathLiveAnalyzer(callback).also { liveAnalyzer = it }
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
            }

            val boundWithAnalysis = imageAnalysis != null && runCatching {
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, imageAnalysis
                )
            }.onFailure { Log.w("BarPathVideoRecorder", "Live analysis bind failed; recording without it", it) }
                .isSuccess

            if (!boundWithAnalysis) {
                analyzerExecutor?.shutdown()
                analyzerExecutor = null
                liveAnalyzer = null
                runCatching {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }.onSuccess {
                    onBound()
                }.onFailure { e ->
                    Log.e("BarPathVideoRecorder", "Camera bind failed", e)
                    if (tier != HighSpeedTier.STANDARD_30) {
                        onHighSpeedUnavailable()
                        bindCamera(context, lifecycleOwner, previewView, highSpeedEnabled = false, onError = onError, onBound = onBound, onLiveResult = onLiveResult)
                    } else {
                        onError("Camera unavailable on this device")
                    }
                }
            } else {
                onBound()
            }
          } catch (t: Throwable) {
            // Last-resort guard: nothing on the camera provider callback may crash the app.
            Log.e("BarPathVideoRecorder", "Camera setup failed", t)
            onError("Camera failed to start")
          }
        }, ContextCompat.getMainExecutor(context))
    }

    /** User tapped "Start rep" — drives the live phase machine's settling window. Independent of
     * video [startRecording] (which still produces the file for post-hoc analysis). */
    fun startRep() {
        liveAnalyzer?.startRep()
    }

    /** User tapped the marker in the live preview — [normX]/[normY] must come from
     * `PreviewView.meteringPointFactory.createPoint(...)`, not raw view pixels (see
     * [BarPathLiveAnalyzer.pendingColorSample] for the coordinate-space assumption/caveat). A
     * no-op if the live analysis loop isn't bound (e.g. the third camera stream failed to bind).
     * [widenTolerance]/[angularVelocityMagnitudeAtTap] come from the stability indicator at the
     * moment of the tap — see [PendingColorSample]. */
    fun requestColorSample(
        normX: Float,
        normY: Float,
        widenTolerance: Boolean = false,
        angularVelocityMagnitudeAtTap: Float = 0f
    ) {
        liveAnalyzer?.pendingColorSample =
            PendingColorSample(normX, normY, widenTolerance, angularVelocityMagnitudeAtTap)
    }

    fun startRecording(context: Context, onFinalized: (path: String?, gyroTimeline: GyroTimeline?, focalMm: Double, sensorWidthMm: Double, videoStartUptimeNs: Long, errorDetail: String?) -> Unit) {
      try {
        val capture = videoCapture ?: run { onFinalized(null, null, 0.0, 0.0, 0L, "camera not ready (bind incomplete)"); return }
        val outputDir = File(context.cacheDir, "bar_path").apply { mkdirs() }
        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.mp4")

        // Gyro capture drives OFFLINE shake compensation — it is strictly optional. Registering at
        // SENSOR_DELAY_FASTEST needs HIGH_SAMPLING_RATE_SENSORS (declared in the manifest on 12+),
        // but a device that still rejects it (or has no gyro) must NOT block recording. Any failure
        // here disables shake comp and continues — trackMarker handles a null gyroTimeline.
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

            currentGyroTimeline = GyroTimeline()
            cumulativeGyroX = 0.0
            cumulativeGyroY = 0.0
            lastGyroTimestampNs = 0L

            gyroListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (lastGyroTimestampNs != 0L) {
                        val dt = (event.timestamp - lastGyroTimestampNs) / 1e9
                        cumulativeGyroX += event.values[0] * dt
                        cumulativeGyroY += event.values[1] * dt
                        currentGyroTimeline?.addSample(event.timestamp, cumulativeGyroX, cumulativeGyroY)
                    }
                    lastGyroTimestampNs = event.timestamp
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            gyroSensor?.let {
                sensorManager?.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
        } catch (t: Throwable) {
            Log.w("BarPathVideoRecorder", "Gyro capture unavailable; recording without shake compensation", t)
            runCatching { gyroListener?.let { sensorManager?.unregisterListener(it) } }
            gyroListener = null
            currentGyroTimeline = null
        }

        var videoStartUptimeNs = 0L

        var focalMm = 0.0
        var sensorWidthMm = 0.0
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    if (focalLengths != null && focalLengths.isNotEmpty() && sensorSize != null) {
                        focalMm = focalLengths[0].toDouble()
                        sensorWidthMm = sensorSize.width.toDouble()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BarPathVideoRecorder", "Failed to get camera characteristics", e)
        }

        activeRecording = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(outputFile).build())
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Start) {
                    videoStartUptimeNs = SystemClock.elapsedRealtimeNanos()
                }
                if (event is VideoRecordEvent.Finalize) {
                    val timeline = currentGyroTimeline
                    gyroListener?.let { sensorManager?.unregisterListener(it) }
                    gyroListener = null
                    currentGyroTimeline = null

                    if (event.hasError()) {
                        val detail = describeRecordError(event.error, event.cause)
                        Log.e("BarPathVideoRecorder", "Recording error: $detail", event.cause)
                        onFinalized(null, null, 0.0, 0.0, 0L, detail)
                    } else {
                        onFinalized(outputFile.absolutePath, timeline, focalMm, sensorWidthMm, videoStartUptimeNs, null)
                    }
                }
            }
      } catch (t: Throwable) {
        // Record start can throw on device (codec/camera state) — never crash; reset and report.
        Log.e("BarPathVideoRecorder", "startRecording failed", t)
        gyroListener?.let { sensorManager?.unregisterListener(it) }
        gyroListener = null
        currentGyroTimeline = null
        onFinalized(null, null, 0.0, 0.0, 0L, "start failed: ${t.javaClass.simpleName} ${t.message ?: ""}".trim())
      }
    }

    /** Turns a CameraX [VideoRecordEvent.Finalize] error code into a short human-readable reason,
     * so the UI can show WHY a recording failed instead of a generic message — the only diagnostic
     * available without USB logcat access. */
    private fun describeRecordError(error: Int, cause: Throwable?): String {
        val name = when (error) {
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "not enough storage"
            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "file size limit"
            VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "invalid output options"
            VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "encoding failed"
            VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "recorder error"
            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "no valid data (clip too short?)"
            VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "camera source inactive"
            VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "duration limit"
            VideoRecordEvent.Finalize.ERROR_RECORDING_GARBAGE_COLLECTED -> "recording was garbage-collected"
            else -> "unknown"
        }
        val causeMsg = cause?.message?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
        return "code $error: $name$causeMsg"
    }

    fun stopRecording() {
        runCatching { activeRecording?.stop() }
        activeRecording = null
        gyroListener?.let { runCatching { sensorManager?.unregisterListener(it) } }
        gyroListener = null
    }
}
