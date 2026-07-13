package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** Normalized canvas coordinates: x=0 left / x=1 right, y=0 top / y=1 bottom. */
@Serializable
data class NodePosition(
    val id: NodeId,
    val x: Float,
    val y: Float
)
