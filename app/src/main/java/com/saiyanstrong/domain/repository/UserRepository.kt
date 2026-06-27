package com.saiyanstrong.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getLifetimePowerEarned(): Flow<Int>
    fun getLastDismissedUpdateVersion(): Flow<String>
    suspend fun addPowerEarned(amount: Int)
    suspend fun saveDismissedUpdateVersion(version: String)
}
