package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/** Card metadata for the archetype selection screen (name/description) — separate from
 * [ArchetypeAnimation]'s per-lift keyframe content, since this is needed before a lift is
 * even chosen. */
@Serializable
data class ArchetypeInfo(
    val archetype: Archetype,
    val name: String,
    val description: String
)
