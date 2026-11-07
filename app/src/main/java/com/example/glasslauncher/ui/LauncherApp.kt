package com.example.glasslauncher.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.glasslauncher.LauncherViewModel
import com.example.glasslauncher.SystemActions
import com.example.glasslauncher.WidgetController
import com.example.glasslauncher.data.AppInfo
import com.example.glasslauncher.services.NotificationDots
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.Flow

@Composable
fun LauncherApp(
    homePressed: Flow<Unit>,
    widgets: WidgetController,
    vm: LauncherViewModel = viewModel(),
) {
    val context = LocalContext.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.userData.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val dots by NotificationDots.packages.collectAsStateWithLifecycle()

    LaunchedEffect(widgets) { widgets.onWidgetAdded = { vm.addWidget(it) } }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerFocusSearch by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var homeMenuOpen by remember { mutableStateOf(false) }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var editApp by remember { mutableStateOf<AppInfo?>(null) }
    var homeEvent by remember { mutableIntStateOf(0) }
    var lastIconLongPress by remember { mutableLongStateOf(0L) }

    // Lowest priority: a launcher never closes on Back.
    BackHandler(enabled = true) {}

    fun closeTop(): Boolean {
        when {
            editApp != null -> editApp = null
            menuApp != null -> menuApp = null
            widgetPickerOpen -> widgetPickerOpen = false
            homeMenuOpen -> homeMenuOpen = false
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
            else -> return false
        }
        return true
    }

    LaunchedEffect(homePressed) {
        homePressed.collect {
            val anythingOpen = drawerOpen || settingsOpen || homeMenuOpen || widgetPickerOpen ||
                menuApp != null || editApp != null
            drawerOpen = false; settingsOpen = false; homeMenuOpen = false
            widgetPickerOpen = false; menuApp = null; editApp = null
            if (!anythingOpen) homeEvent++
        }
    }
    BackHandler(
        enabled = drawerOpen || settingsOpen || homeMenuOpen || widgetPickerOpen || menuApp != null || editApp != null
    ) { closeTop() }

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }
    val launchPicker = {
        pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val homeApps = remember(byKey, data.home, data.hidden) {
        data.home.filter { it !in data.hidden }.mapNotNull { byKey[it] }
    }
    val dockApps = remember(byKey, data.dock, data.hidden, settings.dockCount) {
        data.dock.filter { it !in data.hidden }.mapNotNull { byKey[it] }.take(settings.dockCount)
    }
    val drawerApps = remember(apps, data.hidden, data.renames) {
        apps.filter { it.key !in data.hidden }.sortedBy { (data.renames[it.key] ?: it.label).lowercase() }
    }
    val suggestions = remember(drawerApps, data.launches, settings.drawerColumns) {
        drawerApps
            .filter { (data.launches[it.key] ?: 0) > 0 }
            .sortedByDescending { data.launches[it.key] ?: 0 }
            .take(settings.drawerColumns.coerceIn(3, 6))
    }
    val hiddenApps = remember(apps, data.hidden) { apps.filter { it.key in data.hidden } }

    val glass = GlassConfig(settings.refraction, settings.blur, settings.frost, settings.rimLight)

    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Color.Transparent)) {
        CompositionLocalProvider(LocalGlass provides glass) {
            // Everything drawn into this backdrop is what the glass refracts.
            val backdrop = rememberLayerBackdrop()
            val ic = IconContext(settings, data, if (settings.notificationDots) dots else emptySet(), backdrop)

            val onAppLongPress: (AppInfo) -> Unit = {
                lastIconLongPress = SystemClock.uptimeMillis()
                homeMenuOpen = false
                menuApp = it
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                LauncherBackground(wallpaper, Modifier.fillMaxSize().layerBackdrop(backdrop))

                HomeScreen(
                    ic = ic,
                    homeApps = homeApps,
                    dockApps = dockApps,
                    widgetIds = data.widgets,
                    widgets = widgets,
                    homeEvent = homeEvent,
                    onLaunch = vm::launch,
                    onAppLongPress = onAppLongPress,
                    onOpenDrawer = { search ->
                        drawerFocusSearch = search
                        drawerOpen = true
                    },
                    onHomeLongPress = {
                        if (SystemClock.uptimeMillis() - lastIconLongPress > 600 && menuApp == null) homeMenuOpen = true
                    },
                    onAddWidget = { widgetPickerOpen = true },
                    onRemoveWidget = { id ->
                        widgets.remove(id)
                        vm.removeWidget(id)
                    },
                )

                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 380f)) { it / 3 } + fadeIn(),
                    exit = slideOutVertically { it / 3 } + fadeOut(),
                ) {
                    AppDrawer(
                        ic = ic,
                        apps = drawerApps,
                        suggestions = suggestions,
                        focusSearch = drawerFocusSearch,
                        onLaunch = {
                            drawerOpen = false
                            vm.launch(it)
                        },
                        onLongPress = onAppLongPress,
                        onClose = { drawerOpen = false },
                        onOpenSettings = { settingsOpen = true },
                    )
                }

                AnimatedVisibility(
                    visible = settingsOpen,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut(),
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
                            add(MenuAction(Icons.Default.Refresh, t("Animated background", "Hareketli arka plan")) { vm.clearWallpaper() })
                        }
                        add(MenuAction(Icons.Default.Add, t("Add widget", "Widget ekle")) { widgetPickerOpen = true })
                        add(MenuAction(Icons.Default.Settings, t("Launcher settings", "Başlatıcı ayarları")) { settingsOpen = true })
                    },
                    onDismiss = { homeMenuOpen = false },
                )

                AppMenu(
                    app = menuApp,
                    ic = ic,
                    loadShortcuts = vm::shortcutsFor,
                    onShortcut = {
                        drawerOpen = false
                        vm.launchShortcut(it)
                    },
                    onToggleHome = { vm.toggleHome(it) },
                    onMoveHome = { app, delta -> vm.moveOnHome(app, delta) },
                    onToggleDock = {
                        if (!vm.toggleDock(it)) {
                            SystemActions.toast(context, t("The dock is full", "Dock dolu"))
                        }
                    },
                    onEdit = { editApp = it },
                    onHide = { vm.hide(it) },
                    onInfo = { vm.openAppInfo(it) },
                    onUninstall = { vm.uninstall(it) },
                    onDismiss = { menuApp = null },
                )

                EditAppDialog(
                    app = editApp,
                    ic = ic,
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
