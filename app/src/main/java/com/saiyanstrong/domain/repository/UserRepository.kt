package com.saiyanstrong.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getLifetimePowerEarned(): Flow<Int>
    suspend fun addPowerEarned(amount: Int)
}
