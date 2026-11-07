package com.example.glasslauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.util.Locale

/** Tiny translation helper: Turkish if the phone is in Turkish, otherwise English. */
fun t(en: String, tr: String): String = if (Locale.getDefault().language == "tr") tr else en

/** Global glass strength, set from Settings. */
@Immutable
data class GlassConfig(
    val refraction: Float = 1.6f,
    val blur: Float = 1.2f,
    val frost: Float = 0.06f,
    val rim: Boolean = true,
)

val LocalGlass = compositionLocalOf { GlassConfig() }

val LabelStyle = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 6f),
)

/**
 * The liquid glass material used everywhere.
 *
 *  - refraction: lens() bends the background near the edges (the "thick glass" look)
 *  - blur + vibrancy: frosted, saturated background
 *  - frost / tint: a color wash on the surface
 *  - rim light: a bright specular edge + soft top gloss, like light hitting real glass
 *
 * Note: lens() needs a Compose CornerBasedShape (RoundedCornerShape / CircleShape).
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: CornerBasedShape,
    tint: Color = Color.Unspecified,
    blurRadius: Dp = 3.dp,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 32.dp,
    frost: Float? = null,
): Modifier {
    val cfg = LocalGlass.current
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            val b = blurRadius.toPx() * cfg.blur
            if (b > 0.5f) blur(b)
            lens(refractionHeight.toPx(), refractionAmount.toPx() * cfg.refraction)
        },
        onDrawSurface = {
            val f = frost ?: cfg.frost
            if (f > 0f) drawRect(Color.White.copy(alpha = f.coerceIn(0f, 1f)))
            if (tint.isSpecified) drawRect(tint)
            if (cfg.rim) {
                val outline = shape.createOutline(size, layoutDirection, this)
                // soft gloss on the upper half
                drawOutline(
                    outline,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.55f,
                    ),
                )
                // bright specular rim, strongest at top-left and bottom-right
                drawOutline(
                    outline,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.45f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
        },
    )
}

/** Small glass pill button with optional icon. */
@Composable
fun GlassChip(text: String, icon: ImageVector?, backdrop: Backdrop, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .liquidGlass(backdrop, RoundedCornerShape(50), refractionHeight = 10.dp, refractionAmount = 20.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = LabelStyle.copy(fontSize = 14.sp))
    }
}
