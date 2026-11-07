package com.example.glasslauncher.ui

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.SystemActions
import android.content.ClipData
import android.content.ClipboardManager
import com.example.glasslauncher.data.Calc
import com.example.glasslauncher.data.DrawerSort
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.UiApp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

private fun letterOf(label: String): String {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

/** Rows rise and fade in one after another while the drawer opens. Pure draw-phase work. */
private fun Modifier.stagger(row: Int, progress: () -> Float): Modifier = graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    if (p >= 1f) return@graphicsLayer
    val delay = row.coerceIn(0, 6) * 0.06f
    val local = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
    val eased = 1f - (1f - local) * (1f - local)
    alpha = eased
    translationY = (1f - eased) * 56.dp.toPx()
    val sc = 0.9f + 0.1f * eased
    scaleX = sc
    scaleY = sc
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The app drawer. Its position is driven from outside (progress 0..1) so it follows the
 * finger exactly; onDrag/onSettle feed gestures back. Pulling down at the top of the list
 * closes it, like on Pixel.
 */
@Composable
fun AppDrawer(
    settings: LauncherSettings,
    backdrop: Backdrop,
    frost: ImageBitmap?,
    apps: List<UiApp>,
    suggestions: List<UiApp>,
    dots: Set<String>,
    open: Boolean,
    focusSearch: Boolean,
    progress: () -> Float,
    onDrag: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    onLaunch: (UiApp, Rect?) -> Unit,
    onLongPress: (UiApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Reset when closed; focus the search box when opened from the search bar.
    LaunchedEffect(open, focusSearch) {
        if (open && focusSearch) runCatching { focus.requestFocus() }
        if (!open) {
            keyboard?.hide()
            query = ""
            gridState.scrollToItem(0)
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps
            .filter { it.label.contains(q, ignoreCase = true) || it.app.packageName.contains(q, ignoreCase = true) }
            .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
    }
    val letters = remember(filtered) { filtered.map { letterOf(it.label) }.distinct() }
    val calc = remember(query) { Calc.evaluate(query) }

    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnSettle by rememberUpdatedState(onSettle)
    val currentProgress by rememberUpdatedState(progress)

    // Pull-to-close from the top of the list, and smooth hand-off of flings.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While partly closed, pushing up re-opens before the list scrolls.
                if (available.y < 0f && currentProgress() < 1f) {
                    currentOnDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    currentOnDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentProgress() < 1f) {
                    currentOnSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val headerDrag = rememberDraggableState { currentOnDrag(it) }

    fun launchApp(app: UiApp, rect: Rect?) {
        keyboard?.hide()
        onLaunch(app, rect)
    }

    Box(Modifier.fillMaxSize()) {
    // Frosted background: static, blurred once, just fades in. Costs nothing per frame.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = currentProgress().coerceIn(0f, 1f) }
            .frosted(frost, dim = settings.drawerDim)
    )
    // Content follows the finger 1:1
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - currentProgress().coerceIn(0f, 1f)) * size.height }
            .statusBarsPadding()
            .padding(top = 8.dp)
            .sheetPanel()
            .nestedScroll(connection)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header: drag here to move the drawer
        Column(
            Modifier.draggable(
                state = headerDrag,
                orientation = Orientation.Vertical,
                onDragStopped = { v -> currentOnSettle(v) },
            )
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .background(Color.White.copy(alpha = 0.55f), CircleShape)
            )
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .frostedControl(RoundedCornerShape(50))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                t("Search apps", "Uygulama ara"),
                                style = PlainStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f)),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = PlainStyle.copy(fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                val first = filtered.firstOrNull()
                                if (first != null) launchApp(first, null)
                                else if (query.isNotBlank()) SystemActions.webSearch(context, query)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, null, tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { query = "" },
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(50.dp)
                        .frostedControl(CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            keyboard?.hide()
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Settings, t("Settings", "Ayarlar"), tint = Color.White)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            val cols = settings.drawerColumns.coerceIn(3, 9)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                contentPadding = PaddingValues(start = 8.dp, end = 30.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val showSuggestions = query.isBlank() && settings.showSuggestions && suggestions.isNotEmpty()
                if (showSuggestions) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "sugg-title", contentType = "title") {
                        Text(
                            t("Suggested", "Önerilen"),
                            style = PlainStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f)),
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    items(suggestions, key = { "s-" + it.key }, contentType = { "app" }) { app ->
                        Box(Modifier.stagger(0, currentProgress), contentAlignment = Alignment.Center) {
                            AppIcon(
                                app, settings.iconSize.dp, settings.drawerLabels,
                                dot = settings.notificationDots && app.app.packageName in dots,
                                onClick = ::launchApp, onLongClick = onLongPress, labelShadow = false, labelSize = settings.labelSize,
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "sugg-line", contentType = "line") {
                        Box(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                    }
                }
                if (calc != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "calc", contentType = "calc") {
                        Row(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .frostedControl(RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(ClipData.newPlainText("result", calc))
                                    SystemActions.toast(context, t("Copied", "Kopyalandı"))
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("= ", style = PlainStyle.copy(fontSize = 26.sp, color = Color.White.copy(alpha = 0.6f)))
                            Text(calc, style = PlainStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
                            Text(t("tap to copy", "kopyalamak için dokun"), style = PlainStyle.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f)))
                        }
                    }
                }
                itemsIndexed(filtered, key = { _, it -> it.key }, contentType = { _, _ -> "app" }) { index, app ->
                    Box(Modifier.stagger(index / cols + if (showSuggestions) 1 else 0, currentProgress), contentAlignment = Alignment.Center) {
                        AppIcon(
                            app, settings.iconSize.dp, settings.drawerLabels,
                            dot = settings.notificationDots && app.app.packageName in dots,
                            onClick = ::launchApp, onLongClick = onLongPress, labelShadow = false, labelSize = settings.labelSize,
                        )
                    }
                }
                if (query.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "web", contentType = "web") {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            GlassChip(t("Search the web for “$query”", "Web'de ara: “$query”"), Icons.Default.Search, backdrop, live = false) {
                                SystemActions.webSearch(context, query)
                            }
                        }
                    }
                }
            }

            if (settings.drawerFastScroll && settings.drawerSort == DrawerSort.ALPHABETICAL && query.isBlank() && letters.size > 1) {
                val offset = if (settings.showSuggestions && suggestions.isNotEmpty()) suggestions.size + 2 else 0
                AlphabetScroller(
                    letters = letters,
                    onLetter = { letter ->
                        val index = filtered.indexOfFirst { letterOf(it.label) == letter }
                        if (index >= 0) scope.launch { gridState.scrollToItem(index + offset) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
    }
}

/** Letter strip on the right edge: slide your finger to jump through the list. */
@Composable
private fun AlphabetScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier) {
    var active by remember { mutableStateOf<String?>(null) }
    val currentOnLetter by rememberUpdatedState(onLetter)
    Box(
        modifier
            .fillMaxHeight()
            .width(28.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(letters) {
                    awaitEachGesture {
                        fun pick(y: Float) {
                            val i = ((y / size.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            if (letters[i] != active) {
                                active = letters[i]
                                currentOnLetter(letters[i])
                            }
                        }
                        val down = awaitFirstDown()
                        pick(down.position.y)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            pick(change.position.y)
                            change.consume()
                        }
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach {
                Text(
                    it,
                    style = PlainStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (it == active) Color.White else Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
        }
        val current = active
        if (current != null) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = (-72).dp)
                    .size(60.dp)
                    .background(Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(current, style = PlainStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
