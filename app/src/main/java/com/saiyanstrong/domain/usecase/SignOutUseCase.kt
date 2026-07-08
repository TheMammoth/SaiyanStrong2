package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun execute() = authRepository.signOut()
}
