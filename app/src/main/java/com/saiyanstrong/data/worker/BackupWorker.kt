package com.saiyanstrong.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saiyanstrong.domain.usecase.BackupNowUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupNowUseCase: BackupNowUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        backupNowUseCase.execute().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )

    companion object {
        const val UNIQUE_WORK_NAME = "auto_backup"
    }
}
