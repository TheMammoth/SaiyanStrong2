package com.saiyanstrong.domain.model

data class CoachProfile(
    val id: String,
    val role: String,
    val coachEntitlementActive: Boolean,
    val coachEntitlementExpiresAtMs: Long?
)
