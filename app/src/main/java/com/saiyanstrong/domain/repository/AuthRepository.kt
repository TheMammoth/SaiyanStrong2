package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthUser?>

    suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<AuthUser>
    suspend fun signOut()
    fun currentUserId(): String?
}
