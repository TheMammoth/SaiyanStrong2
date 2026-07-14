package com.saiyanstrong.presentation.screens.biomechanics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodeId.L_WRIST
import com.saiyanstrong.domain.model.NodeId.R_WRIST
import com.saiyanstrong.domain.model.NodePosition
import com.saiyanstrong.domain.util.StickmanKinematics
import com.saiyanstrong.presentation.theme.PowerAmber
import com.saiyanstrong.presentation.theme.StickmanBar
import com.saiyanstrong.presentation.theme.StickmanBody
import com.saiyanstrong.presentation.theme.StickmanFloor
import kotlin.math.hypot

/** Degrees of yaw per pixel dragged horizontally — tuned so a full-width drag on a typical phone
 * (~350dp canvas) covers roughly the full -80..80 clamp range without feeling twitchy. */
private const val DEGREES_PER_PIXEL = 0.35f
private const val YAW_CLAMP = 80f

/**
 * Compose replacement for the spec's `StickmanCanvasView : View` — this project is Compose-only
 * (CLAUDE.md non-negotiable rule, no custom Views/XML). All topology/ordering logic lives in the
 * pure, unit-tested [StickmanRenderer]; this composable only maps normalized node coordinates to
 * pixels and issues the DrawScope calls, which can't be unit-tested without a real Canvas.
 *
 * Drag horizontally anywhere on the canvas to spin the figure on its own vertical axis (a fake
 * 3D "turntable" — see [StickmanKinematics.applyYaw]'s KDoc for what this does and doesn't show).
 * Yaw is transient per-instance UI state: `remember` scopes it to this composable's own call
 * site, so each [StickmanCanvas] on screen (e.g. every panel in the compare grid) rotates
 * independently, and it resets to 0° whenever the screen is left and reopened — a viewing
 * convenience, not a persisted body-proportion setting.
 */
@Composable
fun StickmanCanvas(
    nodes: List<NodePosition>,
    activeJoint: NodeId? = null,
    showBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    var yawDegrees by remember { mutableFloatStateOf(0f) }
    val rotatedNodes = remember(nodes, yawDegrees) { StickmanKinematics.applyYaw(nodes, yawDegrees) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    yawDegrees = (yawDegrees + dragAmount.x * DEGREES_PER_PIXEL)
                        .coerceIn(-YAW_CLAMP, YAW_CLAMP)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        fun px(id: NodeId): Offset? =
            StickmanRenderer.findNode(rotatedNodes, id)?.let { Offset(it.x * w, it.y * h) }

        // 1. Floor — a plain thin line, not dashed; less visual noise
        val floorY = StickmanRenderer.floorYFraction(nodes) * h
        drawLine(
            color = StickmanFloor,
            start = Offset(0f, floorY),
            end = Offset(w, floorY),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Bar — one line spanning both wrists, extended slightly beyond each. No separate
        // wrist-to-bar limb stubs are drawn (see StickmanRenderer): the arms already terminate
        // exactly at the wrist, which sits on this line by construction, so there's nothing left
        // to double-draw underneath it.
        val lWrist = px(L_WRIST)
        val rWrist = px(R_WRIST)
        if (showBar && lWrist != null && rWrist != null) {
            val dx = rWrist.x - lWrist.x
            val dy = rWrist.y - lWrist.y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val (ux, uy) = if (len > 0f) dx / len to dy / len else 1f to 0f
            val ext = 10.dp.toPx()
            drawLine(
                color = StickmanBar,
                start = Offset(lWrist.x - ux * ext, lWrist.y - uy * ext),
                end = Offset(rWrist.x + ux * ext, rWrist.y + uy * ext),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 3-7. Right leg, left leg, torso, right arm, left arm, head-neck line
        for (group in StickmanRenderer.limbSegmentGroupsInOrder) {
            for ((fromId, toId) in group) {
                val from = px(fromId) ?: continue
                val to = px(toId) ?: continue
                drawLine(color = StickmanBody, start = from, end = to, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        // 9. Joint dots, all on top — kept small so they read as landmarks, not blobs
        for (id in StickmanRenderer.jointDotNodes) {
            val point = px(id) ?: continue
            val radius = if (StickmanRenderer.isHeadNode(id)) 5.dp.toPx() else 3.dp.toPx()
            val color = if (id == activeJoint) PowerAmber else StickmanBody
            drawCircle(color = color, radius = radius, center = point)
        }
    }
}
