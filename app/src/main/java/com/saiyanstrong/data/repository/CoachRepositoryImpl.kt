package com.saiyanstrong.data.repository

import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.domain.model.CoachProfile
import com.saiyanstrong.domain.repository.CoachRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ProfileRow(
    val id: String,
    val role: String,
    @SerialName("coach_entitlement_active") val coachEntitlementActive: Boolean,
    @SerialName("coach_entitlement_expires_at") val coachEntitlementExpiresAt: String?
)

@Serializable
private data class InviteCodeInsert(
    val code: String,
    @SerialName("coach_id") val coachId: String
)

@Serializable
private data class CoachAthleteRow(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("linked_at") val linkedAt: String
)

@Serializable
private data class LinkedProfileRow(
    val id: String,
    val email: String?,
    @SerialName("display_name") val displayName: String?
)

@Serializable
private data class RevokeStatusUpdate(val status: String = "revoked")

/** Excludes ambiguous-looking characters (0/O, 1/I/L) so a spoken/handwritten code is unambiguous. */
private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

@Singleton
class CoachRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : CoachRepository {

    override suspend fun isCoach(): Result<Boolean> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        supabaseClient.postgrest
            .rpc("is_coach", buildJsonObject { put("uid", userId) })
            .decodeAs<Boolean>()
    }

    override suspend fun getProfile(): Result<CoachProfile> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val row = supabaseClient.postgrest.from("profiles").select {
            filter { eq("id", userId) }
            single()
        }.decodeAs<ProfileRow>()

        CoachProfile(
            id = row.id,
            role = row.role,
            coachEntitlementActive = row.coachEntitlementActive,
            coachEntitlementExpiresAtMs = row.coachEntitlementExpiresAt
                ?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
        )
    }

    override suspend fun generateInviteCode(): Result<String> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val code = (1..8).map { INVITE_CODE_ALPHABET.random() }.joinToString("")
        supabaseClient.postgrest.from("coach_invite_codes")
            .insert(InviteCodeInsert(code = code, coachId = userId))
        code
    }

    override suspend fun redeemInviteCode(code: String): Result<Unit> = runCatching {
        val success = supabaseClient.postgrest
            .rpc("redeem_invite_code", buildJsonObject { put("invite_code", code) })
            .decodeAs<Boolean>()
        check(success) { "That invite code is invalid or expired" }
    }

    override suspend fun getLinkedCoaches(): Result<List<CoachLink>> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val links = supabaseClient.postgrest.from("coach_athletes").select {
            filter {
                eq("athlete_id", userId)
                eq("status", "active")
            }
        }.decodeList<CoachAthleteRow>()

        if (links.isEmpty()) return@runCatching emptyList()

        val coachIds = links.map { it.coachId }.distinct()
        val profilesById = supabaseClient.postgrest.from("linked_profile_public").select {
            filter { isIn("id", coachIds) }
        }.decodeList<LinkedProfileRow>().associateBy { it.id }

        links.map { link ->
            val profile = profilesById[link.coachId]
            CoachLink(
                linkId = link.id,
                coachId = link.coachId,
                coachEmail = profile?.email,
                coachDisplayName = profile?.displayName,
                linkedAtMs = OffsetDateTime.parse(link.linkedAt).toInstant().toEpochMilli()
            )
        }
    }

    override suspend fun revokeCoachLink(linkId: String): Result<Unit> = runCatching {
        supabaseClient.postgrest.from("coach_athletes").update(RevokeStatusUpdate()) {
            filter { eq("id", linkId) }
        }
        Unit
    }
}
