package com.saiyanstrong.presentation.screens.visualizer

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.saiyanstrong.domain.model.MuscleGroup
import com.saiyanstrong.presentation.theme.NeonGreen

private data class MuscleRegion(
    val cx: Float,  // normalized center x  0..1
    val cy: Float,  // normalized center y  0..1
    val rx: Float,  // x-radius as fraction of image width
    val ry: Float   // y-radius as fraction of image height
)

// Approximate positions for a standard front-view full-body anatomy PNG
private val MUSCLE_REGIONS: Map<MuscleGroup, List<MuscleRegion>> = mapOf(
    MuscleGroup.QUADRICEPS to listOf(
        MuscleRegion(0.38f, 0.65f, 0.07f, 0.10f),
        MuscleRegion(0.62f, 0.65f, 0.07f, 0.10f)
    ),
    MuscleGroup.GLUTEUS_MAXIMUS to listOf(
        MuscleRegion(0.43f, 0.53f, 0.08f, 0.05f),
        MuscleRegion(0.57f, 0.53f, 0.08f, 0.05f)
    ),
    MuscleGroup.HAMSTRINGS to listOf(
        MuscleRegion(0.38f, 0.68f, 0.06f, 0.09f),
        MuscleRegion(0.62f, 0.68f, 0.06f, 0.09f)
    ),
    MuscleGroup.ERECTOR_SPINAE to listOf(
        MuscleRegion(0.46f, 0.43f, 0.04f, 0.10f),
        MuscleRegion(0.54f, 0.43f, 0.04f, 0.10f)
    ),
    MuscleGroup.PECTORALIS_MAJOR to listOf(
        MuscleRegion(0.43f, 0.28f, 0.09f, 0.06f),
        MuscleRegion(0.57f, 0.28f, 0.09f, 0.06f)
    ),
    MuscleGroup.DELTOIDS to listOf(
        MuscleRegion(0.29f, 0.25f, 0.06f, 0.05f),
        MuscleRegion(0.71f, 0.25f, 0.06f, 0.05f)
    ),
    MuscleGroup.TRICEPS to listOf(
        MuscleRegion(0.27f, 0.34f, 0.04f, 0.08f),
        MuscleRegion(0.73f, 0.34f, 0.04f, 0.08f)
    ),
    MuscleGroup.BICEPS to listOf(
        MuscleRegion(0.30f, 0.33f, 0.04f, 0.07f),
        MuscleRegion(0.70f, 0.33f, 0.04f, 0.07f)
    ),
    MuscleGroup.LATISSIMUS_DORSI to listOf(
        MuscleRegion(0.35f, 0.38f, 0.06f, 0.08f),
        MuscleRegion(0.65f, 0.38f, 0.06f, 0.08f)
    ),
    MuscleGroup.TRAPEZIUS to listOf(
        MuscleRegion(0.44f, 0.20f, 0.06f, 0.04f),
        MuscleRegion(0.56f, 0.20f, 0.06f, 0.04f)
    ),
    MuscleGroup.RECTUS_ABDOMINIS to listOf(
        MuscleRegion(0.50f, 0.40f, 0.07f, 0.08f)
    ),
    MuscleGroup.CALVES to listOf(
        MuscleRegion(0.38f, 0.82f, 0.05f, 0.07f),
        MuscleRegion(0.62f, 0.82f, 0.05f, 0.07f)
    )
)

@Composable
fun AnatomyOverlayCanvas(
    assetName: String,
    highlightedMuscles: List<MuscleGroup>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap = remember(assetName) {
        runCatching {
            context.assets.open("anatomy/$assetName.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    } ?: return

    val aspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
    ) {
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )

        highlightedMuscles.forEach { muscle ->
            MUSCLE_REGIONS[muscle]?.forEach { region ->
                drawOval(
                    color = NeonGreen.copy(alpha = 0.45f),
                    topLeft = Offset(
                        x = (region.cx - region.rx) * size.width,
                        y = (region.cy - region.ry) * size.height
                    ),
                    size = Size(
                        width = region.rx * 2f * size.width,
                        height = region.ry * 2f * size.height
                    )
                )
            }
        }
    }
}
