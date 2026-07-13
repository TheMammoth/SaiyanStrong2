package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.Archetype
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

    fun getDefaultRestSeconds(): Flow<Int>
    suspend fun setDefaultRestSeconds(seconds: Int)

    fun getOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)

    fun getRestTimerSoundsEnabled(): Flow<Boolean>
    suspend fun setRestTimerSoundsEnabled(enabled: Boolean)

    fun getBarPathTipsDismissed(): Flow<Boolean>
    suspend fun setBarPathTipsDismissed(dismissed: Boolean)

    /** Null = user hasn't explicitly chosen — caller defaults to "on if the device supports it." */
    fun getHighSpeedModeEnabled(): Flow<Boolean?>
    suspend fun setHighSpeedModeEnabled(enabled: Boolean)

    fun getSelectedArchetype(): Flow<Archetype>
    suspend fun setSelectedArchetype(archetype: Archetype)

    fun getBiomechanicsDisclaimerShown(): Flow<Boolean>
    suspend fun setBiomechanicsDisclaimerShown(shown: Boolean)
}
