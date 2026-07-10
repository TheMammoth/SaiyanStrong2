package com.saiyanstrong.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

private val LIFETIME_POWER_EARNED           = intPreferencesKey("lifetime_power_earned")
private val LAST_DISMISSED_UPDATE_VERSION   = stringPreferencesKey("last_dismissed_update_version")
private val USE_FEMALE_DOTS_FORMULA         = booleanPreferencesKey("use_female_dots_formula")
private val DEFAULT_REST_SECONDS            = intPreferencesKey("default_rest_seconds")
private val LAST_BACKUP_AT_MS               = longPreferencesKey("last_backup_at_ms")
private val LAST_BACKUP_VERSION_CODE        = intPreferencesKey("last_backup_version_code")
private val ONBOARDING_COMPLETE             = booleanPreferencesKey("onboarding_complete")
private val REST_TIMER_SOUNDS_ENABLED       = booleanPreferencesKey("rest_timer_sounds_enabled")
private val BAR_PATH_TIPS_DISMISSED         = booleanPreferencesKey("bar_path_tips_dismissed")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lifetimePowerEarned: Flow<Int> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[LIFETIME_POWER_EARNED] ?: 0 }

    val lastDismissedUpdateVersion: Flow<String> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[LAST_DISMISSED_UPDATE_VERSION] ?: "" }

    suspend fun addPowerEarned(amount: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            val current = preferences[LIFETIME_POWER_EARNED] ?: 0
            preferences[LIFETIME_POWER_EARNED] = (current + amount).coerceAtLeast(0)
        }
    }

    suspend fun setLifetimePowerEarned(total: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[LIFETIME_POWER_EARNED] = total
        }
    }

    suspend fun saveDismissedUpdateVersion(version: String) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[LAST_DISMISSED_UPDATE_VERSION] = version
        }
    }

    val useFemaleDotsFormula: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[USE_FEMALE_DOTS_FORMULA] ?: false }

    suspend fun setUseFemaleDotsFormula(useFemale: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[USE_FEMALE_DOTS_FORMULA] = useFemale
        }
    }

    val defaultRestSeconds: Flow<Int> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[DEFAULT_REST_SECONDS] ?: 90 }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[DEFAULT_REST_SECONDS] = seconds.coerceIn(10, 600)
        }
    }

    val lastBackupAtMs: Flow<Long> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[LAST_BACKUP_AT_MS] ?: 0L }

    val lastBackupVersionCode: Flow<Int> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[LAST_BACKUP_VERSION_CODE] ?: 0 }

    suspend fun setLastBackupInfo(atMs: Long, versionCode: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[LAST_BACKUP_AT_MS] = atMs
            preferences[LAST_BACKUP_VERSION_CODE] = versionCode
        }
    }

    val onboardingComplete: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }

    val restTimerSoundsEnabled: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[REST_TIMER_SOUNDS_ENABLED] ?: true }

    suspend fun setRestTimerSoundsEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[REST_TIMER_SOUNDS_ENABLED] = enabled
        }
    }

    val barPathTipsDismissed: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[BAR_PATH_TIPS_DISMISSED] ?: false }

    suspend fun setBarPathTipsDismissed(dismissed: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[BAR_PATH_TIPS_DISMISSED] = dismissed
        }
    }
}
