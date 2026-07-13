package com.saiyanstrong.presentation.screens.biomechanics

import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodeId.BAR
import com.saiyanstrong.domain.model.NodeId.HEAD
import com.saiyanstrong.domain.model.NodeId.HIP_CENTER
import com.saiyanstrong.domain.model.NodeId.L_ANKLE
import com.saiyanstrong.domain.model.NodeId.L_ELBOW
import com.saiyanstrong.domain.model.NodeId.L_HIP
import com.saiyanstrong.domain.model.NodeId.L_KNEE
import com.saiyanstrong.domain.model.NodeId.L_SHOULDER
import com.saiyanstrong.domain.model.NodeId.L_TOE
import com.saiyanstrong.domain.model.NodeId.L_WRIST
import com.saiyanstrong.domain.model.NodeId.NECK_BASE
import com.saiyanstrong.domain.model.NodeId.R_ANKLE
import com.saiyanstrong.domain.model.NodeId.R_ELBOW
import com.saiyanstrong.domain.model.NodeId.R_HIP
import com.saiyanstrong.domain.model.NodeId.R_KNEE
import com.saiyanstrong.domain.model.NodeId.R_SHOULDER
import com.saiyanstrong.domain.model.NodeId.R_TOE
import com.saiyanstrong.domain.model.NodeId.R_WRIST
import com.saiyanstrong.domain.model.NodePosition

/**
 * Pure Kotlin skeleton topology (no Android/Compose dependency, per the spec's explicit "no
 * View/Context deps, testable" requirement for StickmanRenderer) — the actual drawing calls
 * (which need a DrawScope receiver) live in the [StickmanCanvas] composable, which only
 * consumes what's exposed here. Segment groups and their order match spec section 6's draw
 * order exactly (floor and bar are handled by the composable directly, not listed here).
 */
object StickmanRenderer {

    /** Every joint that gets a dot in the final "joint dots (all, on top)" pass. HEAD is drawn
     * larger (r=12dp vs 8dp) — see [isHeadNode]. BAR is excluded: it's a line, not a joint. */
    val jointDotNodes: List<NodeId> = listOf(
        HEAD, NECK_BASE,
        L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST,
        HIP_CENTER, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE, L_TOE, R_TOE
    )

    fun isHeadNode(id: NodeId): Boolean = id == HEAD

    private val rightLegSegments = listOf(HIP_CENTER to R_HIP, R_HIP to R_KNEE, R_KNEE to R_ANKLE, R_ANKLE to R_TOE)
    private val leftLegSegments = listOf(HIP_CENTER to L_HIP, L_HIP to L_KNEE, L_KNEE to L_ANKLE, L_ANKLE to L_TOE)
    private val torsoSegments = listOf(NECK_BASE to HIP_CENTER)
    private val rightArmSegments = listOf(NECK_BASE to R_SHOULDER, R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST, R_WRIST to BAR)
    private val leftArmSegments = listOf(NECK_BASE to L_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST, L_WRIST to BAR)
    private val headSegments = listOf(NECK_BASE to HEAD)

    /** Draw order (back to front): right leg, left leg, torso, right arm, left arm, head-neck
     * line. Floor and bar are drawn by the composable before/after this, per spec section 6. */
    val limbSegmentGroupsInOrder: List<List<Pair<NodeId, NodeId>>> =
        listOf(rightLegSegments, leftLegSegments, torsoSegments, rightArmSegments, leftArmSegments, headSegments)

    fun findNode(nodes: List<NodePosition>, id: NodeId): NodePosition? = nodes.firstOrNull { it.id == id }

    /** Floor sits just below the lowest foot/ankle point actually present in [nodes], rather
     * than a hardcoded canvas fraction — stays correct if the underlying geometry is retuned. */
    fun floorYFraction(nodes: List<NodePosition>, marginFraction: Float = 0.02f): Float {
        val lowestY = listOf(L_ANKLE, R_ANKLE, L_TOE, R_TOE)
            .mapNotNull { findNode(nodes, it)?.y }
            .maxOrNull() ?: return 1f - marginFraction
        return (lowestY + marginFraction).coerceAtMost(1f)
    }
}
