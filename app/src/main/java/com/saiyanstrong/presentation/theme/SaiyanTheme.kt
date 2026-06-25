package com.saiyanstrong.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SaiyanDarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    secondary = PowerAmber,
    background = MatteBlack,
    surface = SaiyanGray,
    error = DangerRed
)

@Composable
fun SaiyanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaiyanDarkColorScheme,
        typography = SaiyanTypography,
        content = content
    )
}
