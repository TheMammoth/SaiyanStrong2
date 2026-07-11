package com.saiyanstrong.util.barpath

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.saiyanstrong.domain.util.angularVelocityMagnitude

/**
 * Always-on gyroscope listener driving the pre-tap stability indicator — separate from
 * [BarPathVideoRecorder]'s own gyro listener, which only runs during an active recording (for
 * offline shake compensation, a different concern with a different lifecycle). This one runs
 * for as long as the camera preview is visible, independent of whether recording has started.
 *
 * `SENSOR_DELAY_UI` (not `SENSOR_DELAY_FASTEST`) — this only drives a UI hint, not a precision
 * measurement, so there's no reason to burn extra CPU/battery sampling faster than the eye can see.
 *
 * Callbacks land on the thread [start] was called from (no Handler specified) — call from the
 * main/Compose thread and it's safe to update Compose state directly from [onMagnitudeChanged].
 */
class StabilityMonitor(
    private val onMagnitudeChanged: (Float) -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null

    fun start(context: Context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = manager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
        gyroSensor = sensor
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        gyroSensor = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        onMagnitudeChanged(angularVelocityMagnitude(event.values[0], event.values[1], event.values[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
