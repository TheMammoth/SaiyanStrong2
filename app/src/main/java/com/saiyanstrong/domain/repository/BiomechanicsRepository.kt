package com.saiyanstrong.domain.repository

import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.ArchetypeAnimation
import com.saiyanstrong.domain.model.ArchetypeInfo
import com.saiyanstrong.domain.model.LiftType

interface BiomechanicsRepository {
    /** @throws IllegalStateException if no keyframe content exists for this archetype/lift
     * pairing (currently true for every DEADLIFT entry — Phase 1 ships SQUAT only, and the
     * UI never requests DEADLIFT since its Lift Selector button stays disabled). */
    suspend fun getAnimation(archetype: Archetype, lift: LiftType): ArchetypeAnimation

    suspend fun getArchetypeInfoList(): List<ArchetypeInfo>

    fun getSupportedArchetypes(): List<Archetype>
}
