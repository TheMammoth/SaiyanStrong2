package com.saiyanstrong.data.repository

import com.saiyanstrong.domain.model.AuthUser
import com.saiyanstrong.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    override val authState: Flow<AuthUser?> =
        supabaseClient.auth.sessionStatus.map { status ->
            (status as? SessionStatus.Authenticated)?.session?.user?.toAuthUser()
        }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<AuthUser> =
        runCatching {
            supabaseClient.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                nonce = rawNonce
            }
            supabaseClient.auth.currentUserOrNull()?.toAuthUser()
                ?: error("Sign-in succeeded but no user session was returned")
        }

    override suspend fun signOut() {
        supabaseClient.auth.signOut()
    }

    override fun currentUserId(): String? = supabaseClient.auth.currentUserOrNull()?.id

    private fun UserInfo.toAuthUser(): AuthUser {
        val metadata: JsonObject? = userMetadata
        return AuthUser(
            id = id,
            email = email,
            displayName = metadata.stringOrNull("full_name") ?: metadata.stringOrNull("name"),
            photoUrl = metadata.stringOrNull("avatar_url") ?: metadata.stringOrNull("picture")
        )
    }

    private fun JsonObject?.stringOrNull(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull
}
