package com.saiyanstrong.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * Manual GraphicsLayer creation/disposal via LocalGraphicsContext — the app's Compose UI
 * version (1.7.6, from compose-bom 2024.12.01) predates the androidx.compose.ui.graphics
 * `rememberGraphicsLayer()` convenience function, so this reimplements it.
 */
@Composable
fun rememberComposeGraphicsLayer(): GraphicsLayer {
    val graphicsContext = LocalGraphicsContext.current
    val graphicsLayer = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
    }
    return graphicsLayer
}
