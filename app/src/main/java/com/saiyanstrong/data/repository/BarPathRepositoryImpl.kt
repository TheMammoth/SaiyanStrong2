package com.saiyanstrong.data.repository

import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.VelocityZone
import com.saiyanstrong.domain.repository.BarPathRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BarPathRepositoryImpl @Inject constructor(
    private val barPathMetricsDao: BarPathMetricsDao
) : BarPathRepository {

    override suspend fun saveBarPathMetrics(setLogId: Long, analysis: BarPathAnalysis) {
        barPathMetricsDao.insert(
            BarPathMetricsEntity(
                setLogId = setLogId,
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
            entities.associate { it.setLogId to it.toDomain() }
        }
    }

    override suspend fun deleteBarPathMetrics(setLogId: Long) {
        barPathMetricsDao.deleteForSetLog(setLogId)
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
