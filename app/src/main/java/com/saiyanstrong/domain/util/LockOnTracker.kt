package com.saiyanstrong.domain.util

import kotlin.math.sqrt

/** How confident the live tracker is that it's found (and is still holding) the user's marker,
 * distinct from [LiftPhase] — this is a detection-quality axis (is the blob real and stable?),
 * not a rep-timing axis (has the bar started moving?). Meaningful only once a tap has set a
 * [com.saiyanstrong.util.barpath.MarkerColorProfile] to detect against. */
enum class ReticleState { SEARCHING, ACQUIRING, LOCKED }

/**
 * @param state the current [ReticleState].
 * @param confidence 0f..1f, derived from how stable the blob's size has been over the recent
 * window — a real marker holds a roughly constant apparent size frame to frame; a false-positive
 * match (a shirt, a reflection) tends to fluctuate. Only meaningful once at least 2 samples exist.
 * @param justLostLock true on exactly the one frame LOCKED reverts to SEARCHING — a one-shot
 * event for the UI to react to (show "Lost marker", not just silently change state).
 */
data class LockOnUpdate(
    val state: ReticleState,
    val confidence: Float,
    val justLostLock: Boolean
)

/**
 * Pure per-frame classifier feeding the lock-on reticle. Pure Kotlin, no Android dependency —
 * mirrors [LiftPhaseDetector]'s pattern of a small stateful class driven by per-frame updates
 * from a ViewModel, kept fully unit-testable.
 *
 * State rules (all literal from spec, not just descriptive prose):
 *  - Not detected, never locked -> SEARCHING (no pre-lock miss tolerance: any miss before LOCKED
 *    immediately resets the consecutive-detection streak).
 *  - Detected, streak < [LOCK_THRESHOLD] consecutive frames -> ACQUIRING.
 *  - Detected, streak >= [LOCK_THRESHOLD] -> LOCKED.
 *  - Once LOCKED, brief misses are tolerated (a real tracker flickers occasionally) — only
 *    [LOCK_LOSS_MISS_FRAMES] consecutive misses drops back to SEARCHING (matches the spec's
 *    explicit "blob disappears for 10+ frames" trigger).
 */
class LockOnTracker(
    private val lockThreshold: Int = LOCK_THRESHOLD,
    private val lockLossMissFrames: Int = LOCK_LOSS_MISS_FRAMES,
    private val diameterWindowSize: Int = DIAMETER_WINDOW_SIZE
) {
    private var consecutiveDetections = 0
    private var missStreak = 0
    private var wasLocked = false
    private val diameterWindow = ArrayDeque<Double>()

    fun update(detected: Boolean, diameterPx: Double?): LockOnUpdate {
        if (detected && diameterPx != null && diameterPx > 0.0) {
            missStreak = 0
            consecutiveDetections++
            if (diameterWindow.size >= diameterWindowSize) diameterWindow.removeFirst()
            diameterWindow.addLast(diameterPx)

            val state = if (consecutiveDetections >= lockThreshold) ReticleState.LOCKED else ReticleState.ACQUIRING
            wasLocked = state == ReticleState.LOCKED
            return LockOnUpdate(state, currentConfidence(), justLostLock = false)
        }

        if (wasLocked) {
            missStreak++
            if (missStreak >= lockLossMissFrames) {
                consecutiveDetections = 0
                wasLocked = false
                diameterWindow.clear()
                return LockOnUpdate(ReticleState.SEARCHING, 0f, justLostLock = true)
            }
            // Still within tolerance -- stays LOCKED through a brief flicker.
            return LockOnUpdate(ReticleState.LOCKED, currentConfidence(), justLostLock = false)
        }

        consecutiveDetections = 0
        diameterWindow.clear()
        return LockOnUpdate(ReticleState.SEARCHING, 0f, justLostLock = false)
    }

    /** Coefficient-of-variation of the recent diameter window, inverted into a 0..1 confidence —
     * a low-variance (stable-size) blob scores high; a jittery/false-positive one scores low. */
    private fun currentConfidence(): Float {
        if (diameterWindow.size < 2) return 0f
        val mean = diameterWindow.average()
        if (mean <= 0.0) return 0f
        val variance = diameterWindow.sumOf { (it - mean) * (it - mean) } / diameterWindow.size
        val coefficientOfVariation = sqrt(variance) / mean
        return (1.0 - coefficientOfVariation).coerceIn(0.0, 1.0).toFloat()
    }

    /** Full reset — a new tap (or RE-TAP) starts the lock-on search fresh. */
    fun reset() {
        consecutiveDetections = 0
        missStreak = 0
        wasLocked = false
        diameterWindow.clear()
    }

    companion object {
        private const val LOCK_THRESHOLD = 5
        private const val LOCK_LOSS_MISS_FRAMES = 10
        private const val DIAMETER_WINDOW_SIZE = 10
    }
}
