package com.saiyanstrong.util.barpath

import android.content.Context
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * One-time OpenCV native-runtime init and TrackerVit model provisioning. The Maven Central AAR
 * self-contains its native libraries (since 4.9.0), so [OpenCVLoader.initLocal] loads them with no
 * OpenCV Manager app. Guarded and idempotent — every native call in [VitBarTracker] is gated on
 * [ensureInitialized] returning true, so a device where init fails degrades to "couldn't track"
 * rather than crashing.
 */
object OpenCvInitializer {

    private const val MODEL_ASSET = "vittrack/object_tracking_vittrack_2023sep.onnx"
    private const val MODEL_FILE = "object_tracking_vittrack_2023sep.onnx"

    @Volatile
    private var initialized: Boolean? = null

    @Volatile
    private var cachedModelPath: String? = null

    /** Loads the OpenCV native libs once; caches the result. False if the device can't load them. */
    @Synchronized
    fun ensureInitialized(): Boolean {
        initialized?.let { return it }
        val ok = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        initialized = ok
        return ok
    }

    /**
     * Copies the bundled ONNX model out of assets into filesDir once (TrackerVit_Params.set_net
     * needs a filesystem path, not an asset stream) and returns its absolute path. Null on any IO
     * failure.
     */
    @Synchronized
    fun vitModelPath(context: Context): String? {
        cachedModelPath?.let { return it }
        return try {
            val outFile = File(context.filesDir, MODEL_FILE)
            if (!outFile.exists() || outFile.length() == 0L) {
                context.assets.open(MODEL_ASSET).use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            cachedModelPath = outFile.absolutePath
            outFile.absolutePath
        } catch (t: Throwable) {
            null
        }
    }
}
