package com.saiyanstrong.presentation.screens.barpath

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.domain.util.LiftPhase
import com.saiyanstrong.domain.util.ReticleState
import com.saiyanstrong.presentation.theme.DangerRed
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.PowerAmber
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The lock-on targeting reticle — the state between a successful tap (color sampled) and the
 * lift actually starting. Confirms to the user that the tracker has found (and is holding) the
 * right object before they trust it enough to lift.
 *
 * ADAPTED from the original ask in two ways, both forced by this project's real architecture
 * (CLAUDE.md: "UI: Jetpack Compose only. No XML layouts, ever" — a non-negotiable rule, listed
 * as such): this is a Compose composable drawing on a Compose [Canvas], not an
 * `android.view.View` subclass, and all animation goes through Compose's own first-party
 * `androidx.compose.animation` APIs (`Animatable`, `animateFloatAsState`, `spring()`,
 * `rememberInfiniteTransition`) rather than `ValueAnimator`/`ObjectAnimator` — these ARE the
 * platform-native, non-third-party animation system for this UI toolkit, so the actual intent of
 * "no third-party animation libraries" is honored, just through the toolkit this app is built on.
 *
 * [markerFrame]/[boxSize] positioning carries the SAME approximation already flagged for the
 * tap-to-sample coordinate mapping (Sprint 39/44): it assumes the downsampled analysis buffer and
 * the displayed preview box share the same aspect ratio/crop, with no correction for CameraX's
 * FILL_CENTER crop possibly differing between the Preview and ImageAnalysis streams. Unverified
 * on a device — this is the first time that gap becomes visually consequential (a positioned
 * overlay, not just a numeric readout).
 */
@Composable
fun LockOnReticle(
    state: ReticleState,
    confidence: Float,
    markerFrame: BarPathCaptureViewModel.LiveMarkerFrame?,
    tapAnchor: Offset?,
    boxSize: Size,
    liftPhase: LiftPhase,
    modifier: Modifier = Modifier
) {
    if (boxSize.width <= 0f || boxSize.height <= 0f) return

    val rawTarget = markerFrame
        ?.takeIf { it.frameWidthPx > 0 && it.frameHeightPx > 0 }
        ?.let { Offset(it.xPx / it.frameWidthPx * boxSize.width, it.yPx / it.frameHeightPx * boxSize.height) }
        ?: tapAnchor
        ?: Offset(boxSize.width / 2f, boxSize.height / 2f)

    // Light exponential smoothing on the tracked position (spec: pos = pos*0.7 + new*0.3) — only
    // meaningful once real detections exist; before that, the raw tap anchor is a static point
    // with nothing to smooth.
    var smoothedPos by remember { mutableStateOf(rawTarget) }
    LaunchedEffect(rawTarget) {
        smoothedPos = if (markerFrame != null) {
            Offset(smoothedPos.x * 0.7f + rawTarget.x * 0.3f, smoothedPos.y * 0.7f + rawTarget.y * 0.3f)
        } else rawTarget
    }
    val displayPos = if (markerFrame != null) smoothedPos else rawTarget

    // Blob radius in screen space — diameter scaled by the same ratio used for position. Diameter
    // is an approximately isotropic measure, so either axis's scale factor is fine here.
    val blobRadiusPx = markerFrame
        ?.takeIf { it.frameWidthPx > 0 }
        ?.let { (it.diameterPx / it.frameWidthPx * boxSize.width) / 2f }
        ?: 0f

    val density = LocalDensity.current
    val minRadiusPx = with(density) { 40.dp.toPx() }
    val paddingPx = with(density) { 8.dp.toPx() }

    // Whole reticle fades out once the lift actually starts moving -- its job is done.
    val overallAlpha = remember { Animatable(1f) }
    LaunchedEffect(liftPhase) {
        overallAlpha.animateTo(if (liftPhase == LiftPhase.MOVING) 0f else 1f, animationSpec = tween(300))
    }
    if (overallAlpha.value <= 0f) return

    when (state) {
        ReticleState.SEARCHING -> SearchingReticle(displayPos, minRadiusPx, overallAlpha.value, modifier)
        ReticleState.ACQUIRING -> AcquiringReticle(
            displayPos, blobRadiusPx.takeIf { it > 0f } ?: minRadiusPx, overallAlpha.value, modifier
        )
        ReticleState.LOCKED -> LockedReticle(
            displayPos, blobRadiusPx, paddingPx, confidence, overallAlpha.value, modifier
        )
    }
}

@Composable
private fun SearchingReticle(pos: Offset, radiusPx: Float, alpha: Float, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "searchRotation")
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "searchRotationDeg"
    )
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            rotate(rotationDeg, pivot = pos) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f * alpha),
                    radius = radiusPx,
                    center = pos,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
        }
        Text(
            "Searching...",
            color = Color.White.copy(alpha = 0.6f * alpha),
            fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.offset { IntOffset((pos.x - 35.dp.toPx()).roundToInt(), (pos.y + radiusPx + 8.dp.toPx()).roundToInt()) }
        )
    }
}

