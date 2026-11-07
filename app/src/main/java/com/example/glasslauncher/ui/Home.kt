package com.example.glasslauncher.ui

import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.WidgetController
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.UiApp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun HomeScreen(
    settings: LauncherSettings,
    backdrop: Backdrop,
    homeApps: List<UiApp>,
    dockApps: List<UiApp>,
    dots: Set<String>,
    widgetIds: List<Int>,
    widgets: WidgetController,
    homeEvent: Int,
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    onLaunch: (UiApp, Rect?) -> Unit,
    onAppLongPress: (UiApp) -> Unit,
    onOpenSearch: () -> Unit,
    onHomeLongPress: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
) {
    val s = settings
    val context = LocalContext.current
    val perPage = max(1, s.homeColumns * s.homeRows)
    val appPages = max(1, (homeApps.size + perPage - 1) / perPage)
    val pageCount by rememberUpdatedState(appPages + 1) // page 0 = widgets
    val pagerState = rememberPagerState(initialPage = 1) { pageCount }

    LaunchedEffect(homeEvent) {
        if (homeEvent > 0) pagerState.animateScrollToPage(1, animationSpec = spring(stiffness = 500f))
    }

    // One vertical gesture: up = drawer (follows the finger), down = notifications.
    var mode by remember { mutableIntStateOf(0) } // 0 undecided, 1 drawer, 2 notifications
    var notifDrag by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        if (mode == 0) mode = if (delta < 0f) 1 else 2
        if (mode == 1) onDrawerDrag(delta) else notifDrag += delta
    }
    val onDoubleTap: ((Offset) -> Unit)? =
        if (s.doubleTapLock) ({ _: Offset -> SystemActions.lockScreen(context) }) else null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(s.doubleTapLock) {
                detectTapGestures(onDoubleTap = onDoubleTap, onLongPress = { onHomeLongPress() })
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    mode = 0
                    notifDrag = 0f
                },
                onDragStopped = { velocity ->
                    if (mode == 1) onDrawerSettle(velocity)
                    else if (mode == 2 && notifDrag > 90f && s.swipeDownNotifications) {
                        SystemActions.expandNotifications(context)
                    }
                    mode = 0
                },
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (s.showGlance) {
                GlanceCard(backdrop, s.clock24h, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                if (page == 0) {
                    WidgetPage(widgetIds, widgets, backdrop, onAddWidget, onRemoveWidget)
                } else {
                    val slice = homeApps.drop((page - 1) * perPage).take(perPage)
                    AppGridPage(slice, s, dots, onLaunch, onAppLongPress)
                }
            }

            PageDots(pagerState.pageCount, pagerState.currentPage)
            Spacer(Modifier.height(10.dp))

            Dock(dockApps, s, dots, backdrop, onLaunch, onAppLongPress)

            if (s.showSearchBar) SearchBar(backdrop, onOpenSearch) else Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AppGridPage(
    apps: List<UiApp>,
    s: LauncherSettings,
    dots: Set<String>,
    onLaunch: (UiApp, Rect?) -> Unit,
    onLongPress: (UiApp) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        repeat(s.homeRows) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(s.homeColumns) { c ->
                    val app = apps.getOrNull(r * s.homeColumns + c)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (app != null) {
                            AppIcon(
                                app, s.iconSize.dp, s.showLabels,
                                dot = s.notificationDots && app.app.packageName in dots,
                                onClick = onLaunch, onLongClick = onLongPress,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceCard(backdrop: Backdrop, clock24h: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    // Only wakes up once a minute (the clock shows minutes), so it costs nothing in between.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val t = LocalDateTime.now()
            value = t
            delay(((60 - t.second) * 1000L - t.nano / 1_000_000).coerceAtLeast(200L))
        }
    }
    val battery = remember(now) { SystemActions.battery(context) }
    val timeFormat = remember(clock24h) { DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm") }
    val alarm = remember(now, clock24h) {
        SystemActions.nextAlarmMillis(context)?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).format(timeFormat)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(34.dp), blurRadius = 4.dp, refractionHeight = 26.dp, refractionAmount = 60.dp)
            .clickable { SystemActions.openClock(context) }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(now.format(timeFormat), style = LabelStyle.copy(fontSize = 58.sp, fontWeight = FontWeight.Light))
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f)),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (battery.charging) "⚡ " else "🔋 ") + "${battery.percent}%", style = LabelStyle.copy(fontSize = 15.sp))
            if (alarm != null) {
                Spacer(Modifier.height(4.dp))
                Text("⏰ $alarm", style = LabelStyle.copy(fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val selected = i == current
            val w by animateDpAsState(if (selected) 18.dp else 6.dp, spring(dampingRatio = 0.7f, stiffness = 900f), label = "dot")
            val col by animateColorAsState(Color.White.copy(alpha = if (selected) 0.95f else 0.4f), label = "dotc")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = w, height = 6.dp)
                    .background(col, CircleShape)
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<UiApp>,
    s: LauncherSettings,
    dots: Set<String>,
    backdrop: Backdrop,
    onLaunch: (UiApp, Rect?) -> Unit,
    onLongPress: (UiApp) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(36.dp), blurRadius = 5.dp, refractionHeight = 24.dp, refractionAmount = 56.dp)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            Text(
                t("Long-press an app → Add to dock", "Bir uygulamaya uzun bas → Dock'a ekle"),
                style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)),
                modifier = Modifier.padding(vertical = 18.dp),
            )
        }
        apps.forEach { app ->
            AppIcon(
                app, s.iconSize.dp, showLabel = false,
                dot = s.notificationDots && app.app.packageName in dots,
                onClick = onLaunch, onLongClick = onLongPress,
            )
        }
    }
}

