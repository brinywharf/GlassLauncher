package com.example.glasslauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.DropTarget
import kotlin.math.roundToInt

/** "Remove" / "Uninstall" targets that slide in at the top while something is being dragged. */
@Composable
fun DropBar(drag: DragController) {
    val item = drag.item
    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(fluidSpring(LocalUiPrefs.current, 0.8f, 700f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            val src = item?.source
            if (src != null && src !is DragSource.Drawer) {
                DropPill(
                    "✕  " + t("Remove", "Kaldır"),
                    active = drag.target == DropTarget.Remove,
                    activeColor = Color(0xCCFF9F43),
                    modifier = Modifier.onGloballyPositioned { drag.removeRect = it.boundsInWindow() },
                )
            }
            if (item?.entry is AppEntry) {
                DropPill(
                    "🗑  " + t("Uninstall", "Sil"),
                    active = drag.target == DropTarget.Uninstall,
                    activeColor = Color(0xCCFF4D5E),
                    modifier = Modifier.onGloballyPositioned { drag.uninstallRect = it.boundsInWindow() },
                )
            }
        }
    }
}

@Composable
private fun DropPill(text: String, active: Boolean, activeColor: Color, modifier: Modifier) {
    val scale by animateFloatAsState(if (active) 1.08f else 1f, fluidSpring(LocalUiPrefs.current, 0.6f, 900f), label = "pill")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(46.dp)
            .clip(shape)
            .then(if (active) Modifier.background(activeColor, shape) else Modifier.frostedControl(shape))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PlainStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The icon under your finger while dragging. Follows the finger in the layout phase (no recomposition). */
@Composable
fun DragGhost(drag: DragController, size: Dp) {
    val item = drag.item ?: return
    val half = with(LocalDensity.current) { size.toPx() / 2f }
    val prefs = LocalUiPrefs.current
    val lift = remember(item.id) { Animatable(1f) }
    LaunchedEffect(item.id) { lift.animateTo(1.15f, fluidSpring(prefs, 0.55f, 700f)) }
    Box(
        Modifier
            .offset {
                IntOffset((drag.finger.x - half).roundToInt(), (drag.finger.y - half).roundToInt())
            }
            .size(size)
            .graphicsLayer {
                scaleX = lift.value
                scaleY = lift.value
                alpha = if (drag.target == DropTarget.Remove || drag.target == DropTarget.Uninstall) 0.6f else 1f
            }
    ) {
        when (val e = item.entry) {
            is AppEntry -> Image(e.app.icon, null, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize())
            is FolderEntry -> FolderTile(e, size)
            HoleEntry -> Unit
        }
    }
}
