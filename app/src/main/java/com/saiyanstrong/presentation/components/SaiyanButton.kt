package com.saiyanstrong.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.theme.MatteBlack
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanGray

// Two-layer border: bright 1dp inner + dim 2dp outer halo = glow on dark backgrounds
fun Modifier.saiyanGlowBorder(color: Color = NeonGreen): Modifier = this
    .border(2.dp, color.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
    .border(1.dp, color, RoundedCornerShape(6.dp))

fun Modifier.scanlineTexture(
    backgroundColor: Color = MatteBlack
): Modifier = this.drawBehind {
    drawRect(backgroundColor)
    val spacing = density * 4f
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = Color.White.copy(alpha = 0.03f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacing
    }
}

@Composable
fun SaiyanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MatteBlack,
            contentColor = Color.White,
            disabledContainerColor = SaiyanGray,
            disabledContentColor = Color.White.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, if (enabled) NeonGreen else NeonGreen.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
        content = content
    )
}

@Composable
fun WeightKnobButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(SaiyanGray)
            .border(1.dp, NeonGreen, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = NeonGreen,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}
