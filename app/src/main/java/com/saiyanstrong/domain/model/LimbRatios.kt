package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** Every segment length/width as a fraction of standing body height — one independently
 * adjustable value per limb, per archetype. This is what makes "long femur" mean something
 * concrete: [thighRatio] is bigger and [shankRatio] smaller, while the leg's total length
 * ([thighRatio] + [shankRatio]) stays comparable across archetypes. */
@Serializable
data class LimbRatios(
    val thighRatio: Float,
    val shankRatio: Float,
    val torsoRatio: Float,
    val headNeckRatio: Float,
    val footLenRatio: Float,
    val shoulderHalfRatio: Float,
    val hipHalfRatio: Float,
    val kneeHalfRatio: Float,
    val ankleHalfRatio: Float,
    val barRiseRatio: Float,
    val gripHalfRatio: Float
)
