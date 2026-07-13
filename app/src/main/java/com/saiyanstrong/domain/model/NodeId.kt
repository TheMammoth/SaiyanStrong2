package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** 17 body nodes + 1 bar node = 18, per the biomechanics visualizer skeleton map. */
@Serializable
enum class NodeId {
    HEAD, NECK_BASE,
    L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
    HIP_CENTER, L_HIP, R_HIP,
    L_KNEE, R_KNEE, L_ANKLE, R_ANKLE, L_TOE, R_TOE,
    BAR
}