@Composable
private fun AcquiringReticle(pos: Offset, targetRadiusPx: Float, alpha: Float, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "acquirePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(600), repeatMode = RepeatMode.Reverse),
        label = "acquirePulseAlpha"
    )
    val animatedRadius by animateFloatAsState(targetValue = targetRadiusPx, animationSpec = tween(400), label = "acquireRadius")
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = PowerAmber.copy(alpha = pulseAlpha * alpha),
                radius = animatedRadius.coerceAtLeast(4f),
                center = pos,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Text(
            "Acquiring...",
            color = PowerAmber.copy(alpha = alpha),
            fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.offset { IntOffset((pos.x - 35.dp.toPx()).roundToInt(), (pos.y + animatedRadius + 8.dp.toPx()).roundToInt()) }
        )
    }
}

@Composable
private fun LockedReticle(
    pos: Offset,
    blobRadiusPx: Float,
    paddingPx: Float,
    confidence: Float,
    alpha: Float,
    modifier: Modifier
) {
    val targetHalfSize = (blobRadiusPx + paddingPx).coerceAtLeast(20f)
    // "Snap" — a spring, not a linear tween, gives the quick-then-settle feel the ~200ms ask wants.
    val animatedHalfSize by animateFloatAsState(
        targetValue = targetHalfSize,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "lockSnapHalfSize"
    )

    // Fresh LaunchedEffect(Unit) scopes: this composable is only in the tree while state==LOCKED
    // (see the `when` in LockOnReticle), so re-entering LOCKED after leaving it is a genuinely
    // new composition — these restart correctly every time lock is (re)acquired, not just once.
    val labelAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        labelAlpha.snapTo(1f)
        delay(1500)
        labelAlpha.animateTo(0f, animationSpec = tween(400))
    }
    var showLiftPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        showLiftPrompt = true
    }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCornerBrackets(center = pos, halfSize = animatedHalfSize, color = NeonGreen.copy(alpha = alpha))
        }
        Text(
            "Locked ✓",
            color = NeonGreen.copy(alpha = alpha * labelAlpha.value),
            fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.offset { IntOffset((pos.x - 28.dp.toPx()).roundToInt(), (pos.y + animatedHalfSize + 8.dp.toPx()).roundToInt()) }
        )
        ConfidenceBar(
            confidence = confidence,
            modifier = Modifier.offset { IntOffset((pos.x - 40.dp.toPx()).roundToInt(), (pos.y + animatedHalfSize + 30.dp.toPx()).roundToInt()) }
        )
        if (showLiftPrompt) {
            Text(
                "LIFT WHEN READY",
                color = NeonGreen.copy(alpha = alpha),
                fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
            )
        }
    }
}

/** Camera-autofocus-style corner brackets: 4 L-shapes instead of a full square outline. */
private fun DrawScope.drawCornerBrackets(center: Offset, halfSize: Float, color: Color) {
    val arm = halfSize * 0.4f
    val strokeWidth = 3.dp.toPx()
    val left = center.x - halfSize; val right = center.x + halfSize
    val top = center.y - halfSize; val bottom = center.y + halfSize

    drawLine(color, Offset(left, top + arm), Offset(left, top), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(left, top), Offset(left + arm, top), strokeWidth, cap = StrokeCap.Round)

    drawLine(color, Offset(right - arm, top), Offset(right, top), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right, top + arm), strokeWidth, cap = StrokeCap.Round)

    drawLine(color, Offset(right, bottom - arm), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(right, bottom), Offset(right - arm, bottom), strokeWidth, cap = StrokeCap.Round)

    drawLine(color, Offset(left + arm, bottom), Offset(left, bottom), strokeWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - arm), strokeWidth, cap = StrokeCap.Round)
}

@Composable
private fun ConfidenceBar(confidence: Float, modifier: Modifier = Modifier) {
    val clamped = confidence.coerceIn(0f, 1f)
    val color = when {
        clamped >= 0.7f -> NeonGreen
        clamped >= 0.4f -> PowerAmber
        else -> DangerRed
    }
    Column(modifier) {
        Text("Tracking confidence", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Box(Modifier.width(80.dp).height(4.dp).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().width((80 * clamped).dp).background(color))
        }
    }
}

/** "Lock lost" banner — only needed unattended, since a lost lock while READY (pre-lift) requires
 * user action to recover (per spec, item 4: re-enter the tap flow, don't reset the session). */
@Composable
fun LockLostBanner(onRetap: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Lost marker — retap to reselect",
            color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onRetap, colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)) {
            Text("RETAP", fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}
