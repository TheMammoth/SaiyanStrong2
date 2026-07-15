package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** The angles from spec section 7's table — the real source of truth for a pose. Interpolating
 * these scalars between keyframes (not raw node positions) is what keeps limb lengths rigid
 * during the scrub animation; see [com.saiyanstrong.domain.util.StickmanKinematics].
 *
 * [torsoLeanBiasDeg] is the degree of freedom that makes a real ascent possible: a squat pose is
 * otherwise a pure function of [kneeAngleDeg] (torso is solved from balance), so descending and
 * ascending at the same knee angle would be geometrically identical. The bias is applied as a
 * rotation about the hip on top of the solved/authored torso lean — a rotation never changes
 * segment length, so rigidity is preserved. A positive value pitches the torso further forward
 * than balance (the bar drifts forward of mid-foot — exactly what a grind looks like). Descent
 * keyframes use ~0; ascent "sticking-point" keyframes use a positive value that decays to 0 by
 * lockout. Defaulted to 0 so every pre-existing keyframe JSON still decodes unchanged. */
@Serializable
data class PoseAngles(
    val hipAngleDeg: Float,
    val kneeAngleDeg: Float,
    val torsoAngleDeg: Float,
    val torsoLeanBiasDeg: Float = 0f
)
