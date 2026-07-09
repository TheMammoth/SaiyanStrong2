package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.CoachProfile

interface CoachRepository {
    /** The single entitlement check — backed by the is_coach() Postgres function. */
    suspend fun isCoach(): Result<Boolean>

    /** Raw profile fields for display only (e.g. entitlement expiry) — never used to gate UI. */
    suspend fun getProfile(): Result<CoachProfile>
}