@Composable
private fun SearchBar(backdrop: Backdrop, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            .liquidGlass(backdrop, RoundedCornerShape(50), refractionHeight = 16.dp, refractionAmount = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(
            t("Search apps and the web", "Uygulamalarda ve web'de ara"),
            style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f)),
        )
    }
}

// ---------------------------------------------------------------- Widgets page

@Composable
private fun WidgetPage(
    ids: List<Int>,
    controller: WidgetController,
    backdrop: Backdrop,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip(t("Add widget", "Widget ekle"), Icons.Default.Add, backdrop, onAdd)
            if (ids.isNotEmpty()) {
                GlassChip(if (editing) t("Done", "Bitti") else t("Edit", "Düzenle"), Icons.Default.Edit, backdrop) {
                    editing = !editing
                }
            }
        }
        if (ids.isEmpty()) {
            Text(
                t("Your widgets live here. Tap “Add widget” to place one.",
                    "Widget'ların burada durur. Eklemek için “Widget ekle”ye dokun."),
                style = LabelStyle.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f)),
            )
        }
        ids.forEach { id -> key(id) { WidgetCard(id, controller, backdrop, editing, onRemove) } }
    }
}

@Composable
private fun WidgetCard(id: Int, controller: WidgetController, backdrop: Backdrop, editing: Boolean, onRemove: (Int) -> Unit) {
    val info = remember(id) { controller.manager.getAppWidgetInfo(id) }
    if (info == null) {
        LaunchedEffect(id) { onRemove(id) }
        return
    }
    val height = with(LocalDensity.current) { info.minHeight.toDp() }.coerceAtLeast(110.dp) + 20.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .liquidGlass(backdrop, RoundedCornerShape(26.dp), refractionHeight = 18.dp, refractionAmount = 40.dp)
            .padding(10.dp)
    ) {
        AndroidView(
            factory = { ctx -> controller.host.createView(ctx, id, info) },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .background(Color(0xE6FF4D5E), CircleShape)
                    .clickable { onRemove(id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
