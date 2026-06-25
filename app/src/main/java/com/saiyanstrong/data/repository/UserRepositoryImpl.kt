package com.saiyanstrong.data.repository

import com.saiyanstrong.data.datastore.UserPreferencesDataStore
import com.saiyanstrong.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : UserRepository {

    override fun getLifetimePowerEarned(): Flow<Int> =
        userPreferencesDataStore.lifetimePowerEarned

    override suspend fun addPowerEarned(amount: Int) {
        userPreferencesDataStore.addPowerEarned(amount)
    }
}
