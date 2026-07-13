package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** Phase 1 ships SQUAT only — DEADLIFT exists in the model so the JSON/UI shape is ready,
 * but no keyframe content exists for it yet and the Lift Selector shows it disabled. */
@Serializable
enum class LiftType { SQUAT, DEADLIFT }
