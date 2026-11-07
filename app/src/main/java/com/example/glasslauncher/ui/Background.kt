package com.example.glasslauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * What sits behind the glass: the user's picture, or a still aurora.
 * It must NOT animate: anything moving here would force every glass surface to redraw each frame.
 */
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
        Canvas(modifier) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0B2E), Color(0xFF1A0F3D), Color(0xFF07141F))))
            val blobs = listOf(
                Triple(Color(0xFFE85DFF), 0.22f, 0.20f),
                Triple(Color(0xFF3FD5FF), 0.85f, 0.38f),
                Triple(Color(0xFFFF8A3D), 0.30f, 0.70f),
                Triple(Color(0xFF5BFFB0), 0.85f, 0.88f),
            )
            blobs.forEach { (color, bx, by) ->
                val center = Offset(size.width * bx, size.height * by)
                val radius = size.minDimension * 0.6f
                drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = 0.75f), Color.Transparent), center, radius),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}
