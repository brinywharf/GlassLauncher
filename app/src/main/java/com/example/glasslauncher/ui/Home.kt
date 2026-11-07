package com.example.glasslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import com.example.glasslauncher.data.AppInfo
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun HomeScreen(
    ic: IconContext,
    homeApps: List<AppInfo>,
    dockApps: List<AppInfo>,
    widgetIds: List<Int>,
    widgets: WidgetController,
    homeEvent: Int,
    onLaunch: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onHomeLongPress: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
) {
    val s = ic.settings
    val context = LocalContext.current
    val perPage = max(1, s.homeColumns * s.homeRows)
    val appPages = max(1, (homeApps.size + perPage - 1) / perPage)
    val pageCount by rememberUpdatedState(appPages + 1) // page 0 = widgets
    val pagerState = rememberPagerState(initialPage = 1) { pageCount }

    LaunchedEffect(homeEvent) {
        if (homeEvent > 0) pagerState.animateScrollToPage(1)
    }

    var drag by remember { mutableFloatStateOf(0f) }
    val onDoubleTap: ((Offset) -> Unit)? =
        if (s.doubleTapLock) ({ _: Offset -> SystemActions.lockScreen(context) }) else null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(s.doubleTapLock) {
                detectTapGestures(onDoubleTap = onDoubleTap, onLongPress = { onHomeLongPress() })
            }
            .pointerInput(s.swipeDownNotifications) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag += dy },
                    onDragEnd = {
                        if (drag < -100f) onOpenDrawer(false)
                        else if (drag > 140f && s.swipeDownNotifications) SystemActions.expandNotifications(context)
                    },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (s.showGlance) {
                GlanceCard(ic.backdrop, s.clock24h, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                if (page == 0) {
                    WidgetPage(widgetIds, widgets, ic.backdrop, onAddWidget, onRemoveWidget)
                } else {
                    val slice = homeApps.drop((page - 1) * perPage).take(perPage)
                    AppGridPage(slice, ic, s.homeColumns, s.homeRows, onLaunch, onAppLongPress)
                }
            }

            PageDots(pagerState.pageCount, pagerState.currentPage)
            Spacer(Modifier.height(10.dp))

            Dock(dockApps, ic, onLaunch, onAppLongPress)

            if (s.showSearchBar) {
                SearchBar(ic.backdrop) { onOpenDrawer(true) }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AppGridPage(
    apps: List<AppInfo>,
    ic: IconContext,
    cols: Int,
    rows: Int,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        repeat(rows) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(cols) { c ->
                    val app = apps.getOrNull(r * cols + c)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (app != null) {
                            AppIcon(
                                app, ic,
                                showLabel = ic.settings.showLabels,
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
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
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    val battery by produceState(SystemActions.battery(context)) {
        while (true) {
            value = SystemActions.battery(context)
            delay(30_000)
        }
    }
    val timeFormat = remember(clock24h) { DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm") }
    val alarm = remember(now.minute, clock24h) {
        SystemActions.nextAlarmMillis(context)?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).format(timeFormat)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(
                backdrop, RoundedCornerShape(34.dp),
                blurRadius = 4.dp, refractionHeight = 26.dp, refractionAmount = 60.dp,
            )
            .clickable { SystemActions.openClock(context) }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                now.format(timeFormat),
                style = LabelStyle.copy(fontSize = 58.sp, fontWeight = FontWeight.Light),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f)),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (battery.charging) "⚡ " else "🔋 ") + "${battery.percent}%",
                style = LabelStyle.copy(fontSize = 15.sp),
            )
            if (alarm != null) {
                Spacer(Modifier.height(4.dp))
                Text("⏰ $alarm", style = LabelStyle.copy(fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f), CircleShape)
            )
        }
    }
}

@Composable
private fun Dock(
    apps: List<AppInfo>,
    ic: IconContext,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .liquidGlass(
                ic.backdrop, RoundedCornerShape(36.dp),
                blurRadius = 5.dp, refractionHeight = 24.dp, refractionAmount = 56.dp,
            )
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
            AppIcon(app, ic, showLabel = false, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
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
                GlassChip(
                    if (editing) t("Done", "Bitti") else t("Edit", "Düzenle"),
                    Icons.Default.Edit, backdrop,
                ) { editing = !editing }
            }
        }
        if (ids.isEmpty()) {
            Text(
                t("Your widgets live here. Tap “Add widget” to place one.",
                    "Widget'ların burada durur. Eklemek için “Widget ekle”ye dokun."),
                style = LabelStyle.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f)),
            )
        }
        ids.forEach { id ->
            key(id) { WidgetCard(id, controller, backdrop, editing, onRemove) }
        }
    }
}

@Composable
private fun WidgetCard(
    id: Int,
    controller: WidgetController,
    backdrop: Backdrop,
    editing: Boolean,
    onRemove: (Int) -> Unit,
) {
    val info = remember(id) { controller.manager.getAppWidgetInfo(id) }
    if (info == null) {
        LaunchedEffect(id) { onRemove(id) }
        return
    }
    val density = LocalDensity.current
    val height = with(density) { info.minHeight.toDp() }.coerceAtLeast(110.dp) + 20.dp

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
