package com.example.glasslauncher.ui

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.glasslauncher.LauncherViewModel
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.WidgetController
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.services.NotificationDots
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun LauncherApp(
    homePressed: Flow<Unit>,
    widgets: WidgetController,
    vm: LauncherViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val apps by vm.apps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.userData.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val rawDots by NotificationDots.packages.collectAsStateWithLifecycle()
    val dots = if (settings.notificationDots) rawDots else emptySet()

    LaunchedEffect(widgets) { widgets.onWidgetAdded = { vm.addWidget(it) } }

    // ------------------------------------------------ Drawer physics (0 = closed, 1 = open)
    val drawer = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(2400f) }
    val drawerSpring = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 420f) }
    val drawerShown by remember { derivedStateOf { drawer.value > 0.001f } }
    var drawerWarm by remember { mutableStateOf(false) }
    var drawerFocusSearch by remember { mutableStateOf(false) }

    fun dragDrawer(dy: Float) {
        drawerWarm = true
        scope.launch { drawer.snapTo((drawer.value - dy / heightPx).coerceIn(0f, 1f)) }
    }

    fun settleDrawer(vy: Float) {
        val target = when {
            vy < -500f -> 1f
            vy > 500f -> 0f
            drawer.value > 0.45f -> 1f
            else -> 0f
        }
        if (target == 0f) drawerFocusSearch = false
        scope.launch { drawer.animateTo(target, drawerSpring, initialVelocity = -vy / heightPx) }
    }

    fun openDrawer(search: Boolean) {
        drawerWarm = true
        drawerFocusSearch = search
        scope.launch { drawer.animateTo(1f, drawerSpring) }
    }

    fun closeDrawer() {
        drawerFocusSearch = false
        scope.launch { drawer.animateTo(0f, drawerSpring) }
    }

    // ------------------------------------------------ Overlays
    var settingsOpen by remember { mutableStateOf(false) }
    var homeMenuOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<UiApp?>(null) }
    var editApp by remember { mutableStateOf<UiApp?>(null) }
    var homeEvent by remember { mutableIntStateOf(0) }
    var lastIconLongPress by remember { mutableLongStateOf(0L) }

    // Lowest priority: a launcher never closes on Back.
    BackHandler(enabled = true) {}
    BackHandler(enabled = drawerShown || settingsOpen || homeMenuOpen || widgetPickerOpen || menuApp != null || editApp != null) {
        when {
            editApp != null -> editApp = null
            menuApp != null -> menuApp = null
            widgetPickerOpen -> widgetPickerOpen = false
            homeMenuOpen -> homeMenuOpen = false
            settingsOpen -> settingsOpen = false
            drawerShown -> closeDrawer()
        }
    }

    LaunchedEffect(homePressed) {
        homePressed.collect {
            val anythingOpen = drawerShown || settingsOpen || homeMenuOpen || widgetPickerOpen ||
                menuApp != null || editApp != null
            settingsOpen = false; homeMenuOpen = false; widgetPickerOpen = false
            menuApp = null; editApp = null
            if (drawerShown) closeDrawer()
            if (!anythingOpen) homeEvent++
        }
    }

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }
    val launchPicker: () -> Unit = {
        pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ------------------------------------------------ Lists
    val homeApps = remember(apps, data.home, data.hidden) {
        data.home.filter { it !in data.hidden }.mapNotNull { apps[it] }
    }
    val dockApps = remember(apps, data.dock, data.hidden, settings.dockCount) {
        data.dock.filter { it !in data.hidden }.mapNotNull { apps[it] }.take(settings.dockCount)
    }
    val drawerApps = remember(apps, data.hidden) {
        apps.values.filter { it.key !in data.hidden }.sortedBy { it.label.lowercase() }
    }
    val suggestions = remember(drawerApps, data.launches, settings.drawerColumns) {
        drawerApps
            .filter { (data.launches[it.key] ?: 0) > 0 }
            .sortedByDescending { data.launches[it.key] ?: 0 }
            .take(settings.drawerColumns.coerceIn(3, 9))
    }
    val hiddenApps = remember(apps, data.hidden) { apps.values.filter { it.key in data.hidden } }

    // ------------------------------------------------ Actions
    val launchApp: (UiApp, Rect?) -> Unit = { ui, rect ->
        // Zoom the app out of its icon
        val options = rect?.let {
            ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
        }
        vm.launch(ui.app, rect, options)
        if (drawerShown) {
            scope.launch {
                delay(450)
                drawerFocusSearch = false
                drawer.snapTo(0f)
            }
        }
    }
    val onAppLongPress: (UiApp) -> Unit = {
        lastIconLongPress = SystemClock.uptimeMillis()
        homeMenuOpen = false
        menuApp = it
    }

    val glass = GlassConfig(settings.refraction, settings.blur, settings.frost, settings.rimLight)

    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Color.Transparent)) {
        CompositionLocalProvider(LocalGlass provides glass) {
            // Everything drawn into this backdrop is what the glass refracts.
            val backdrop = rememberLayerBackdrop()

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val h = constraints.maxHeight.toFloat()
                SideEffect { if (h > 0f) heightPx = h }

                LauncherBackground(wallpaper, Modifier.fillMaxSize().layerBackdrop(backdrop))

                // Home fades and sinks back a little as the drawer comes up (draw-phase only: no recomposition)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = drawer.value.coerceIn(0f, 1f)
                            alpha = 1f - p
                            val sc = 1f - 0.06f * p
                            scaleX = sc
                            scaleY = sc
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                ) {
                    HomeScreen(
                        settings = settings,
                        backdrop = backdrop,
                        homeApps = homeApps,
                        dockApps = dockApps,
                        dots = dots,
                        widgetIds = data.widgets,
                        widgets = widgets,
                        homeEvent = homeEvent,
                        onDrawerDrag = ::dragDrawer,
                        onDrawerSettle = ::settleDrawer,
                        onLaunch = launchApp,
                        onAppLongPress = onAppLongPress,
                        onOpenSearch = { openDrawer(search = true) },
                        onHomeLongPress = {
                            if (SystemClock.uptimeMillis() - lastIconLongPress > 600 && menuApp == null) homeMenuOpen = true
                        },
                        onAddWidget = { widgetPickerOpen = true },
                        onRemoveWidget = { id ->
                            widgets.remove(id)
                            vm.removeWidget(id)
                        },
                    )
                }

                // Drawer follows the finger: its position is the spring value, applied in the draw phase.
                if (drawerShown || drawerWarm) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = drawer.value.coerceIn(0f, 1f)
                                translationY = (1f - p) * size.height
                                alpha = if (p < 0.001f) 0f else 1f
                            }
                    ) {
                        AppDrawer(
                            settings = settings,
                            backdrop = backdrop,
                            apps = drawerApps,
                            suggestions = suggestions,
                            dots = dots,
                            open = drawerShown,
                            focusSearch = drawerFocusSearch,
                            progress = { drawer.value },
                            onDrag = ::dragDrawer,
                            onSettle = ::settleDrawer,
                            onLaunch = launchApp,
                            onLongPress = onAppLongPress,
                            onOpenSettings = { settingsOpen = true },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = settingsOpen,
                    enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 480f)) { it },
                    exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 650f)) { it },
                ) {
                    SettingsScreen(
                        settings = settings,
                        backdrop = backdrop,
                        hiddenApps = hiddenApps,
                        hasWallpaper = wallpaper != null,
                        onUpdate = vm::updateSettings,
                        onUnhide = vm::unhide,
                        onResetColors = vm::resetAllColors,
                        onResetSettings = vm::resetSettings,
                        onPickWallpaper = launchPicker,
                        onClearWallpaper = vm::clearWallpaper,
                        onClose = { settingsOpen = false },
                    )
                }

                GlassMenu(
                    visible = homeMenuOpen,
                    backdrop = backdrop,
                    title = t("Home screen", "Ana ekran"),
                    items = buildList {
                        add(MenuAction(Icons.Default.Star, t("Change wallpaper", "Duvar kâğıdını değiştir"), launchPicker))
                        if (wallpaper != null) {
                            add(MenuAction(Icons.Default.Refresh, t("Default background", "Varsayılan arka plan")) { vm.clearWallpaper() })
                        }
                        add(MenuAction(Icons.Default.Add, t("Add widget", "Widget ekle")) { widgetPickerOpen = true })
                        add(MenuAction(Icons.Default.Settings, t("Launcher settings", "Başlatıcı ayarları")) { settingsOpen = true })
                    },
                    onDismiss = { homeMenuOpen = false },
                )

                AppMenu(
                    app = menuApp,
                    data = data,
                    backdrop = backdrop,
                    loadShortcuts = vm::shortcutsFor,
                    onShortcut = { vm.launchShortcut(it) },
                    onToggleHome = { vm.toggleHome(it) },
                    onMoveHome = { app, delta -> vm.moveOnHome(app, delta) },
                    onToggleDock = {
                        if (!vm.toggleDock(it)) SystemActions.toast(context, t("The dock is full", "Dock dolu"))
                    },
                    onEdit = { editApp = it },
                    onHide = { vm.hide(it) },
                    onInfo = { vm.openAppInfo(it) },
                    onUninstall = { vm.uninstall(it) },
                    onDismiss = { menuApp = null },
                )

                EditAppDialog(
                    app = editApp,
                    data = data,
                    settings = settings,
                    backdrop = backdrop,
                    preview = vm::previewIcon,
                    onSave = { app, name, color -> vm.customize(app, name, color) },
                    onDismiss = { editApp = null },
                )

                WidgetPicker(
                    visible = widgetPickerOpen,
                    controller = widgets,
                    backdrop = backdrop,
                    onPick = { widgets.add(it) },
                    onDismiss = { widgetPickerOpen = false },
                )
            }
        }
    }
}
