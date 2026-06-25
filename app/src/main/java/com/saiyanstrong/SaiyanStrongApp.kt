package com.saiyanstrong

import android.app.Application
import com.saiyanstrong.data.local.dao.ExerciseDao
import com.saiyanstrong.data.local.seed.ExerciseSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SaiyanStrongApp : Application() {

    @Inject lateinit var exerciseDao: ExerciseDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            exerciseDao.insertAll(ExerciseSeeder.DATA)
        }
    }
}
