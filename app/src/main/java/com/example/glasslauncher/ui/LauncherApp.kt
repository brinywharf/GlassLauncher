package com.example.glasslauncher.ui

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Rect
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.glasslauncher.LauncherViewModel
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.WidgetController
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.DrawerSort
import com.example.glasslauncher.data.DropTarget
import com.example.glasslauncher.data.isFolderId
import com.example.glasslauncher.data.GestureAction
import com.example.glasslauncher.data.OpenAnim
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.services.NotificationDots
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
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

    LaunchedEffect(settings.hideStatusBar, settings.orientation, settings.maxRefreshRate) {
        (context as? Activity)?.let {
            SystemActions.applyWindowPrefs(it, settings.hideStatusBar, settings.orientation, settings.maxRefreshRate)
        }
    }

    // ------------------------------------------------ Drawer physics (0 = closed, 1 = open)
    val drawer = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(2400f) }
    val uiPrefs = UiPrefs(settings.animSpeed.factor, settings.haptics)
    val drawerSpring = fluidSpring<Float>(uiPrefs, dampingRatio = 0.9f, stiffness = 520f)
    val drawerShown by remember { derivedStateOf { drawer.value > 0.001f } }
    var drawerWarm by remember { mutableStateOf(false) }
    var drawerFocusSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1200)
        drawerWarm = true
    }

    fun dragDrawer(dy: Float) {
        drawerWarm = true
        val before = drawer.value
        val after = (before - dy / heightPx).coerceIn(0f, 1f)
        if (settings.haptics && (before < 0.45f) != (after < 0.45f)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        scope.launch { drawer.snapTo(after) }
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

    // ------------------------------------------------ Return-to-home reveal
    val reveal = remember { Animatable(1f) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!drawerShown) {
            scope.launch {
                reveal.snapTo(0.94f)
                reveal.animateTo(1f, fluidSpring(uiPrefs, dampingRatio = 0.72f, stiffness = 320f))
            }
        }
    }

    // ------------------------------------------------ Overlays
    var settingsOpen by remember { mutableStateOf(false) }
    var homeMenuOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<UiApp?>(null) }
    var editApp by remember { mutableStateOf<UiApp?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var folderHiddenForDrag by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<FolderEntry?>(null) }
    var homeEvent by remember { mutableIntStateOf(0) }
    var lastIconLongPress by remember { mutableLongStateOf(0L) }

    // Lowest priority: a launcher never closes on Back.
    BackHandler(enabled = true) {}
    BackHandler(
        enabled = drawerShown || settingsOpen || homeMenuOpen || widgetPickerOpen || menuApp != null ||
            editApp != null || openFolderId != null || folderMenu != null
    ) {
        when {
            editApp != null -> editApp = null
            menuApp != null -> menuApp = null
            folderMenu != null -> folderMenu = null
            openFolderId != null -> openFolderId = null
            widgetPickerOpen -> widgetPickerOpen = false
            homeMenuOpen -> homeMenuOpen = false
            settingsOpen -> settingsOpen = false
            drawerShown -> closeDrawer()
        }
    }

    LaunchedEffect(homePressed) {
        homePressed.collect {
            val anythingOpen = drawerShown || settingsOpen || homeMenuOpen || widgetPickerOpen ||
                menuApp != null || editApp != null || openFolderId != null || folderMenu != null
            settingsOpen = false; homeMenuOpen = false; widgetPickerOpen = false
            menuApp = null; editApp = null; openFolderId = null; folderMenu = null
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
    val homeEntries = remember(apps, data.home, data.folders, data.hidden) {
        data.home.mapNotNull { id ->
            if (isFolderId(id)) {
                data.folders[id]?.let { f ->
                    val inside = f.apps.filter { it !in data.hidden }.mapNotNull { apps[it] }
                    if (inside.isEmpty()) null else FolderEntry(id, f.name, inside)
                }
            } else if (id in data.hidden) null else apps[id]?.let { AppEntry(it) }
        }
    }
    val openFolder = remember(homeEntries, openFolderId) {
        openFolderId?.let { id -> homeEntries.firstOrNull { it.id == id } as? FolderEntry }
    }
    val dockApps = remember(apps, data.dock, data.hidden, settings.dockCount) {
        data.dock.filter { it !in data.hidden }.mapNotNull { apps[it] }.take(settings.dockCount)
    }
    val sortedApps = remember(apps, data.hidden) {
        apps.values.filter { it.key !in data.hidden }.sortedBy { it.label.lowercase() }
    }
    val drawerApps = remember(sortedApps, settings.drawerSort, if (settings.drawerSort == DrawerSort.MOST_USED) data.launches else null) {
        when (settings.drawerSort) {
            DrawerSort.ALPHABETICAL -> sortedApps
            DrawerSort.MOST_USED -> sortedApps.sortedByDescending { data.launches[it.key] ?: 0 }
            DrawerSort.NEWEST -> sortedApps.sortedByDescending { it.app.installTime }
        }
    }
    val suggestions = remember(sortedApps, data.launches, settings.drawerColumns) {
        sortedApps
            .filter { (data.launches[it.key] ?: 0) > 0 }
            .sortedByDescending { data.launches[it.key] ?: 0 }
            .take(settings.drawerColumns.coerceIn(3, 9))
    }
    val hiddenApps = remember(apps, data.hidden) { apps.values.filter { it.key in data.hidden } }

    // ------------------------------------------------ Actions
    val launchApp: (UiApp, Rect?) -> Unit = { ui, rect ->
        // Zoom the app out of its icon
        val options = rect?.let {
            when (settings.openAnim) {
                OpenAnim.ZOOM -> ActivityOptions.makeScaleUpAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                OpenAnim.REVEAL -> ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
                OpenAnim.SYSTEM -> null
            }
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
    fun runAction(action: GestureAction, appKey: String) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> openDrawer(search = false)
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(context)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(context)
            GestureAction.LOCK_SCREEN -> SystemActions.lockScreen(context)
            GestureAction.SEARCH -> openDrawer(search = true)
            GestureAction.LAUNCHER_SETTINGS -> settingsOpen = true
            GestureAction.OPEN_APP -> apps[appKey]?.let { vm.launch(it.app, null, null) }
        }
    }
    val onGesture: (HomeGesture) -> Unit = { g ->
        when (g) {
            HomeGesture.SWIPE_UP -> runAction(settings.swipeUp, settings.swipeUpApp)
            HomeGesture.SWIPE_DOWN -> runAction(settings.swipeDown, settings.swipeDownApp)
            HomeGesture.DOUBLE_TAP -> runAction(settings.doubleTap, settings.doubleTapApp)
        }
    }

    // Backup & restore files
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    // ------------------------------------------------ Drag & drop
    val drag = remember { DragController(scope) }
    SideEffect {
        drag.onStart = { d ->
            menuApp = null
            homeMenuOpen = false
            folderMenu = null
            if (d.source is DragSource.Folder) folderHiddenForDrag = true
            if (d.source is DragSource.Drawer) closeDrawer()
        }
        drag.onTargetChange = { t ->
            if (settings.haptics && (t is DropTarget.Merge || t is DropTarget.Remove || t is DropTarget.Uninstall)) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        drag.onDrop = { d, t ->
            val entry = d.entry
            if (t is DropTarget.Uninstall && entry is AppEntry) vm.uninstall(entry.app.app)
            else if (vm.drop(d.id, d.source, t) && settings.haptics) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            if (d.source is DragSource.Folder) {
                folderHiddenForDrag = false
                openFolderId = null
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
        CompositionLocalProvider(LocalGlass provides glass, LocalUiPrefs provides uiPrefs, LocalDrag provides drag) {
            // Everything drawn into this backdrop is what the glass refracts.
            val backdrop = rememberLayerBackdrop()

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val h = constraints.maxHeight.toFloat()
                SideEffect { if (h > 0f) heightPx = h }
                val wPx = constraints.maxWidth
                val hPx = constraints.maxHeight

                val aurora by produceState<ImageBitmap?>(null, wPx / 3, hPx / 3) {
                    value = withContext(Dispatchers.Default) { Frost.aurora(wPx / 3, hPx / 3).asImageBitmap() }
                }
                val bg = wallpaper ?: aurora
                val frost by produceState<ImageBitmap?>(null, bg, wPx, hPx) {
                    val b = bg
                    value = if (b == null) null else withContext(Dispatchers.Default) {
                        runCatching { Frost.make(b.asAndroidBitmap(), wPx, hPx).asImageBitmap() }.getOrNull()
                    }
                }

                LauncherBackground(bg, Modifier.fillMaxSize().layerBackdrop(backdrop))

                // Home fades and sinks back a little as the drawer comes up (draw-phase only: no recomposition)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = drawer.value.coerceIn(0f, 1f)
                            alpha = 1f - p
                            translationY = -p * 80.dp.toPx()
                            val r = reveal.value
                            val sc = (1f - 0.06f * p) * r
                            scaleX = sc
                            scaleY = sc
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                ) {
                    HomeScreen(
                        settings = settings,
                        backdrop = backdrop,
                        homeEntries = homeEntries,
                        dockApps = dockApps,
                        dots = dots,
                        widgetIds = data.widgets,
                        widgets = widgets,
                        homeEvent = homeEvent,
                        onDrawerDrag = ::dragDrawer,
                        onDrawerSettle = ::settleDrawer,
                        onGesture = onGesture,
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
                        onOpenFolder = { openFolderId = it.id },
                        onFolderLongPress = {
                            lastIconLongPress = SystemClock.uptimeMillis()
                            homeMenuOpen = false
                            folderMenu = it
                        },
                    )
                }

                // Drawer follows the finger: its position is the spring value, applied in the draw phase.
                if (drawerShown || drawerWarm) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (drawer.value < 0.001f) 0f else 1f }
                    ) {
                        AppDrawer(
                            settings = settings,
                            backdrop = backdrop,
                            frost = frost,
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
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .animateEnterExit(enter = fadeIn(tween(220)), exit = fadeOut(tween(200)))
                            .frosted(frost, dim = 0.40f)
                    )
                    SettingsScreen(
                        modifier = Modifier.animateEnterExit(
                            enter = slideInVertically(fluidSpring(uiPrefs, 0.86f, 480f)) { it },
                            exit = slideOutVertically(fluidSpring(uiPrefs, 1f, 650f)) { it },
                        ),
                        settings = settings,
                        backdrop = backdrop,
                        apps = sortedApps,
                        hiddenApps = hiddenApps,
                        hasWallpaper = wallpaper != null,
                        onUpdate = vm::updateSettings,
                        onUnhide = vm::unhide,
                        onResetColors = vm::resetAllColors,
                        onResetSettings = vm::resetSettings,
                        onPickWallpaper = launchPicker,
                        onClearWallpaper = vm::clearWallpaper,
                        onExport = { exportLauncher.launch("glass-launcher-backup.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
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

                FolderOverlay(
                    folder = openFolder,
                    hiddenForDrag = folderHiddenForDrag,
                    settings = settings,
                    dots = dots,
                    backdrop = backdrop,
                    onLaunch = launchApp,
                    onAppLongPress = onAppLongPress,
                    onRename = { id, name -> vm.renameFolder(id, name) },
                    onDismiss = { openFolderId = null },
                )

                val fm = folderMenu
                GlassMenu(
                    visible = fm != null,
                    backdrop = backdrop,
                    title = fm?.name ?: "",
                    items = if (fm == null) emptyList() else listOf(
                        MenuAction(Icons.Default.Edit, t("Open & rename", "Aç ve yeniden adlandır")) { openFolderId = fm.id },
                        MenuAction(Icons.Default.Refresh, t("Ungroup", "Klasörü dağıt")) { vm.ungroupFolder(fm.id) },
                        MenuAction(Icons.Default.Close, t("Remove from home screen", "Ana ekrandan kaldır")) { vm.removeFromHome(fm.id) },
                    ),
                    onDismiss = { folderMenu = null },
                )

                DropBar(drag)
                DragGhost(drag, settings.iconSize.dp)
            }
        }
    }
}
