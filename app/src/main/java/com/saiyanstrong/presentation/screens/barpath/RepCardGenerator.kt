package com.saiyanstrong.presentation.screens.barpath

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.saiyanstrong.domain.model.TrackedFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Everything the shareable rep card needs. Adapted from the requested `VbtSessionResult`:
 * sets/reps are omitted (a VBT recording is one rep, and standalone analyses have no logged set),
 * "Mean Propulsive Velocity" is the app's mean *concentric* velocity (labelled accordingly, not
 * mis-claimed as MPV), and Time Under Tension is derived from the tracked frame span.
 */
data class RepCardData(
    val frames: List<TrackedFrame>,
    val exerciseName: String,
    val weightKg: Double,
    val meanVelocityMps: Double,
    val peakVelocityMps: Double,
    val romMeters: Double,
    val tutSeconds: Double,
    val dateMs: Long
)

/**
 * Renders a 1080×1920 (9:16 Stories) shareable "rep card" via android.graphics only — no XML, no
 * third-party image libs — so it's pixel-identical regardless of device screen. Not unit-testable
 * itself (android.graphics.Bitmap needs a device/Robolectric); the geometry helpers below and the
 * reused [velocityColorArgb] are the tested parts.
 */
object RepCardGenerator {
    private const val W = 1080
    private const val H = 1920

