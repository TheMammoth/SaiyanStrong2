package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.BodyWeightLog
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getLifetimePowerEarned(): Flow<Int>
    fun getLastDismissedUpdateVersion(): Flow<String>
    suspend fun addPowerEarned(amount: Int)
    suspend fun saveDismissedUpdateVersion(version: String)

    fun getBodyWeightLogs(): Flow<List<BodyWeightLog>>
    suspend fun logBodyWeight(weightKg: Double)
    suspend fun deleteBodyWeightLog(id: Long)

    fun getUseFemaleDotsFormula(): Flow<Boolean>
    suspend fun setUseFemaleDotsFormula(useFemale: Boolean)
}
