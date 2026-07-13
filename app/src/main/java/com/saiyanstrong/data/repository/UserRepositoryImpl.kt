package com.saiyanstrong.data.repository

import com.saiyanstrong.data.datastore.UserPreferencesDataStore
import com.saiyanstrong.data.local.dao.BodyWeightDao
import com.saiyanstrong.data.local.entity.BodyWeightEntity
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.BodyWeightLog
import com.saiyanstrong.domain.model.LimbRatios
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Matches archetypes.json's PROPORTIONAL entry — the sensible starting point for the Custom
 * sliders before the user has ever saved their own. Small, deliberate duplication of that JSON
 * entry's numbers rather than a cross-repository dependency on BiomechanicsRepository. */
private val DEFAULT_CUSTOM_RATIOS = LimbRatios(
    thighRatio = 0.230f, shankRatio = 0.270f, torsoRatio = 0.29f, headNeckRatio = 0.16f,
    footLenRatio = 0.10f, shoulderHalfRatio = 0.090f, hipHalfRatio = 0.070f,
    kneeHalfRatio = 0.050f, ankleHalfRatio = 0.045f, barRiseRatio = 0.04f, gripHalfRatio = 0.12f
)

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val bodyWeightDao: BodyWeightDao
) : UserRepository {

    private val json = Json { ignoreUnknownKeys = true }

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

    override fun getBarPathTipsDismissed(): Flow<Boolean> =
        userPreferencesDataStore.barPathTipsDismissed

    override suspend fun setBarPathTipsDismissed(dismissed: Boolean) {
        userPreferencesDataStore.setBarPathTipsDismissed(dismissed)
    }

    override fun getHighSpeedModeEnabled(): Flow<Boolean?> =
        userPreferencesDataStore.highSpeedModeEnabled

    override suspend fun setHighSpeedModeEnabled(enabled: Boolean) {
        userPreferencesDataStore.setHighSpeedModeEnabled(enabled)
    }

    override fun getSelectedArchetype(): Flow<Archetype> =
        userPreferencesDataStore.selectedArchetypeName.map { name ->
            runCatching { Archetype.valueOf(name) }.getOrDefault(Archetype.PROPORTIONAL)
        }

    override suspend fun setSelectedArchetype(archetype: Archetype) {
        userPreferencesDataStore.setSelectedArchetypeName(archetype.name)
    }

    override fun getBiomechanicsDisclaimerShown(): Flow<Boolean> =
        userPreferencesDataStore.biomechanicsDisclaimerShown

    override suspend fun setBiomechanicsDisclaimerShown(shown: Boolean) {
        userPreferencesDataStore.setBiomechanicsDisclaimerShown(shown)
    }

    override fun getCustomLimbRatios(): Flow<LimbRatios> =
        userPreferencesDataStore.customLimbRatiosJson.map { rawJson ->
            rawJson?.let { runCatching { json.decodeFromString<LimbRatios>(it) }.getOrNull() }
                ?: DEFAULT_CUSTOM_RATIOS
        }

    override suspend fun setCustomLimbRatios(ratios: LimbRatios) {
        userPreferencesDataStore.setCustomLimbRatiosJson(json.encodeToString(ratios))
    }
}