    private const val CRIMSON_DARK = 0xFF1A0508.toInt()
    private const val MATTE_BLACK = 0xFF0D0D0D.toInt()
    private const val NEON_GREEN = 0xFF39FF14.toInt()
    private const val POWER_AMBER = 0xFFF5A623.toInt()
    private const val DANGER_RED = 0xFFFF3B3B.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun generateRepCard(data: RepCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)
        drawPathViz(canvas, data.frames)
        drawMetrics(canvas, data)
        drawFooter(canvas, data)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), CRIMSON_DARK, MATTE_BLACK, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
    }

    private fun drawPathViz(canvas: Canvas, frames: List<TrackedFrame>) {
        // Top 40% (0..768). Subtle rack uprights as simple line art.
        val sectionTop = 140f
        val sectionBottom = 740f
        val sectionLeft = 120f
        val sectionRight = 960f
        val rackPaint = Paint().apply {
            color = WHITE; alpha = 30; strokeWidth = 6f; isAntiAlias = true
        }
        canvas.drawLine(sectionLeft - 40, sectionTop - 20, sectionLeft - 40, sectionBottom + 20, rackPaint)
        canvas.drawLine(sectionRight + 40, sectionTop - 20, sectionRight + 40, sectionBottom + 20, rackPaint)

        val bounds = boundsOf(frames) ?: return
        val transform = fitTransform(bounds, sectionLeft, sectionTop, sectionRight - sectionLeft, sectionBottom - sectionTop)

        // Ghost (full arc), thin white 60%.
        val ghostPaint = Paint().apply {
            color = WHITE; alpha = 150; style = Paint.Style.STROKE; strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        val ghost = Path()
        frames.forEachIndexed { i, f ->
            val (x, y) = transform.map(f.xPx.toFloat(), f.yPx.toFloat())
            if (i == 0) ghost.moveTo(x, y) else ghost.lineTo(x, y)
        }
        canvas.drawPath(ghost, ghostPaint)

        // Velocity-coloured path, thick.
        val segPaint = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 16f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        for (i in 1 until frames.size) {
            val (x0, y0) = transform.map(frames[i - 1].xPx.toFloat(), frames[i - 1].yPx.toFloat())
            val (x1, y1) = transform.map(frames[i].xPx.toFloat(), frames[i].yPx.toFloat())
            segPaint.color = velocityColorArgb(frames[i].velocityMps.toFloat())
            canvas.drawLine(x0, y0, x1, y1, segPaint)
        }

        // Peak (green) + sticking (red) markers.
        val markerPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        frames.indices.maxByOrNull { frames[it].velocityMps }?.let {
            val (x, y) = transform.map(frames[it].xPx.toFloat(), frames[it].yPx.toFloat())
            markerPaint.color = NEON_GREEN
            canvas.drawCircle(x, y, 18f, markerPaint)
        }
        frames.indices.minByOrNull { frames[it].velocityMps }?.let {
            val (x, y) = transform.map(frames[it].xPx.toFloat(), frames[it].yPx.toFloat())
            markerPaint.color = DANGER_RED
            canvas.drawCircle(x, y, 18f, markerPaint)
        }
    }

    private fun drawMetrics(canvas: Canvas, data: RepCardData) {
        // Middle 30% (768..1344), 2x2 grid.
        val cells = listOf(
            Triple("MEAN VELOCITY", "%.2f".format(data.meanVelocityMps), "m/s"),
            Triple("PEAK VELOCITY", "%.2f".format(data.peakVelocityMps), "m/s"),
            Triple("RANGE OF MOTION", "%.2f".format(data.romMeters), "m"),
            Triple("TIME UNDER TENSION", "%.1f".format(data.tutSeconds), "s")
        )
        val numberPaint = Paint().apply {
            color = WHITE; textSize = 96f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val unitPaint = Paint().apply {
            color = NEON_GREEN; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint().apply {
            color = WHITE; alpha = 140; textSize = 30f; typeface = Typeface.MONOSPACE
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val cellW = W / 2f
        val cellH = 576f / 2f
        cells.forEachIndexed { i, (name, value, unit) ->
            val cx = (i % 2) * cellW + cellW / 2f
            val cyTop = 768f + (i / 2) * cellH
            val cy = cyTop + cellH / 2f
            canvas.drawText(value, cx, cy, numberPaint)
            canvas.drawText(unit, cx, cy + 44f, unitPaint)
            canvas.drawText(name, cx, cy - 84f, namePaint)
        }
    }

    private fun drawFooter(canvas: Canvas, data: RepCardData) {
        // Bottom 30% (1344..1920).
        val exercisePaint = Paint().apply {
            color = POWER_AMBER; textSize = 60f; typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint().apply {
            color = WHITE; textSize = 40f; typeface = Typeface.MONOSPACE
            isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        val brandPaint = Paint().apply {
            color = NEON_GREEN; alpha = 180; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true; textAlign = Paint.Align.CENTER; letterSpacing = 0.15f
        }
        val cx = W / 2f
        if (data.exerciseName.isNotBlank()) canvas.drawText(data.exerciseName, cx, 1480f, exercisePaint)
        val weight = if (data.weightKg > 0.0) formatWeight(data.weightKg) else ""
        if (weight.isNotBlank()) canvas.drawText(weight, cx, 1550f, subPaint)
        canvas.drawText(formatDate(data.dateMs), cx, 1610f, subPaint.apply { alpha = 150 })
        canvas.drawText("TRACKED WITH SAIYANSTRONG", cx, 1840f, brandPaint)
    }

    private fun formatWeight(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()} kg" else "%.1f kg".format(kg)

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ms))
}

// ── Pure, unit-testable geometry ─────────────────────────────────────────────────────────────

internal data class FloatRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal class RepCardTransform(val scale: Float, val originX: Float, val originY: Float) {
    fun map(x: Float, y: Float): Pair<Float, Float> = (x * scale + originX) to (y * scale + originY)
}

/** Bounding box of the tracked centroids, or null if there are none. */
internal fun boundsOf(frames: List<TrackedFrame>): FloatRect? {
    if (frames.isEmpty()) return null
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (f in frames) {
        val x = f.xPx.toFloat(); val y = f.yPx.toFloat()
        if (x < minX) minX = x; if (x > maxX) maxX = x
        if (y < minY) minY = y; if (y > maxY) maxY = y
    }
    return FloatRect(minX, minY, maxX, maxY)
}

/** Aspect-preserving, centered fit of [src] into the dst rect — the transform mapping a source
 * point into the destination. Guards against a zero-size source (a perfectly still marker). */
internal fun fitTransform(src: FloatRect, dstLeft: Float, dstTop: Float, dstWidth: Float, dstHeight: Float): RepCardTransform {
    val srcW = src.width.takeIf { it > 0f } ?: 1f
    val srcH = src.height.takeIf { it > 0f } ?: 1f
    val scale = min(dstWidth / srcW, dstHeight / srcH)
    val scaledW = srcW * scale
    val scaledH = srcH * scale
    val originX = dstLeft + (dstWidth - scaledW) / 2f - src.left * scale
    val originY = dstTop + (dstHeight - scaledH) / 2f - src.top * scale
    return RepCardTransform(scale, originX, originY)
}
