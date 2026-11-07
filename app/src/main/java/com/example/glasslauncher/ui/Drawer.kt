package com.example.glasslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.data.AppInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun letterOf(label: String): String {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

@Composable
fun AppDrawer(
    ic: IconContext,
    apps: List<AppInfo>,
    suggestions: List<AppInfo>,
    focusSearch: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val backdrop = ic.backdrop
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            delay(150)
            runCatching { focus.requestFocus() }
        }
    }

    val filtered = remember(apps, query, ic.data.renames) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps
            .filter { ic.labelOf(it).contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedBy { !ic.labelOf(it).startsWith(q, ignoreCase = true) }
    }
    val letters = remember(filtered) { filtered.map { letterOf(ic.labelOf(it)) }.distinct() }

    fun launchAndClose(app: AppInfo) {
        keyboard?.hide()
        onLaunch(app)
    }

    var drag by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
            .liquidGlass(
                backdrop,
                RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp),
                tint = Color.Black.copy(alpha = 0.30f),
                blurRadius = 18.dp,
                refractionHeight = 30.dp,
                refractionAmount = 70.dp,
            )
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header: drag down here to close
        Column(
            Modifier.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag += dy },
                    onDragEnd = {
                        if (drag > 120f) {
                            keyboard?.hide()
                            onClose()
                        }
                    },
                )
            }
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .background(Color.White.copy(alpha = 0.55f), CircleShape)
            )

            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .liquidGlass(backdrop, RoundedCornerShape(50), refractionHeight = 12.dp, refractionAmount = 26.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                t("Search apps", "Uygulama ara"),
                                style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f)),
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = LabelStyle.copy(fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                val first = filtered.firstOrNull()
                                if (first != null) launchAndClose(first)
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
                        .liquidGlass(backdrop, CircleShape, refractionHeight = 12.dp, refractionAmount = 26.dp)
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

        if (query.isBlank() && ic.settings.showSuggestions && suggestions.isNotEmpty()) {
            Text(
                t("Suggested", "Önerilen"),
                style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                suggestions.forEach { app ->
                    AppIcon(app, ic, showLabel = true, onClick = { launchAndClose(app) }, onLongClick = { onLongPress(app) })
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(ic.settings.drawerColumns.coerceIn(3, 9)),
                state = gridState,
                contentPadding = PaddingValues(start = 8.dp, end = 30.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.key }) { app ->
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            app, ic,
                            showLabel = true,
                            onClick = { launchAndClose(app) },
                            onLongClick = { onLongPress(app) },
                        )
                    }
                }
                if (query.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            GlassChip(
                                t("Search the web for “$query”", "Web'de ara: “$query”"),
                                Icons.Default.Search, backdrop,
                            ) { SystemActions.webSearch(context, query) }
                        }
                    }
                }
            }

            if (query.isBlank() && letters.size > 1) {
                AlphabetScroller(
                    letters = letters,
                    onLetter = { letter ->
                        val index = filtered.indexOfFirst { letterOf(ic.labelOf(it)) == letter }
                        if (index >= 0) scope.launch { gridState.scrollToItem(index) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** Letter strip on the right edge: slide your finger to jump through the list. */
@Composable
private fun AlphabetScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier) {
    var active by remember { mutableStateOf<String?>(null) }
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
                                onLetter(letters[i])
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
                    style = LabelStyle.copy(
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
                Text(current, style = LabelStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
