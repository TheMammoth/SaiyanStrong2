package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/**
 * 4 fixed body-proportion archetypes, plus [CUSTOM] — a 5th, permanent option where the user
 * dials in their own exact proportions via live sliders (femur/shin/torso/shoulder/hip/foot
 * ratios), persisted via [com.saiyanstrong.domain.repository.UserRepository]. CUSTOM's
 * [BiomechanicsRepository] entry reuses the PROPORTIONAL archetype's angle keyframes (sagittal
 * joint angles are a movement-pattern choice, not a body proportion) — only its [LimbRatios]
 * differ, and those come from the user's saved sliders instead of the JSON asset.
 */
@Serializable
enum class Archetype { LONG_FEMUR, SHORT_FEMUR, PROPORTIONAL, WIDE_HIP, CUSTOM }
