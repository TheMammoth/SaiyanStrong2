package com.saiyanstrong.domain.model

data class AthleteSummary(
    val athleteId: String,
    val displayName: String?,
    val email: String?,
    val lastSessionDateMs: Long?,
    val weeklyVolumeKg: Double,
    val powerLevel: PowerLevel,
    val isStale: Boolean
)
