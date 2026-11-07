package com.example.glasslauncher.ui

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.UiApp

private class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun rect(): Rect? = coords?.takeIf { it.isAttached }?.boundsInWindow()?.let {
        Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
}

/**
 * One app icon: a pre-rendered glass bitmap (nearly free to draw).
 * Tap = open, long-press = menu, long-press + move = drag (when [dragSource] is set).
 */
@Composable
fun AppIcon(
    ui: UiApp,
    size: Dp,
    showLabel: Boolean,
    dot: Boolean,
    onClick: (UiApp, Rect?) -> Unit,
    onLongClick: (UiApp) -> Unit,
    labelShadow: Boolean = true,
    labelSize: Int = 12,
    dragSource: DragSource? = null,
    highlighted: Boolean = false,
) {
    val prefs = LocalUiPrefs.current
    val drag = LocalDrag.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            highlighted -> 1.14f
            pressed -> 0.86f
            else -> 1f
        },
        animationSpec = fluidSpring(prefs, dampingRatio = 0.5f, stiffness = 1400f),
        label = "press",
    )
    val haptic = LocalHapticFeedback.current
    val holder = remember { CoordsHolder() }

    Column(
        Modifier
            .width(size + 26.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .launcherGestures(
                onPress = { pressed = it },
                onClick = { onClick(ui, holder.rect()) },
                onLongClick = {
                    if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(ui)
                },
                onDragStart = if (dragSource != null && drag != null) {
                    { pos -> drag.start(AppEntry(ui), dragSource, pos) }
                } else null,
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
                contentDescription = null,
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
                style = (if (labelShadow) LabelStyle else PlainStyle).copy(fontSize = labelSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A folder on the home screen: a frosted tile with up to four tiny app icons. */
@Composable
fun FolderIcon(
    folder: FolderEntry,
    size: Dp,
    showLabel: Boolean,
    labelSize: Int,
    onOpen: (FolderEntry) -> Unit,
    onLongClick: (FolderEntry) -> Unit,
    draggable: Boolean = true,
    highlighted: Boolean = false,
) {
    val prefs = LocalUiPrefs.current
    val drag = LocalDrag.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            highlighted -> 1.14f
            pressed -> 0.88f
            else -> 1f
        },
        animationSpec = fluidSpring(prefs, dampingRatio = 0.5f, stiffness = 1400f),
        label = "fpress",
    )
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier
            .width(size + 26.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .launcherGestures(
                onPress = { pressed = it },
                onClick = { onOpen(folder) },
                onLongClick = {
                    if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(folder)
                },
                onDragStart = if (draggable && drag != null) {
                    { pos -> drag.start(folder, DragSource.Home, pos) }
                } else null,
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
            FolderTile(folder, size)
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                folder.name,
                style = LabelStyle.copy(fontSize = labelSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The folder's look, also used for the floating icon while dragging. */
@Composable
fun FolderTile(folder: FolderEntry, size: Dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.14f))),
                shape,
            )
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.45f))),
                shape,
            )
            .padding(size * 0.12f),
    ) {
        val mini = size * 0.36f
        val apps = folder.apps.take(4)
        apps.forEachIndexed { i, app ->
            Image(
                app.icon,
                contentDescription = null,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .align(
                        when (i) {
                            0 -> Alignment.TopStart
                            1 -> Alignment.TopEnd
                            2 -> Alignment.BottomStart
                            else -> Alignment.BottomEnd
                        }
                    )
                    .size(mini),
            )
        }
    }
}
