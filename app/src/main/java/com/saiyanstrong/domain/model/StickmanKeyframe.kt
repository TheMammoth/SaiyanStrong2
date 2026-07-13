package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StickmanKeyframe(
    val phase: BiomechanicsPhase,
    val nodes: List<NodePosition>
)
