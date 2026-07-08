package com.saiyanstrong.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import com.saiyanstrong.BuildConfig
import com.saiyanstrong.data.backup.BackupEnvelope
import com.saiyanstrong.data.backup.BackupSerializer
import com.saiyanstrong.data.datastore.UserPreferencesDataStore
import com.saiyanstrong.data.worker.BackupWorker
import com.saiyanstrong.domain.model.BackupInfo
import com.saiyanstrong.domain.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient,
    private val backupSerializer: BackupSerializer,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : BackupRepository {

    override val lastBackupInfo: Flow<BackupInfo?> = combine(
        userPreferencesDataStore.lastBackupAtMs,
        userPreferencesDataStore.lastBackupVersionCode
    ) { atMs, versionCode ->
        if (atMs == 0L) null else BackupInfo(atMs, versionCode)
    }

    override suspend fun backupNow(): Result<Unit> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val nowMs = System.currentTimeMillis()
        val envelope = BackupEnvelope(
            timestampMs = nowMs,
            appVersionCode = BuildConfig.VERSION_CODE,
            payload = backupSerializer.buildPayload()
        )
        val json = backupSerializer.encode(envelope)
        supabaseClient.storage.from(BACKUPS_BUCKET).upload(
            path = "$userId/latest.json",
            data = json.toByteArray()
        ) { upsert = true }
        userPreferencesDataStore.setLastBackupInfo(nowMs, BuildConfig.VERSION_CODE)
    }

    override suspend fun restoreLatest(): Result<Unit> = runCatching {
        val userId = supabaseClient.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val bytes = supabaseClient.storage.from(BACKUPS_BUCKET).downloadAuthenticated("$userId/latest.json")
        val envelope = backupSerializer.decode(String(bytes))
        check(envelope.appVersionCode <= BuildConfig.VERSION_CODE) {
            "Backup was made with a newer app version — update the app first."
        }
        backupSerializer.restore(envelope.payload)
    }

    override fun scheduleAutoBackup() {
        if (supabaseClient.auth.currentUserOrNull() == null) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(BackupWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val BACKUPS_BUCKET = "backups"
    }
}
