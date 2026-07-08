package com.saiyanstrong.domain.usecase

import com.saiyanstrong.domain.repository.BackupRepository
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository
) {
    suspend fun execute(): Result<Unit> = backupRepository.restoreLatest()
}
