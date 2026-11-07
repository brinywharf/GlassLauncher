package com.example.glasslauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.DropTarget
import com.example.glasslauncher.data.UiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

// ================================================================ What sits on the home screen

sealed interface HomeEntry {
    val id: String
}

data class AppEntry(val app: UiApp) : HomeEntry {
    override val id: String get() = app.key
}

data class FolderEntry(override val id: String, val name: String, val apps: List<UiApp>) : HomeEntry

/** The gap that opens up where a dragged icon will land. */
data object HoleEntry : HomeEntry {
    override val id: String = "__hole__"
}

data class DragItem(val entry: HomeEntry, val source: DragSource) {
    val id: String get() = entry.id
}

// ================================================================ Drag controller

/**
 * Tracks one drag: the item, the finger (window coordinates) and the current drop target.
 * Screens report their geometry here; the target is worked out on every finger move,
 * and only changes in the *target* cause recomposition (the floating icon follows the finger
 * in the layout phase).
 */
@Stable
class DragController(private val scope: CoroutineScope) {

    var item by mutableStateOf<DragItem?>(null)
        private set
    var finger by mutableStateOf(Offset.Zero)
        private set
    var target by mutableStateOf<DropTarget>(DropTarget.None)
        private set
    /** Page the home screen should scroll to (with a counter so repeats still trigger). */
    var pageRequest by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    val dragging: Boolean get() = item != null

    // ---- geometry, reported by the screens (plain fields: no recomposition)
    var gridRect = Rect.Zero
    var dockRect = Rect.Zero
    var removeRect = Rect.Zero
    var uninstallRect = Rect.Zero
    var cols = 4
    var rows = 5
    var currentPage = 1
    var pageCount = 2
    var showDock = true
    var dockCapacity = 5
    var edgePx = 60f
    var homeBase: List<HomeEntry> = emptyList()
    var dockBase: List<UiApp> = emptyList()

    // ---- hooks set by the root
    var onStart: (DragItem) -> Unit = {}
    var onDrop: (DragItem, DropTarget) -> Unit = { _, _ -> }
    var onTargetChange: (DropTarget) -> Unit = {}

    private var mergeCandidate: String? = null
    private var mergeJob: Job? = null
    private var edgeDir = 0
    private var edgeJob: Job? = null
    private var pageNonce = 0

    fun start(entry: HomeEntry, source: DragSource, at: Offset) {
        removeRect = Rect.Zero
        uninstallRect = Rect.Zero
        val d = DragItem(entry, source)
        item = d
        finger = at
        target = DropTarget.None
        onStart(d)
    }

    fun move(at: Offset) {
        if (item == null) return
        finger = at
        update(at)
    }

    fun end() {
        val d = item ?: return
        val t = target
        mergeJob?.cancel(); edgeJob?.cancel()
        mergeCandidate = null; edgeDir = 0
        item = null
        target = DropTarget.None
        onDrop(d, t)
    }

    private fun setTarget(t: DropTarget) {
        if (t != target) {
            target = t
            onTargetChange(t)
        }
    }

    private fun update(p: Offset) {
        val d = item ?: return
        val isApp = d.entry is AppEntry

        when {
            removeRect.contains(p) -> { clearTimers(); setTarget(DropTarget.Remove); return }
            isApp && uninstallRect.contains(p) -> { clearTimers(); setTarget(DropTarget.Uninstall); return }
            showDock && dockRect.contains(p) -> {
                clearTimers()
                if (!isApp) { setTarget(DropTarget.None); return }
                val alreadyIn = d.source is DragSource.Dock
                if (!alreadyIn && dockBase.size >= dockCapacity) { setTarget(DropTarget.None); return }
                val slots = dockBase.size + 1
                val i = ((p.x - dockRect.left) / dockRect.width * slots).toInt().coerceIn(0, dockBase.size)
                setTarget(DropTarget.Dock(i))
                return
            }
            gridRect.contains(p) -> gridTarget(p, d, isApp)
            else -> { clearTimers(); setTarget(DropTarget.None) }
        }
    }

