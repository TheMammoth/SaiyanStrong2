package com.saiyanstrong.domain.model

/** One tracked video frame of the bar marker — pixel position + timestamp. */
data class BarPathSample(
    val timestampMs: Long,
    val xPx: Double,
    val yPx: Double
)
