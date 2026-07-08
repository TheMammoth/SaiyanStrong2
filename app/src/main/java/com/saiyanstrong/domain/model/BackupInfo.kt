package com.saiyanstrong.domain.model

data class BackupInfo(
    val lastBackupAtMs: Long,
    val appVersionCode: Int
)
