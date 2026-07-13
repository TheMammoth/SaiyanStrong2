package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/**
 * Fixed body-proportion archetypes for the biomechanics visualizer. Phase 1 ships exactly
 * these 4 — no continuous sliders (that's a Phase 2 femur-ratio blend between SHORT_FEMUR
 * and LONG_FEMUR, not built yet).
 */
@Serializable
enum class Archetype { LONG_FEMUR, SHORT_FEMUR, PROPORTIONAL, WIDE_HIP }
