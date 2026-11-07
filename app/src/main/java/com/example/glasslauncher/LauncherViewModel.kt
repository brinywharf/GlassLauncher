package com.example.glasslauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.glasslauncher.data.AppInfo
import com.example.glasslauncher.data.IconBaker
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.IconFactory
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.SettingsStore
import com.example.glasslauncher.data.ShortcutItem
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.data.UserData
import com.example.glasslauncher.data.UserDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class IconSpec(val glass: Boolean, val tint: Int?)
private data class IconStyle(val glass: Boolean, val mode: IconColorMode, val color: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val settingsStore = SettingsStore(app)
    private val dataStore = UserDataStore(app)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val wallpaperFile = File(app.filesDir, "wallpaper")

    val settings: StateFlow<LauncherSettings> = settingsStore.state
    val userData: StateFlow<UserData> = dataStore.state

    /** Raw apps, keyed by "package/activity". */
    private val rawApps = MutableStateFlow<Map<String, AppInfo>>(emptyMap())

    /** Baked icons, reused until the app or its color changes. */
    private val iconCache = ConcurrentHashMap<String, Triple<IconSpec, AppInfo, ImageBitmap>>()

    /** Ready-to-draw apps (label + baked glass icon). */
    val apps: StateFlow<Map<String, UiApp>> = combine(
        rawApps,
        settings.map { IconStyle(it.glassIcons, it.iconColorMode, it.iconColor) }.distinctUntilChanged(),
        userData.map { it.colors to it.renames }.distinctUntilChanged(),
    ) { raw, style, custom -> Triple(raw, style, custom) }
        .mapLatest { (raw, style, custom) -> buildUi(raw, style, custom.first, custom.second) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = removePackage(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = reloadPackage(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reloadPackage(packageName)
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = p.forEach { reloadPackage(it) }
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = p.forEach { removePackage(it) }
    }

    init {
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        viewModelScope.launch {
            val all = loadAll()
            rawApps.value = all
            if (!dataStore.state.value.initialized && all.isNotEmpty()) initDefaults(all.values.toList())
        }
        viewModelScope.launch { _wallpaper.value = decodeWallpaper() }
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(packageCallback)
    }

    // ---------------------------------------------------------------- Loading

    private suspend fun loadAll(): Map<String, AppInfo> = coroutineScope {
        launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info -> async(Dispatchers.Default) { buildApp(info) } }
            .awaitAll()
            .filterNotNull()
            .associateBy { it.key }
    }

    private fun buildApp(info: LauncherActivityInfo): AppInfo? = runCatching {
        val drawable = info.getIcon(0)
        val original = IconFactory.original(drawable)
        AppInfo(
            label = info.label.toString(),
            packageName = info.componentName.packageName,
            activityName = info.componentName.className,
            icon = original,
            glyph = IconFactory.glyph(drawable, original, info.label.toString()),
            autoColor = IconFactory.autoColor(original),
        )
    }.getOrNull()

    private fun reloadPackage(pkg: String) {
        if (pkg == context.packageName) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.Default) {
                runCatching { launcherApps.getActivityList(pkg, user) }.getOrDefault(emptyList())
                    .mapNotNull { buildApp(it) }
            }
            rawApps.update { map -> map.filterValues { it.packageName != pkg } + updated.associateBy { it.key } }
        }
    }

    private fun removePackage(pkg: String) {
        rawApps.update { map -> map.filterValues { it.packageName != pkg } }
    }

    private suspend fun buildUi(
        raw: Map<String, AppInfo>,
        style: IconStyle,
        colors: Map<String, Int>,
        renames: Map<String, String>,
    ): Map<String, UiApp> = coroutineScope {
        raw.values.map { app ->
            async(Dispatchers.Default) {
                val tint: Int? = colors[app.key] ?: when (style.mode) {
                    IconColorMode.AUTO -> app.autoColor
                    IconColorMode.SINGLE -> style.color
                    IconColorMode.CLEAR -> null
                }
                val spec = IconSpec(style.glass, tint)
                val cached = iconCache[app.key]
                val icon = if (cached != null && cached.first == spec && cached.second === app) {
                    cached.third
                } else {
                    val bmp: Bitmap = if (style.glass) IconBaker.bake(app.glyph, tint) else app.icon
                    bmp.asImageBitmap().also { iconCache[app.key] = Triple(spec, app, it) }
                }
                UiApp(app.key, app, renames[app.key] ?: app.label, icon)
            }
        }.awaitAll().associateBy { it.key }
    }

    /** Preview for the edit dialog. */
    suspend fun previewIcon(app: AppInfo, tint: Int?): ImageBitmap = withContext(Dispatchers.Default) {
        IconBaker.bake(app.glyph, tint).asImageBitmap()
    }

    /** First run: fill the dock and home screen with sensible apps. */
    private fun initDefaults(list: List<AppInfo>) {
        val pm = context.packageManager
        val roles = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL),
        )
        val rolePackages = roles.mapNotNull {
            runCatching {
                @Suppress("DEPRECATION")
                pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()
        }.filter { it != "android" }.distinct()

        val sorted = list.sortedBy { it.label.lowercase() }
        val keyOf = { pkg: String -> sorted.firstOrNull { it.packageName == pkg }?.key }
        val dock = (rolePackages.mapNotNull(keyOf) + sorted.map { it.key })
            .distinct()
            .take(settings.value.dockCount)

        val favourites = listOf(
            "com.android.vending", "com.google.android.gm", "com.google.android.youtube",
            "com.google.android.apps.maps", "com.google.android.apps.photos", "com.google.android.calendar",
            "com.google.android.deskclock", "com.android.settings", "com.google.android.apps.docs",
            "com.sec.android.app.myfiles", "com.samsung.android.calendar", "com.sec.android.gallery3d",
            "com.whatsapp", "com.instagram.android", "org.telegram.messenger", "com.spotify.music",
        )
        var home = favourites.mapNotNull(keyOf).filter { it !in dock }
        if (home.size < 8) home = (home + sorted.map { it.key }.filter { it !in dock }).distinct().take(8)

        dataStore.update { it.copy(initialized = true, dock = dock, home = home.take(12)) }
    }

    // ---------------------------------------------------------------- Launching

    fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle?) {
        runCatching {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.activityName), user, sourceBounds, options)
        }
        dataStore.update { it.copy(launches = it.launches + (app.key to ((it.launches[app.key] ?: 0) + 1))) }
    }

    fun openAppInfo(app: AppInfo) {
        SystemActions.start(
            context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
        )
    }

    fun uninstall(app: AppInfo) {
        SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null)))
    }

    suspend fun shortcutsFor(app: AppInfo): List<ShortcutItem> = withContext(Dispatchers.IO) {
        runCatching {
            if (!launcherApps.hasShortcutHostPermission()) return@runCatching emptyList<ShortcutItem>()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            val dm = context.resources.displayMetrics
            val px = (32 * dm.density).toInt()
            launcherApps.getShortcuts(query, user).orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(5)
                .map { s ->
                    ShortcutItem(
                        info = s,
                        label = (s.shortLabel ?: s.longLabel ?: "").toString(),
                        icon = runCatching {
                            launcherApps.getShortcutIconDrawable(s, dm.densityDpi)?.toBitmap(px, px)?.asImageBitmap()
                        }.getOrNull(),
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun launchShortcut(item: ShortcutItem) {
        runCatching { launcherApps.startShortcut(item.info, null, null) }
    }

    // ---------------------------------------------------------------- Arranging

    /** Returns false if the dock is already full. */
    fun toggleDock(app: AppInfo): Boolean {
        val d = dataStore.state.value
        if (app.key in d.dock) {
            dataStore.update { it.copy(dock = it.dock - app.key) }
            return true
        }
        if (d.dock.size >= settings.value.dockCount) return false
        dataStore.update { it.copy(dock = it.dock + app.key, home = it.home - app.key) }
        return true
    }

    fun toggleHome(app: AppInfo) = dataStore.update {
        if (app.key in it.home) it.copy(home = it.home - app.key) else it.copy(home = it.home + app.key)
    }

    fun moveOnHome(app: AppInfo, delta: Int) = dataStore.update {
        val i = it.home.indexOf(app.key)
        if (i < 0) return@update it
        val j = (i + delta).coerceIn(0, it.home.lastIndex)
        val list = it.home.toMutableList()
        list.add(j, list.removeAt(i))
        it.copy(home = list)
    }

    fun hide(app: AppInfo) = dataStore.update {
        it.copy(hidden = it.hidden + app.key, home = it.home - app.key, dock = it.dock - app.key)
    }

    fun unhide(key: String) = dataStore.update { it.copy(hidden = it.hidden - key) }

    fun customize(app: AppInfo, name: String?, color: Int?) = dataStore.update {
        val renames = if (name.isNullOrBlank() || name == app.label) it.renames - app.key else it.renames + (app.key to name)
        val colors = if (color == null) it.colors - app.key else it.colors + (app.key to color)
        it.copy(renames = renames, colors = colors)
    }

    fun resetAllColors() = dataStore.update { it.copy(colors = emptyMap()) }

    fun addWidget(id: Int) = dataStore.update { it.copy(widgets = it.widgets + id) }
    fun removeWidget(id: Int) = dataStore.update { it.copy(widgets = it.widgets - id) }

    fun updateSettings(block: (LauncherSettings) -> LauncherSettings) = settingsStore.update(block)
    fun resetSettings() = settingsStore.reset()

    // ---------------------------------------------------------------- Wallpaper
    // Since Android 13 a normal app can't read the system wallpaper, and the glass needs
    // real pixels to refract. So the launcher draws its own wallpaper, picked by the user.

    fun setWallpaper(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    wallpaperFile.outputStream().use { input.copyTo(it) }
                }
            }
            _wallpaper.value = decodeWallpaper()
        }
    }

    fun clearWallpaper() {
        wallpaperFile.delete()
        _wallpaper.value = null
    }

    private suspend fun decodeWallpaper(): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!wallpaperFile.exists()) return@withContext null
        runCatching {
            val dm = context.resources.displayMetrics
            val longSide = maxOf(dm.widthPixels, dm.heightPixels)
            val bitmap: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(wallpaperFile)) { decoder, info, _ ->
                val scale = maxOf(1, minOf(info.size.width, info.size.height) / longSide)
                decoder.setTargetSampleSize(scale)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            bitmap.prepareToDraw()
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
