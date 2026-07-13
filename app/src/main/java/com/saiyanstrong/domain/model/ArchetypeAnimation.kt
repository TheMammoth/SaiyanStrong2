package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/**
 * Design-language policy (spec section 3): [mechanicalFacts]/[stanceCue] must read as
 * mechanical/observational, never prescriptive ("this proportion produces X", not "you should
 * do X") — content authored under that rule lives in the JSON asset, not here.
 */
@Serializable
data class ArchetypeAnimation(
    val archetype: Archetype,
    val lift: LiftType,
    val limbRatios: LimbRatios,
    val keyframes: List<StickmanKeyframe>,
    val mechanicalFacts: List<String>,
    val stanceCue: String,
    val irrelevantCue: String
)
