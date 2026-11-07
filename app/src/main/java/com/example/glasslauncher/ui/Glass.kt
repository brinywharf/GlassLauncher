package com.example.glasslauncher.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The one place that defines what "liquid glass" means in this launcher.
 * Tweak these numbers to change the look everywhere.
 *
 *  - tint            : a thin color wash on top of the glass (more alpha = more frosted / solid)
 *  - blurRadius      : how blurry things behind the glass look
 *  - refractionHeight: how wide the bending zone along the edges is
 *  - refractionAmount: how strongly the edges bend the background (the "thick glass" look)
 *
 * Note: with lens() the shape must be a Compose CornerBasedShape
 * (RoundedCornerShape / CircleShape), not a custom Shape.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: CornerBasedShape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    blurRadius: Dp = 3.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(refractionHeight.toPx(), refractionAmount.toPx())
    },
    onDrawSurface = { drawRect(tint) },
)
