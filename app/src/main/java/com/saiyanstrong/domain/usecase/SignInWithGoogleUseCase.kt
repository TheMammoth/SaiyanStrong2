package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.model.AuthUser
import com.saiyanstrong.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun execute(idToken: String, rawNonce: String): Result<AuthUser> =
        authRepository.signInWithGoogle(idToken = idToken, rawNonce = rawNonce)
}
