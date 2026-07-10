package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.BarPathAnalysis
import kotlinx.coroutines.flow.Flow

interface BarPathRepository {
    suspend fun saveBarPathMetrics(setLogId: Long, exerciseId: Int, analysis: BarPathAnalysis)
    fun getBarPathMetrics(setLogId: Long): Flow<BarPathAnalysis?>
    fun getBarPathMetricsForSets(setLogIds: List<Long>): Flow<Map<Long, BarPathAnalysis>>
    suspend fun deleteBarPathMetrics(setLogId: Long)

    suspend fun saveFreestandingBarPathMetrics(exerciseId: Int, analysis: BarPathAnalysis)
    fun getFreestandingAnalysesForExercise(exerciseId: Int): Flow<List<TimestampedBarPathAnalysis>>
    fun getFreestandingCountThisMonth(): Flow<Int>
}

/** A freestanding analysis has no set to hang a date off of, so it carries its own timestamp. */
data class TimestampedBarPathAnalysis(val createdAtMs: Long, val analysis: BarPathAnalysis)
