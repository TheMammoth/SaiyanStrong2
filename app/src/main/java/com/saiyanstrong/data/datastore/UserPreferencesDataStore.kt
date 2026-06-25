package com.saiyanstrong.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

private val LIFETIME_POWER_EARNED = intPreferencesKey("lifetime_power_earned")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lifetimePowerEarned: Flow<Int> = context.userPreferencesDataStore.data
        .map { preferences -> preferences[LIFETIME_POWER_EARNED] ?: 0 }

    suspend fun addPowerEarned(amount: Int) {
        context.userPreferencesDataStore.edit { preferences ->
            val current = preferences[LIFETIME_POWER_EARNED] ?: 0
            preferences[LIFETIME_POWER_EARNED] = current + amount
        }
    }
}
