package com.example.glasslauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.cos
import kotlin.math.sin

/** What sits behind the glass: the user's picture, or slowly drifting color blobs. */
@Composable
fun LauncherBackground(wallpaper: ImageBitmap?, modifier: Modifier = Modifier) {
    if (wallpaper != null) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AuroraBackground(modifier)
    }
}

@Composable
private fun AuroraBackground(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF0B0B2E), Color(0xFF1A0F3D), Color(0xFF07141F)))
        )
        val blobs = listOf(
            Triple(Color(0xFFE85DFF), 0.30f, 0.25f),
            Triple(Color(0xFF3FD5FF), 0.70f, 0.45f),
            Triple(Color(0xFFFF8A3D), 0.40f, 0.75f),
            Triple(Color(0xFF5BFFB0), 0.80f, 0.85f),
        )
        blobs.forEachIndexed { i, (color, bx, by) ->
            val phase = t + i * 1.7f
            val center = Offset(
                size.width * (bx + 0.12f * cos(phase)),
                size.height * (by + 0.08f * sin(phase * 1.3f)),
            )
            val radius = size.minDimension * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.75f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
