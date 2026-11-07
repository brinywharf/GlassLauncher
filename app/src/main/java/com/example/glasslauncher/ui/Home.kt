package com.example.glasslauncher.ui

import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.glasslauncher.data.GestureAction
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.PageEffect
import com.example.glasslauncher.data.SearchBarPos
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.data.Weather
import com.example.glasslauncher.data.weatherEmoji
import com.example.glasslauncher.data.weatherText
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
    onGesture: (HomeGesture) -> Unit,
    onLaunch: (UiApp, Rect?) -> Unit,
    onAppLongPress: (UiApp) -> Unit,
    onOpenSearch: () -> Unit,
    onHomeLongPress: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    weather: Weather? = null,
    weatherNeedsPermission: Boolean = false,
    onWeatherClick: () -> Unit = {},
) {
    val s = settings
    val prefs = LocalUiPrefs.current
    val perPage = max(1, s.homeColumns * s.homeRows)
    val appPages = max(1, (homeApps.size + perPage - 1) / perPage)
    val pageCount by rememberUpdatedState(appPages + 1) // page 0 = widgets
    val pagerState = rememberPagerState(initialPage = 1) { pageCount }

    LaunchedEffect(homeEvent) {
        if (homeEvent > 0) pagerState.animateScrollToPage(1, animationSpec = fluidSpring(prefs, 1f, 500f))
    }

    // One vertical gesture. Swipe up for the drawer follows the finger; other actions fire on release.
    var mode by remember { mutableIntStateOf(0) } // 0 undecided, 1 up, 2 down
    var total by remember { mutableFloatStateOf(0f) }
    val followDrawer = s.swipeUp == GestureAction.APP_DRAWER
    val dragState = rememberDraggableState { delta ->
        if (mode == 0) mode = if (delta < 0f) 1 else 2
        total += delta
        if (mode == 1 && followDrawer) onDrawerDrag(delta)
    }
    val onDoubleTap: ((Offset) -> Unit)? =
        if (s.doubleTap != GestureAction.NONE) ({ _: Offset -> onGesture(HomeGesture.DOUBLE_TAP) }) else null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(s.doubleTap) {
                detectTapGestures(onDoubleTap = onDoubleTap, onLongPress = { onHomeLongPress() })
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    mode = 0
                    total = 0f
                },
                onDragStopped = { velocity ->
                    when {
                        mode == 1 && followDrawer -> onDrawerSettle(velocity)
                        mode == 1 && (total < -100f || velocity < -900f) -> onGesture(HomeGesture.SWIPE_UP)
                        mode == 2 && (total > 90f || velocity > 900f) -> onGesture(HomeGesture.SWIPE_DOWN)
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
                GlanceCard(
                    backdrop, s.clock24h,
                    weather = if (s.showWeather) weather else null,
                    askWeather = s.showWeather && weatherNeedsPermission,
                    onWeatherClick = onWeatherClick,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            if (s.searchBarPos == SearchBarPos.TOP) SearchBar(backdrop, onOpenSearch)

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { applyPageEffect(s.pageEffect, pagerState.getOffsetDistanceInPages(page)) }
                ) {
                    if (page == 0) {
                        WidgetPage(widgetIds, widgets, backdrop, onAddWidget, onRemoveWidget)
                    } else {
                        val slice = homeApps.drop((page - 1) * perPage).take(perPage)
                        AppGridPage(slice, s, dots, onLaunch, onAppLongPress)
                    }
                }
            }

            if (s.showPageDots) PageDots(pagerState.pageCount, pagerState.currentPage)
            Spacer(Modifier.height(10.dp))

            if (s.showDock) Dock(dockApps, s, dots, backdrop, onLaunch, onAppLongPress)

            if (s.searchBarPos == SearchBarPos.BOTTOM) SearchBar(backdrop, onOpenSearch) else Spacer(Modifier.height(12.dp))
        }
    }
}

enum class HomeGesture { SWIPE_UP, SWIPE_DOWN, DOUBLE_TAP }

/**
 * Nova-style page transitions. d = offset of this page from the centre in pages
 * (positive = page sits to the right). Everything runs in the draw phase.
 */
private fun GraphicsLayerScope.applyPageEffect(effect: PageEffect, d: Float) {
    val p = d.coerceIn(-1f, 1f)
    val a = abs(p)
    when (effect) {
        PageEffect.SLIDE -> Unit
        PageEffect.CUBE -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (p < 0f) 1f else 0f, 0.5f)
            rotationY = 75f * p
        }
        PageEffect.STACK -> if (p > 0f) {
            // the incoming page waits underneath and grows into place
            translationX = -p * size.width
            val sc = 1f - 0.12f * a
            scaleX = sc; scaleY = sc
            alpha = 1f - a
        }
        PageEffect.ZOOM -> {
            val sc = 1f - 0.18f * a
            scaleX = sc; scaleY = sc
            alpha = 1f - 0.6f * a
        }
        PageEffect.ROTATE -> {
            transformOrigin = TransformOrigin(0.5f, 1.15f)
            rotationZ = 12f * p
        }
        PageEffect.FADE -> {
            translationX = -p * size.width
            alpha = 1f - a
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
                                labelSize = s.labelSize,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceCard(
    backdrop: Backdrop,
    clock24h: Boolean,
    weather: Weather?,
    askWeather: Boolean,
    onWeatherClick: () -> Unit,
    modifier: Modifier,
) {
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
    val small = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))

    Row(
        modifier
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(34.dp), blurRadius = 4.dp, refractionHeight = 26.dp, refractionAmount = 60.dp)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(interactionSource = null, indication = null) { SystemActions.openClock(context) },
        ) {
            Text(now.format(timeFormat), style = LabelStyle.copy(fontSize = 56.sp, fontWeight = FontWeight.Light))
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = LabelStyle.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f)),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(if (battery.charging) "⚡ " else "🔋 ")
                    append("${battery.percent}%")
                    if (alarm != null) append("   ⏰ $alarm")
                },
                style = small,
            )
        }
        if (weather != null || askWeather) {
            Column(
                Modifier.clickable(interactionSource = null, indication = null, onClick = onWeatherClick),
                horizontalAlignment = Alignment.End,
            ) {
                if (weather != null) {
                    Text(
                        "${weatherEmoji(weather.code, weather.isDay)} ${weather.temp.roundToInt()}°",
                        style = LabelStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Light),
                    )
                    val (en, tr) = weatherText(weather.code)
                    Text(weather.city ?: t(en, tr), style = small, maxLines = 1)
                    Text("↑${weather.high.roundToInt()}°  ↓${weather.low.roundToInt()}°", style = small)
                } else {
                    Text("📍", style = LabelStyle.copy(fontSize = 26.sp))
                    Text(t("Tap for weather", "Hava durumu için dokun"), style = small)
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val selected = i == current
            val w by animateDpAsState(if (selected) 18.dp else 6.dp, fluidSpring(LocalUiPrefs.current, 0.7f, 900f), label = "dot")
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
            .then(
                if (s.dockBackground) Modifier.liquidGlass(backdrop, RoundedCornerShape(36.dp), blurRadius = 5.dp, refractionHeight = 24.dp, refractionAmount = 56.dp)
                else Modifier
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
            GlassChip(t("Add widget", "Widget ekle"), Icons.Default.Add, backdrop, onClick = onAdd)
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
