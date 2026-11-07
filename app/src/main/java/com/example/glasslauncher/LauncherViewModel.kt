package com.example.glasslauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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
import com.example.glasslauncher.data.IconFactory
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.SettingsStore
import com.example.glasslauncher.data.ShortcutItem
import com.example.glasslauncher.data.UserData
import com.example.glasslauncher.data.UserDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val settingsStore = SettingsStore(app)
    private val dataStore = UserDataStore(app)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val wallpaperFile = File(app.filesDir, "wallpaper")

    val settings: StateFlow<LauncherSettings> = settingsStore.state
    val userData: StateFlow<UserData> = dataStore.state

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    private var loadJob: Job? = null

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = refreshApps()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = refreshApps()
    }

    init {
        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        refreshApps()
        viewModelScope.launch { _wallpaper.value = decodeWallpaper() }
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(packageCallback)
    }

    // ---------------------------------------------------------------- Apps

    fun refreshApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { loadApps() }
            _apps.value = list
            if (!dataStore.state.value.initialized && list.isNotEmpty()) initDefaults(list)
        }
    }

    private fun loadApps(): List<AppInfo> {
        val px = (96 * context.resources.displayMetrics.density).toInt().coerceAtMost(256)
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    val drawable = info.getIcon(0)
                    val original = IconFactory.original(drawable, px)
                    AppInfo(
                        label = info.label.toString(),
                        packageName = info.componentName.packageName,
                        activityName = info.componentName.className,
                        icon = original.asImageBitmap(),
                        glyph = IconFactory.glyph(drawable, px).asImageBitmap(),
                        autoColor = IconFactory.autoColor(original),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
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

        val keyOf = { pkg: String -> list.firstOrNull { it.packageName == pkg }?.key }
        val dock = (rolePackages.mapNotNull(keyOf) + list.map { it.key })
            .distinct()
            .take(settings.value.dockCount)

        val favourites = listOf(
            "com.android.vending", "com.google.android.gm", "com.google.android.youtube",
            "com.google.android.apps.maps", "com.google.android.apps.photos", "com.google.android.calendar",
            "com.google.android.deskclock", "com.android.settings", "com.google.android.apps.docs",
            "com.whatsapp", "com.instagram.android", "org.telegram.messenger", "com.spotify.music",
        )
        var home = favourites.mapNotNull(keyOf).filter { it !in dock }
        if (home.size < 8) home = (home + list.map { it.key }.filter { it !in dock }).distinct().take(8)

        dataStore.update { it.copy(initialized = true, dock = dock, home = home.take(12)) }
    }

    fun launch(app: AppInfo) {
        runCatching {
            launcherApps.startMainActivity(
                ComponentName(app.packageName, app.activityName), Process.myUserHandle(), null, null
            )
        }
        dataStore.update { it.copy(launches = it.launches + (app.key to ((it.launches[app.key] ?: 0) + 1))) }
    }

    fun openAppInfo(app: AppInfo) = SystemActions.start(
        context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
    )

    fun uninstall(app: AppInfo) = SystemActions.start(
        context, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
    )

    // ---------------------------------------------------------------- Shortcuts

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
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
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
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
