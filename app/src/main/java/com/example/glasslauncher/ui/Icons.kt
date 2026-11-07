package com.example.glasslauncher.ui

import android.graphics.Rect
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.data.UiApp

private class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun rect(): Rect? = coords?.takeIf { it.isAttached }?.boundsInWindow()?.let {
        Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
}

/**
 * One app icon. It draws a pre-rendered glass bitmap (nearly free), so a full grid
 * scrolls and animates smoothly. The press "squish" runs entirely in the draw phase.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    ui: UiApp,
    size: Dp,
    showLabel: Boolean,
    dot: Boolean,
    onClick: (UiApp, Rect?) -> Unit,
    onLongClick: (UiApp) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 1400f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }

    Column(
        Modifier
            .width(size + 26.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onClick(ui, holder.rect()) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(ui)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .onPlaced { holder.coords = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Image(
                ui.icon,
                contentDescription = ui.label,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(size * 0.22f)
                        .background(Color(ui.app.autoColor), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                ui.label,
                style = LabelStyle.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
