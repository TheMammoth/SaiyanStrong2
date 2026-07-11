package com.saiyanstrong.domain.util

/**
 * A recorded gyroscope rotation timeline: the cumulative integrated angle (radians) about the
 * image X (pitch) and Y (yaw) axes at each sample's device-uptime timestamp (ns). Pure — the
 * Android SensorManager integrator appends samples during video capture; offline analysis queries
 * it to compensate each extracted frame's centroid. Samples are appended in monotonic time order.
 */
class GyroTimeline {
    private val timestampsNs = ArrayList<Long>()
    private val cumulativeX = ArrayList<Double>()
    private val cumulativeY = ArrayList<Double>()

    val isEmpty: Boolean get() = timestampsNs.isEmpty()
    val size: Int get() = timestampsNs.size

    fun addSample(uptimeNs: Long, cumulativeAngleX: Double, cumulativeAngleY: Double) {
        timestampsNs.add(uptimeNs)
        cumulativeX.add(cumulativeAngleX)
        cumulativeY.add(cumulativeAngleY)
    }

    /**
     * Linearly-interpolated cumulative (angleX, angleY) at [uptimeNs]; clamps to the first/last
     * sample outside the recorded range, and returns (0,0) if empty.
     */
    fun cumulativeAngleAt(uptimeNs: Long): Pair<Double, Double> {
        if (timestampsNs.isEmpty()) return 0.0 to 0.0
        if (uptimeNs <= timestampsNs.first()) return cumulativeX.first() to cumulativeY.first()
        if (uptimeNs >= timestampsNs.last()) return cumulativeX.last() to cumulativeY.last()

        var lo = 0
        var hi = timestampsNs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (timestampsNs[mid] <= uptimeNs) lo = mid else hi = mid
        }
        val t0 = timestampsNs[lo]; val t1 = timestampsNs[hi]
        val f = if (t1 == t0) 0.0 else (uptimeNs - t0).toDouble() / (t1 - t0)
        val ax = cumulativeX[lo] + (cumulativeX[hi] - cumulativeX[lo]) * f
        val ay = cumulativeY[lo] + (cumulativeY[hi] - cumulativeY[lo]) * f
        return ax to ay
    }
}
