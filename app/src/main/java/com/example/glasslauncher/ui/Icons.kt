package com.example.glasslauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.data.AppInfo
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.UserData
import com.kyant.backdrop.Backdrop

/** Everything an icon needs to draw itself. */
@Immutable
class IconContext(
    val settings: LauncherSettings,
    val data: UserData,
    val dots: Set<String>,
    val backdrop: Backdrop,
) {
    fun labelOf(app: AppInfo): String = data.renames[app.key] ?: app.label

    fun tintOf(app: AppInfo): Color? {
        data.colors[app.key]?.let { return Color(it) }
        return defaultTint(app, settings)
    }
}

fun defaultTint(app: AppInfo, settings: LauncherSettings): Color? = when (settings.iconColorMode) {
    IconColorMode.AUTO -> Color(app.autoColor)
    IconColorMode.SINGLE -> Color(settings.iconColor)
    IconColorMode.CLEAR -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    ic: IconContext,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    size: Dp = ic.settings.iconSize.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Squishy press, like pressing on a drop of liquid
    val scale by animateFloatAsState(
        if (pressed) 0.84f else 1f,
        spring(dampingRatio = 0.38f, stiffness = 650f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier
            .width(size + 26.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            GlassTile(
                app = app,
                tint = ic.tintOf(app),
                size = size,
                backdrop = ic.backdrop,
                glass = ic.settings.glassIcons,
                modifier = Modifier.fillMaxSize(),
            )
            if (ic.settings.notificationDots && app.packageName in ic.dots) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(size * 0.24f)
                        .background(Color(app.autoColor), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                ic.labelOf(app),
                style = LabelStyle.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The auto-generated liquid glass icon: a glass tile tinted with the app's color + its symbol. */
@Composable
fun GlassTile(
    app: AppInfo,
    tint: Color?,
    size: Dp,
    backdrop: Backdrop,
    glass: Boolean,
    modifier: Modifier = Modifier,
) {
    if (glass) {
        Box(
            modifier.liquidGlass(
                backdrop = backdrop,
                shape = RoundedCornerShape(size * 0.3f),
                tint = tint?.copy(alpha = 0.48f) ?: Color.Unspecified,
                blurRadius = 3.dp,
                refractionHeight = size * 0.2f,
                refractionAmount = size * 0.45f,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(app.glyph, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    } else {
        Image(app.icon, contentDescription = null, modifier = modifier)
    }
}
