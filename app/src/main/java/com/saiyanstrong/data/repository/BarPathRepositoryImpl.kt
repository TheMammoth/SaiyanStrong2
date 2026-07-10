package com.saiyanstrong.data.repository

import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.VelocityZone
import com.saiyanstrong.domain.repository.BarPathRepository
import com.saiyanstrong.domain.repository.TimestampedBarPathAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarPathRepositoryImpl @Inject constructor(
    private val barPathMetricsDao: BarPathMetricsDao
) : BarPathRepository {

    // No unique DB constraint on set_log_id anymore (dropped in the v8->9 migration to
    // allow multiple freestanding/null rows) — "one row per set" is enforced here instead.
    override suspend fun saveBarPathMetrics(setLogId: Long, exerciseId: Int, analysis: BarPathAnalysis) {
        barPathMetricsDao.deleteForSetLog(setLogId)
        barPathMetricsDao.insert(
            BarPathMetricsEntity(
                setLogId = setLogId,
                exerciseId = exerciseId,
                createdAtMs = System.currentTimeMillis(),
                peakVelocityMs = analysis.peakVelocityMs,
                meanConcentricVelocityMs = analysis.meanConcentricVelocityMs,
                peakPowerWatts = analysis.peakPowerWatts,
                meanPowerWatts = analysis.meanPowerWatts,
                rangeOfMotionCm = analysis.rangeOfMotionCm,
                barPathDeviationCm = analysis.barPathDeviationCm,
                velocityZone = analysis.velocityZone.name
            )
        )
    }

    override fun getBarPathMetrics(setLogId: Long): Flow<BarPathAnalysis?> =
        barPathMetricsDao.getForSetLog(setLogId).map { entity -> entity?.toDomain() }

    override fun getBarPathMetricsForSets(setLogIds: List<Long>): Flow<Map<Long, BarPathAnalysis>> {
        if (setLogIds.isEmpty()) return flowOf(emptyMap())
        return barPathMetricsDao.getForSetLogIds(setLogIds).map { entities ->
            entities.associate { it.setLogId!! to it.toDomain() }
        }
    }

    override suspend fun deleteBarPathMetrics(setLogId: Long) {
        barPathMetricsDao.deleteForSetLog(setLogId)
    }

    override suspend fun saveFreestandingBarPathMetrics(exerciseId: Int, analysis: BarPathAnalysis) {
        barPathMetricsDao.insert(
            BarPathMetricsEntity(
                setLogId = null,
                exerciseId = exerciseId,
                createdAtMs = System.currentTimeMillis(),
                peakVelocityMs = analysis.peakVelocityMs,
                meanConcentricVelocityMs = analysis.meanConcentricVelocityMs,
                peakPowerWatts = analysis.peakPowerWatts,
                meanPowerWatts = analysis.meanPowerWatts,
                rangeOfMotionCm = analysis.rangeOfMotionCm,
                barPathDeviationCm = analysis.barPathDeviationCm,
                velocityZone = analysis.velocityZone.name
            )
        )
    }

    override fun getFreestandingAnalysesForExercise(exerciseId: Int): Flow<List<TimestampedBarPathAnalysis>> =
        barPathMetricsDao.getFreestandingForExercise(exerciseId).map { entities ->
            entities.map { TimestampedBarPathAnalysis(it.createdAtMs, it.toDomain()) }
        }

    override fun getFreestandingCountThisMonth(): Flow<Int> {
        val monthStartMs = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return barPathMetricsDao.countFreestandingSince(monthStartMs)
    }

    private fun BarPathMetricsEntity.toDomain() = BarPathAnalysis(
        peakVelocityMs = peakVelocityMs,
        meanConcentricVelocityMs = meanConcentricVelocityMs,
        peakPowerWatts = peakPowerWatts,
        meanPowerWatts = meanPowerWatts,
        rangeOfMotionCm = rangeOfMotionCm,
        barPathDeviationCm = barPathDeviationCm,
        velocityZone = VelocityZone.valueOf(velocityZone)
    )
}
