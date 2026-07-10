package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.BarPathAnalysis
import kotlinx.coroutines.flow.Flow

interface BarPathRepository {
    suspend fun saveBarPathMetrics(setLogId: Long, analysis: BarPathAnalysis)
    fun getBarPathMetrics(setLogId: Long): Flow<BarPathAnalysis?>
    fun getBarPathMetricsForSets(setLogIds: List<Long>): Flow<Map<Long, BarPathAnalysis>>
    suspend fun deleteBarPathMetrics(setLogId: Long)
}
