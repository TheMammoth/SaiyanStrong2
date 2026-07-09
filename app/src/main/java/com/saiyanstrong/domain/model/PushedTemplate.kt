package com.saiyanstrong.domain.model

/** A template a coach has pushed to an athlete, not yet accepted into the athlete's local templates. */
data class PushedTemplate(
    val id: String,
    val coachName: String?,
    val name: String,
    val exerciseIds: List<Int>,
    val createdAtMs: Long
)
