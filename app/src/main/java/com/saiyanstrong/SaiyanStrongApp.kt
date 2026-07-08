package com.saiyanstrong

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.seed.ExerciseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SaiyanStrongApp : Application(), Configuration.Provider {

    @Inject lateinit var exerciseDao: ExerciseDao
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            exerciseDao.insertAll(ExerciseSeeder.DATA)
        }
    }
}
