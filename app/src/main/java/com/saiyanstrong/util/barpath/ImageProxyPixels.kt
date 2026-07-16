package com.saiyanstrong.util.barpath

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/** A downsampled ARGB view of a camera frame, used by the live analyzers. */
internal class DownsampledFrame(val data: IntArray, val width: Int, val height: Int)

/**
 * YUV_420_888 [ImageProxy] → a downsampled ARGB IntArray (every [step]th pixel), reusing the tested
 * [yuvToRgb] conversion. Downsampling keeps per-frame cost low enough for a live loop — exact pixel
 * precision isn't needed for a centroid or a color patch. Shared by [BarPathLiveAnalyzer] (offline
 * live tracking) and [MarkerCalibrationAnalyzer] (pre-record marker calibration) so there is one
 * implementation of the plane-stride handling, not two.
 */
internal fun imageProxyToDownsampledPixels(image: ImageProxy, step: Int = 2): DownsampledFrame? {
    if (image.format != ImageFormat.YUV_420_888) return null
    val outWidth = image.width / step
    val outHeight = image.height / step
    if (outWidth <= 0 || outHeight <= 0) return null

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val yRowStride = yPlane.rowStride; val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride; val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride; val vPixelStride = vPlane.pixelStride

    val out = IntArray(outWidth * outHeight)
    for (oy in 0 until outHeight) {
        val sy = oy * step
        val uvRow = (sy / 2)
        for (ox in 0 until outWidth) {
            val sx = ox * step
            val uvCol = (sx / 2)
            val y = yBuffer.get(sy * yRowStride + sx * yPixelStride).toInt() and 0xFF
            val u = uBuffer.get(uvRow * uRowStride + uvCol * uPixelStride).toInt() and 0xFF
            val v = vBuffer.get(uvRow * vRowStride + uvCol * vPixelStride).toInt() and 0xFF
            out[oy * outWidth + ox] = yuvToRgb(y, u, v)
        }
    }
    return DownsampledFrame(out, outWidth, outHeight)
}
