package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.BackupInfo
import kotlinx.coroutines.flow.Flow

interface BackupRepository {
    val lastBackupInfo: Flow<BackupInfo?>

    suspend fun backupNow(): Result<Unit>
    suspend fun restoreLatest(): Result<Unit>

    /** Fire-and-forget: enqueues a background auto-backup, no-op when signed out. */
    fun scheduleAutoBackup()
}
