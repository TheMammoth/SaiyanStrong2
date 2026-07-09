package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.domain.model.CoachProfile

interface CoachRepository {
    /** The single entitlement check — backed by the is_coach() Postgres function. */
    suspend fun isCoach(): Result<Boolean>

    /** Raw profile fields for display only (e.g. entitlement expiry) — never used to gate UI. */
    suspend fun getProfile(): Result<CoachProfile>

    /** Coach-only (enforced by RLS + is_coach() regardless of what the client believes). */
    suspend fun generateInviteCode(): Result<String>

    /** Athlete redeems a coach's code — creates the link server-side via a security-definer RPC. */
    suspend fun redeemInviteCode(code: String): Result<Unit>

    /** Athlete's own view of the coaches they've linked to. */
    suspend fun getLinkedCoaches(): Result<List<CoachLink>>

    /** Athlete revokes their own consent — the coach loses read access to their backup immediately. */
    suspend fun revokeCoachLink(linkId: String): Result<Unit>
}
