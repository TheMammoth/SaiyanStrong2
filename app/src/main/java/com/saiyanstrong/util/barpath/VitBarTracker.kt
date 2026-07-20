package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import org.opencv.video.TrackerVit
import org.opencv.video.TrackerVit_Params

/** A tracked bounding box for one frame, in the frame's full-resolution pixel space. */
data class TrackedBox(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Thin, stateful wrapper around OpenCV's [TrackerVit] (a Vision-Transformer DNN single-object
 * tracker). One instance tracks one rep: [init] it with the plate's bounding box on the mark
 * frame, then feed each subsequent frame to [update]. Returns null when the tracker reports a
 * low-confidence frame so the caller can HOLD the last position instead of teleporting onto
 * whatever the box drifted to — the same "can't jump across the room" guard the old blob tracker
 * used, now backed by the DNN's own confidence score.
 *
 * OpenCV's native runtime must be initialized ([OpenCvInitializer.ensureInitialized]) before
 * [create] is called.
 */
class VitBarTracker private constructor(private val tracker: TrackerVit) {

    fun init(frame: Bitmap, box: BarInitBox) {
        val mat = bitmapToBgr(frame)
        try {
            tracker.init(mat, Rect(box.x, box.y, box.width, box.height))
        } finally {
            mat.release()
        }
    }

    /** The tracked box this frame, or null if tracking is lost / below the confidence floor. */
    fun update(frame: Bitmap): TrackedBox? {
        val mat = bitmapToBgr(frame)
        return try {
            val rect = Rect()
            val ok = tracker.update(mat, rect)
            if (!ok || rect.width <= 0 || rect.height <= 0) return null
            if (tracker.trackingScore < MIN_SCORE) return null
            TrackedBox(rect.x, rect.y, rect.width, rect.height)
        } catch (t: Throwable) {
            null
        } finally {
            mat.release()
        }
    }

    private fun bitmapToBgr(frame: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(frame, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
    }

    companion object {
        /** TrackerVit's per-frame tracking score below which we treat the frame as lost. First-pass
         * threshold — the DNN reports ~0.3+ on a solid track; tune after real footage. */
        private const val MIN_SCORE = 0.3f

        /** Builds a tracker from the bundled ONNX at [modelPath]. Null on any native failure. */
        fun create(modelPath: String): VitBarTracker? = try {
            val params = TrackerVit_Params()
            params.set_net(modelPath)
            VitBarTracker(TrackerVit.create(params))
        } catch (t: Throwable) {
            null
        }
    }
}