    private fun gridTarget(p: Offset, d: DragItem, isApp: Boolean) {
        // Page flipping near the edges
        val dir = when {
            p.x < gridRect.left + edgePx -> -1
            p.x > gridRect.right - edgePx -> 1
            else -> 0
        }
        if (dir != edgeDir) {
            edgeDir = dir
            edgeJob?.cancel()
            if (dir != 0) {
                edgeJob = scope.launch {
                    delay(450)
                    val next = (currentPage + dir).coerceIn(1, pageCount - 1)
                    if (next != currentPage) pageRequest = next to (++pageNonce)
                    edgeDir = 0
                }
            }
        }

        val cw = gridRect.width / cols
        val ch = gridRect.height / rows
        val c = ((p.x - gridRect.left) / cw).toInt().coerceIn(0, cols - 1)
        val r = ((p.y - gridRect.top) / ch).toInt().coerceIn(0, rows - 1)
        val perPage = cols * rows
        val g = (currentPage - 1) * perPage + r * cols + c

        // What is shown in that cell right now (the hole shifts things)
        val hole = (target as? DropTarget.Home)?.index
        val shown: HomeEntry? = when {
            hole == null -> homeBase.getOrNull(g)
            g == hole -> HoleEntry
            g < hole -> homeBase.getOrNull(g)
            else -> homeBase.getOrNull(g - 1)
        }
        val center = Offset(gridRect.left + (c + 0.5f) * cw, gridRect.top + (r + 0.5f) * ch)
        val nearCenter = (p - center).getDistance() < min(cw, ch) * 0.3f

        if (isApp && shown != null && shown !is HoleEntry && shown.id != d.id && nearCenter) {
            // Hovering right on another icon: after a short pause, offer to make a folder
            if (target == DropTarget.Merge(shown.id)) return
            if (mergeCandidate != shown.id) {
                mergeCandidate = shown.id
                mergeJob?.cancel()
                mergeJob = scope.launch {
                    delay(320)
                    if (item != null && mergeCandidate == shown.id) setTarget(DropTarget.Merge(shown.id))
                }
            }
            return
        }
        mergeCandidate = null
        mergeJob?.cancel()
        if (shown is HoleEntry) return
        setTarget(DropTarget.Home(g.coerceIn(0, homeBase.size)))
    }

    private fun clearTimers() {
        mergeCandidate = null
        mergeJob?.cancel()
        edgeJob?.cancel()
        edgeDir = 0
    }
}

val LocalDrag = staticCompositionLocalOf<DragController?> { null }

// ================================================================ Gestures

private class CoordsRef {
    var c: LayoutCoordinates? = null
}

/**
 * Tap, long-press and long-press-then-drag in one gesture (like Pixel):
 * long-press opens the menu; moving afterwards picks the icon up.
 * [onDragStart] gets the finger position in window coordinates.
 */
@Composable
fun Modifier.launcherGestures(
    onPress: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: ((Offset) -> Unit)?,
): Modifier {
    val drag = LocalDrag.current
    val ref = remember { CoordsRef() }
    val press by rememberUpdatedState(onPress)
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val start by rememberUpdatedState(onDragStart)

    return this
        .onPlaced { ref.c = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                press(true)
                val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    val up = waitForUpOrCancellation()
                    if (up != null) { up.consume(); 1 } else 2
                }
                when (result) {
                    1 -> { press(false); click() }
                    2 -> press(false)
                    else -> {
                        long()
                        var dragging = false
                        var total = Offset.Zero
                        fun window(local: Offset): Offset = ref.c?.takeIf { it.isAttached }?.localToWindow(local) ?: local
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    ch.consume()
                                    break
                                }
                                total += ch.positionChange()
                                val startCb = start
                                if (!dragging && startCb != null && total.getDistance() > viewConfiguration.touchSlop) {
                                    dragging = true
                                    press(false)
                                    startCb(window(ch.position))
                                }
                                if (dragging) drag?.move(window(ch.position))
                                ch.consume()
                            }
                        } finally {
                            // Always finish the drag, even if this icon leaves the screen mid-gesture
                            press(false)
                            if (dragging) drag?.end()
                        }
                    }
                }
            }
        }
}

// ================================================================ Layout with animated reflow

/** Smoothly slides an item to its new spot whenever the layout moves it (reflow while dragging). */
fun Modifier.animatePlacement(stiffness: Float = 900f): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val t = target ?: return@offset IntOffset.Zero
            val a = anim ?: Animatable(t, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != t) {
                scope.launch { a.animateTo(t, spring(dampingRatio = 0.8f, stiffness = stiffness)) }
            }
            a.value - t
        }
}

/**
 * Fixed grid where every child keeps its identity (keyed by id) while it changes cells,
 * so icons glide to their new spots instead of jumping.
 */
@Composable
fun <T : HomeEntry> SlotGrid(
    items: List<T>,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            items.forEach { item ->
                key(item.id) {
                    Box(Modifier.animatePlacement()) { content(item) }
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val cw = w / cols.coerceAtLeast(1)
        val ch = if (rows > 0 && h > 0) h / rows else cw
        val childConstraints = Constraints(maxWidth = cw, maxHeight = ch)
        val placeables = measurables.map { it.measure(childConstraints) }
        val height = if (h > 0) h else ch * ((placeables.size + cols - 1) / cols)
        layout(w, height) {
            placeables.forEachIndexed { i, p ->
                val c = i % cols
                val r = i / cols
                p.place(c * cw + (cw - p.width) / 2, r * ch + (ch - p.height) / 2)
            }
        }
    }
}
