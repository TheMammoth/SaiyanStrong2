package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** Angles, not baked node positions — see [PoseAngles]'s KDoc for why. */
@Serializable
data class StickmanKeyframe(
    val phase: BiomechanicsPhase,
    val angles: PoseAngles
)
