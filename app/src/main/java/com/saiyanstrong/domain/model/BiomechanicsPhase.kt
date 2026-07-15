package com.saiyanstrong.domain.model

import kotlinx.serialization.Serializable

/**
 * Named "Biomechanics" (not "LiftPhase") to avoid colliding with
 * [com.saiyanstrong.domain.util.LiftPhase] — the VBT rep-timing state machine
 * (IDLE/SETTLING/READY/MOVING/COMPLETE). Same simple name in a different package would compile
 * fine on its own, but Phase 2B explicitly plans to show this visualizer alongside a VBT bar
 * path in the same screen, so a file that needs both would otherwise require an import alias.
 *
 * Squat and deadlift phases share one enum per the spec's literal Prompt 1 instruction — a
 * given [ArchetypeAnimation] only ever populates the subset valid for its own [LiftType].
 */
@Serializable
enum class BiomechanicsPhase {
    STANDING, DESCENT_MID, PARALLEL, BOTTOM,           // squat descent
    ASCENT_STICK, ASCENT_MID,                          // squat ascent (the grind, hips-lead)
    SETUP, FIRST_PULL, KNEE_PASS, LOCKOUT,             // deadlift
    PRESS_RACK, PRESS_MID, PRESS_STICK, PRESS_LOCKOUT  // overhead press
}
