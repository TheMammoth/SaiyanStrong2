package com.saiyanstrong.domain.model

/**
 * One frame of the analysed bar path: the marker's camera-pixel position and the (Savitzky-Golay
 * smoothed) vertical velocity at that instant. This is the per-frame series behind the aggregate
 * [BarPathAnalysis] — retained (transiently, in-session) so the replay screen can draw a
 * velocity-coloured path synced to video playback. Not persisted; only meaningful right after a
 * rep while the recorded video is still cached.
 *
 * (The request's `scaleCorrectionFactor` per frame isn't included — depth correction is applied
 * to displacement inside AnalyzeBarPathUseCase and not retained as a per-frame factor, and the
 * overlay only needs position + velocity.)
 */
data class TrackedFrame(
    val timestampMs: Long,
    val xPx: Double,
    val yPx: Double,
    val velocityMps: Double
)
