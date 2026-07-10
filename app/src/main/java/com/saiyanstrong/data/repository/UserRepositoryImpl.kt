package com.saiyanstrong.data.repository

import com.saiyanstrong.data.datastore.UserPreferencesDataStore
import com.saiyanstrong.data.local.dao.BodyWeightDao
import com.saiyanstrong.data.local.entity.BodyWeightEntity
import com.saiyanstrong.domain.model.BodyWeightLog
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val bodyWeightDao: BodyWeightDao
) : UserRepository {

    override fun getLifetimePowerEarned(): Flow<Int> =
        userPreferencesDataStore.lifetimePowerEarned

    override fun getLastDismissedUpdateVersion(): Flow<String> =
        userPreferencesDataStore.lastDismissedUpdateVersion

    override suspend fun addPowerEarned(amount: Int) {
        userPreferencesDataStore.addPowerEarned(amount)
    }

    override suspend fun saveDismissedUpdateVersion(version: String) {
        userPreferencesDataStore.saveDismissedUpdateVersion(version)
    }

    override fun getBodyWeightLogs(): Flow<List<BodyWeightLog>> =
        bodyWeightDao.getAll().map { logs ->
            logs.map { BodyWeightLog(id = it.id, dateMs = it.dateMs, weightKg = it.weightKg) }
        }

    override suspend fun logBodyWeight(weightKg: Double) {
        bodyWeightDao.insert(
            BodyWeightEntity(dateMs = System.currentTimeMillis(), weightKg = weightKg)
        )
    }

    override suspend fun deleteBodyWeightLog(id: Long) {
        bodyWeightDao.deleteById(id)
    }

    override fun getUseFemaleDotsFormula(): Flow<Boolean> =
        userPreferencesDataStore.useFemaleDotsFormula

    override suspend fun setUseFemaleDotsFormula(useFemale: Boolean) {
        userPreferencesDataStore.setUseFemaleDotsFormula(useFemale)
    }

    override fun getDefaultRestSeconds(): Flow<Int> =
        userPreferencesDataStore.defaultRestSeconds

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        userPreferencesDataStore.setDefaultRestSeconds(seconds)
    }

    override fun getOnboardingComplete(): Flow<Boolean> =
        userPreferencesDataStore.onboardingComplete

    override suspend fun setOnboardingComplete(complete: Boolean) {
        userPreferencesDataStore.setOnboardingComplete(complete)
    }

    override fun getRestTimerSoundsEnabled(): Flow<Boolean> =
        userPreferencesDataStore.restTimerSoundsEnabled

    override suspend fun setRestTimerSoundsEnabled(enabled: Boolean) {
        userPreferencesDataStore.setRestTimerSoundsEnabled(enabled)
    }
}
