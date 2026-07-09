package com.saiyanstrong.data.repository

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
}
