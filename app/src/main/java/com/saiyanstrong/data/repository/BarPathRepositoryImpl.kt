package com.saiyanstrong.data.repository

import com.saiyanstrong.data.local.dao.BarPathMetricsDao
import com.saiyanstrong.data.local.entity.BarPathMetricsEntity
import com.saiyanstrong.domain.model.BarPathAnalysis
import com.saiyanstrong.domain.model.VelocityZone
import com.saiyanstrong.domain.repository.BarPathRepository
import kotlinx.coroutines.flow.Flow
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
        barPathMetricsDao.getForSetLog(setLogId).map { entity ->
            entity?.let {
                BarPathAnalysis(
                    peakVelocityMs = it.peakVelocityMs,
                    meanConcentricVelocityMs = it.meanConcentricVelocityMs,
                    peakPowerWatts = it.peakPowerWatts,
                    meanPowerWatts = it.meanPowerWatts,
                    rangeOfMotionCm = it.rangeOfMotionCm,
                    barPathDeviationCm = it.barPathDeviationCm,
                    velocityZone = VelocityZone.valueOf(it.velocityZone)
                )
            }
        }

    override suspend fun deleteBarPathMetrics(setLogId: Long) {
        barPathMetricsDao.deleteForSetLog(setLogId)
    }
}
