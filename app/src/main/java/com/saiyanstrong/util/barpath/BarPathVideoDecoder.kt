package com.saiyanstrong.util.barpath

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Streaming video decode via [MediaExtractor] + [MediaCodec], as a faster alternative to
 * [BarPathFrameTracker]'s per-timestamp `MediaMetadataRetriever.getFrameAtTime()` seeks — those
 * re-seek to a sync frame and decode forward on every sampled timestamp, which gets expensive at
 * high frame rates. This decodes the track once, sequentially, and emits one frame per sample
 * interval.
 *
 * UNVERIFIED against a real device this session (MediaCodec is one of the most device-fragile
 * Android APIs — codec support, output color format, and YUV plane strides all vary). It is
 * therefore wired in [BarPathFrameTracker] as an opt-in path with automatic fallback to the
 * proven retriever loop, so a failure here never breaks tracking. Whether it is actually *faster*
 * than the retriever path is also unmeasured — sequential decode beating repeated seeks is the
 * expectation, but the software YUV→RGB conversion below adds cost the hardware getFrameAtTime
 * path doesn't have. Measure on a device (A/B against the retriever path) before making it the
 * default.
 */
class BarPathVideoDecoder @Inject constructor() {

    /**
     * Decodes [videoPath] sequentially and invokes [onFrame] once per [sampleIntervalMs] of
     * presentation time, with a full-resolution ARGB_8888 [Bitmap] and that frame's real
     * presentation timestamp in ms. The Bitmap is recycled immediately after [onFrame] returns —
     * it is only valid for the duration of the callback (consume it synchronously, exactly like
     * the retriever path already does).
     *
     * @return the number of frames emitted. Returns 0 (rather than throwing) for a video with no
     * decodable video track. Genuine decode errors propagate as exceptions so the caller can fall
     * back.
     */
    fun decodeSampledFrames(
        videoPath: String,
        sampleIntervalMs: Long,
        onFrame: (frame: Bitmap, timestampMs: Long) -> Unit
    ): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var emitted = 0
        try {
            extractor.setDataSource(videoPath)
            val trackIndex = selectVideoTrack(extractor) ?: return 0
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)  // no surface — ByteBuffer/Image output
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val intervalUs = (sampleIntervalMs * 1000).coerceAtLeast(1)
            var nextSampleUs = 0L
            var reusablePixels: IntArray? = null
            var sawInputEnd = false
            var sawOutputEnd = false

            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true

                    val presentationUs = bufferInfo.presentationTimeUs
                    val keepFrame = bufferInfo.size > 0 && presentationUs >= nextSampleUs
                    if (keepFrame) {
                        val image = codec.getOutputImage(outIndex)
                        if (image != null) {
                            if (reusablePixels == null) reusablePixels = IntArray(image.width * image.height)
                            val bitmap = imageToBitmap(image, reusablePixels!!)
                            image.close()
                            onFrame(bitmap, presentationUs / 1000)
                            bitmap.recycle()
                            emitted++
                            // Advance past this frame; catch up if it jumped several intervals.
                            do { nextSampleUs += intervalUs } while (nextSampleUs <= presentationUs)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
                // outIndex < 0 (INFO_TRY_AGAIN_LATER / INFO_OUTPUT_FORMAT_CHANGED): just loop.
            }
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
        return emitted
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return null
    }

    /** YUV_420_888 [Image] → ARGB_8888 [Bitmap], honoring each plane's row/pixel strides. */
    private fun imageToBitmap(image: Image, pixels: IntArray): Bitmap {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (row in 0 until height) {
            val yBase = row * yRowStride
            val uvRow = row / 2
            val uBase = uvRow * uRowStride
            val vBase = uvRow * vRowStride
            val out = row * width
            for (col in 0 until width) {
                val uvCol = col / 2
                val y = yBuffer.get(yBase + col * yPixelStride).toInt() and 0xFF
                val u = uBuffer.get(uBase + uvCol * uPixelStride).toInt() and 0xFF
                val v = vBuffer.get(vBase + uvCol * vPixelStride).toInt() and 0xFF
                pixels[out + col] = yuvToRgb(y, u, v)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

/**
 * BT.601 YUV→RGB for a single pixel (y/u/v each 0..255), returning a packed opaque ARGB int.
 * Pure and unit-testable — the one part of the decode path that doesn't depend on Android's
 * media framework.
 */
internal fun yuvToRgb(y: Int, u: Int, v: Int): Int {
    val d = u - 128
    val e = v - 128
    val r = (y + 1.402 * e).roundToInt().coerceIn(0, 255)
    val g = (y - 0.344136 * d - 0.714136 * e).roundToInt().coerceIn(0, 255)
    val b = (y + 1.772 * d).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
