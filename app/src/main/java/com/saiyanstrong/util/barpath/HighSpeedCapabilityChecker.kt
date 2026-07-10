package com.saiyanstrong.util.barpath

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider

enum class HighSpeedTier { FPS_120, FPS_60, STANDARD_30 }

/**
 * `CameraConstrainedHighSpeedCaptureSession` (the raw Camera2 API for guaranteed high-fps
 * recording) isn't reachable through CameraX — this codebase's camera pipeline. Instead this
 * checks `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES`, the standard characteristic every
 * Camera2-backed device exposes, to see whether the back camera can be nudged toward a high
 * frame rate via Camera2Interop on a normal (non-constrained) capture session. This works on
 * many — not all — devices; it's not the hardware guarantee the constrained high-speed session
 * type provides, but it stays inside CameraX's architecture rather than requiring a parallel
 * raw-Camera2 recording path.
 */
@OptIn(ExperimentalCamera2Interop::class)
object HighSpeedCapabilityChecker {
    fun check(cameraProvider: ProcessCameraProvider): HighSpeedTier {
        val backCameraInfo = CameraSelector.DEFAULT_BACK_CAMERA
            .filter(cameraProvider.availableCameraInfos)
            .firstOrNull() ?: return HighSpeedTier.STANDARD_30

        val ranges = Camera2CameraInfo.from(backCameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: return HighSpeedTier.STANDARD_30

        val maxFps = ranges.maxOfOrNull { it.upper } ?: 30
        return when {
            maxFps >= 120 -> HighSpeedTier.FPS_120
            maxFps >= 60 -> HighSpeedTier.FPS_60
            else -> HighSpeedTier.STANDARD_30
        }
    }
}
