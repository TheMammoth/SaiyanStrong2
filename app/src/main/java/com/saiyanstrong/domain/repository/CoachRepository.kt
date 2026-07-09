package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.AthleteSummary
import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.domain.model.CoachProfile
import com.saiyanstrong.domain.model.PushedTemplate
import com.saiyanstrong.domain.model.WorkoutSession

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

    /**
     * Coach's dashboard data — one summary per actively-linked athlete, computed by
     * downloading and decoding each athlete's latest cloud backup (never restoring it
     * locally). Read-only, and enforced by the same storage RLS as everything else here:
     * an unlinked or revoked athlete's backup simply isn't fetchable.
     */
    suspend fun getAthleteSummaries(): Result<List<AthleteSummary>>

    /** Read-only reconstruction of one athlete's session history from their latest backup. */
    suspend fun getAthleteHistory(athleteId: String): Result<List<WorkoutSession>>

    /** Coach-only, requires an active link to the athlete (enforced by RLS). */
    suspend fun pushTemplate(athleteId: String, name: String, exerciseIds: List<Int>): Result<Unit>

    /** Athlete's not-yet-accepted templates pushed by any linked coach. */
    suspend fun getPendingPushedTemplates(): Result<List<PushedTemplate>>

    /** Flips accepted=true server-side. Does NOT create the local template — that's the caller's job. */
    suspend fun markPushedTemplateAccepted(pushedTemplateId: String): Result<Unit>
}
