package com.saiyanstrong.domain.model

data class BarPathAnalysis(
    val peakVelocityMs: Double,
    val meanConcentricVelocityMs: Double,
    val peakPowerWatts: Double,
    val meanPowerWatts: Double,
    val rangeOfMotionCm: Double,
    val barPathDeviationCm: Double,
    val velocityZone: VelocityZone
)
