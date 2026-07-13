package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** The 3 angles from spec section 7's table — the real source of truth for a pose. Interpolating
 * these 3 scalars between keyframes (not raw node positions) is what keeps limb lengths rigid
 * during the scrub animation; see [com.saiyanstrong.domain.util.StickmanKinematics]. */
@Serializable
data class PoseAngles(
    val hipAngleDeg: Float,
    val kneeAngleDeg: Float,
    val torsoAngleDeg: Float
)
