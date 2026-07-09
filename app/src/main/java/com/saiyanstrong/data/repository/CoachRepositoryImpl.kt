package com.saiyanstrong.data.repository

import com.saiyanstrong.data.backup.BackupPayload
import com.saiyanstrong.data.backup.BackupSerializer
import com.saiyanstrong.domain.model.AthleteSummary
import com.saiyanstrong.domain.model.CoachLink
import com.saiyanstrong.domain.model.CoachProfile
import com.saiyanstrong.domain.model.ExerciseLog
import com.saiyanstrong.domain.model.PushedTemplate
import com.saiyanstrong.domain.model.SetLog
import com.saiyanstrong.domain.model.WorkoutSession
import com.saiyanstrong.domain.repository.CoachRepository
import com.saiyanstrong.domain.repository.ExerciseRepository
import com.saiyanstrong.domain.usecase.CalculatePowerLevelUseCase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.first
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
    @SerialName("athlete_id") val athleteId: String,
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

@Serializable
private data class PushedTemplateInsert(
    @SerialName("coach_id") val coachId: String,
    @SerialName("athlete_id") val athleteId: String,
    val name: String,
    @SerialName("exercise_ids") val exerciseIds: List<Int>
)

@Serializable
private data class PushedTemplateRow(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    val name: String,
    @SerialName("exercise_ids") val exerciseIds: List<Int>,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class AcceptedUpdate(val accepted: Boolean = true)

/** Excludes ambiguous-looking characters (0/O, 1/I/L) so a spoken/handwritten code is unambiguous. */
private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

@Singleton
class CoachRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val backupSerializer: BackupSerializer,
    private val exerciseRepository: ExerciseRepository,
    private val calculatePowerLevelUseCase: CalculatePowerLevelUseCase
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

    override suspend fun getAthleteSummaries(): Result<List<AthleteSummary>> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val links = supabaseClient.postgrest.from("coach_athletes").select {
            filter {
                eq("coach_id", userId)
                eq("status", "active")
            }
        }.decodeList<CoachAthleteRow>()

        if (links.isEmpty()) return@runCatching emptyList()

        val athleteIds = links.map { it.athleteId }.distinct()
        val profilesById = supabaseClient.postgrest.from("linked_profile_public").select {
            filter { isIn("id", athleteIds) }
        }.decodeList<LinkedProfileRow>().associateBy { it.id }

        val nowMs = System.currentTimeMillis()

        links.map { link ->
            val profile = profilesById[link.athleteId]
            // An athlete who hasn't backed up yet (or whose backup fetch fails for any
            // reason) still shows up on the dashboard — just with no stats, not an error.
            val payload = runCatching { downloadAthleteBackupPayload(link.athleteId) }.getOrNull()
            val lastSessionDateMs = payload?.sessions?.maxOfOrNull { it.dateMs }
            val weeklyVolumeKg = payload?.sessions
                ?.filter { it.dateMs >= nowMs - SEVEN_DAYS_MS }
                ?.sumOf { it.totalVolumeKg } ?: 0.0

            AthleteSummary(
                athleteId = link.athleteId,
                displayName = profile?.displayName,
                email = profile?.email,
                lastSessionDateMs = lastSessionDateMs,
                weeklyVolumeKg = weeklyVolumeKg,
                powerLevel = calculatePowerLevelUseCase.getPowerLevel(payload?.lifetimePowerEarned ?: 0),
                isStale = lastSessionDateMs == null || nowMs - lastSessionDateMs > SEVEN_DAYS_MS
            )
        }
    }

    override suspend fun getAthleteHistory(athleteId: String): Result<List<WorkoutSession>> = runCatching {
        val payload = downloadAthleteBackupPayload(athleteId)
        val exercisesById = exerciseRepository.getAllExercises().first().associateBy { it.id }
        val exerciseLogsBySessionId = payload.exerciseLogs.groupBy { it.sessionId }
        val setLogsByExerciseLogId = payload.setLogs.groupBy { it.exerciseLogId }

        payload.sessions.sortedByDescending { it.dateMs }.map { sessionDto ->
            val exerciseLogs = exerciseLogsBySessionId[sessionDto.id].orEmpty()
                .sortedBy { it.orderIndex }
                .mapNotNull { logDto ->
                    val exercise = exercisesById[logDto.exerciseId] ?: return@mapNotNull null
                    val sets = setLogsByExerciseLogId[logDto.id].orEmpty().map { setDto ->
                        SetLog(
                            id = setDto.id,
                            setNumber = setDto.setNumber,
                            weightKg = setDto.weightKg,
                            reps = setDto.reps,
                            rpe = setDto.rpe,
                            isFailure = setDto.isFailure,
                            volumeKg = setDto.volumeKg,
                            timestampMs = setDto.timestampMs
                        )
                    }
                    ExerciseLog(id = logDto.id, exercise = exercise, sets = sets, orderIndex = logDto.orderIndex)
                }

            WorkoutSession(
                id = sessionDto.id,
                dateMs = sessionDto.dateMs,
                durationMs = sessionDto.durationMs,
                exerciseLogs = exerciseLogs,
                totalVolumeKg = sessionDto.totalVolumeKg,
                powerEarned = sessionDto.powerEarned,
                notes = sessionDto.notes,
                title = sessionDto.title
            )
        }
    }

    override suspend fun pushTemplate(athleteId: String, name: String, exerciseIds: List<Int>): Result<Unit> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        supabaseClient.postgrest.from("coach_pushed_templates").insert(
            PushedTemplateInsert(coachId = userId, athleteId = athleteId, name = name, exerciseIds = exerciseIds)
        )
        Unit
    }

    override suspend fun getPendingPushedTemplates(): Result<List<PushedTemplate>> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val rows = supabaseClient.postgrest.from("coach_pushed_templates").select {
            filter {
                eq("athlete_id", userId)
                eq("accepted", false)
            }
        }.decodeList<PushedTemplateRow>()

        if (rows.isEmpty()) return@runCatching emptyList()

        val coachIds = rows.map { it.coachId }.distinct()
        val profilesById = supabaseClient.postgrest.from("linked_profile_public").select {
            filter { isIn("id", coachIds) }
        }.decodeList<LinkedProfileRow>().associateBy { it.id }

        rows.map { row ->
            val profile = profilesById[row.coachId]
            PushedTemplate(
                id = row.id,
                coachName = profile?.displayName ?: profile?.email,
                name = row.name,
                exerciseIds = row.exerciseIds,
                createdAtMs = OffsetDateTime.parse(row.createdAt).toInstant().toEpochMilli()
            )
        }
    }

    override suspend fun markPushedTemplateAccepted(pushedTemplateId: String): Result<Unit> = runCatching {
        supabaseClient.postgrest.from("coach_pushed_templates").update(AcceptedUpdate()) {
            filter { eq("id", pushedTemplateId) }
        }
        Unit
    }

    private suspend fun downloadAthleteBackupPayload(athleteId: String): BackupPayload {
        val bytes = supabaseClient.storage.from(BACKUPS_BUCKET).downloadAuthenticated("$athleteId/latest.json")
        return backupSerializer.decode(String(bytes)).payload
    }

    private companion object {
        const val BACKUPS_BUCKET = "backups"
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
