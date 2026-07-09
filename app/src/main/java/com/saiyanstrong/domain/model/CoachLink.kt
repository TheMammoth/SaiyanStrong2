package com.saiyanstrong.domain.model

/** An athlete's view of one coach they've linked to, for the Settings "Linked Coaches" list. */
data class CoachLink(
    val linkId: String,
    val coachId: String,
    val coachEmail: String?,
    val coachDisplayName: String?,
    val linkedAtMs: Long
)
