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
import com.example.glasslauncher.data.DragSource
import com.example.glasslauncher.data.DropTarget
import com.example.glasslauncher.data.FOLDER_PREFIX
import com.example.glasslauncher.data.FolderData
import com.example.glasslauncher.data.isFolderId
import com.example.glasslauncher.data.IconBaker
import com.example.glasslauncher.data.IconColorMode
import com.example.glasslauncher.data.IconFactory
import com.example.glasslauncher.data.IconShape
import com.example.glasslauncher.data.LauncherSettings
import com.example.glasslauncher.data.SettingsStore
import com.example.glasslauncher.data.ShortcutItem
import com.example.glasslauncher.data.UiApp
import com.example.glasslauncher.data.UserData
import com.example.glasslauncher.data.UserDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private data class IconSpec(val glass: Boolean, val tint: Int?, val shape: IconShape)
private data class IconStyle(val glass: Boolean, val mode: IconColorMode, val color: Int, val shape: IconShape)

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
        settings.map { IconStyle(it.glassIcons, it.iconColorMode, it.iconColor, it.iconShape) }.distinctUntilChanged(),
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
            purgeMissing(all.keys)
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
            installTime = runCatching {
                context.packageManager.getPackageInfo(info.componentName.packageName, 0).firstInstallTime
            }.getOrDefault(0L),
        )
    }.getOrNull()

    private fun reloadPackage(pkg: String) {
        if (pkg == context.packageName) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.Default) {
                runCatching { launcherApps.getActivityList(pkg, user) }.getOrDefault(emptyList())
                    .mapNotNull { buildApp(it) }
            }
            val before = rawApps.value.keys
            rawApps.update { map -> map.filterValues { it.packageName != pkg } + updated.associateBy { it.key } }
            // Nova-style "add new apps to home screen"
            val fresh = updated.map { it.key }.filter { it !in before }
            if (fresh.isNotEmpty() && settings.value.addNewAppsToHome) {
                dataStore.update { d -> d.copy(home = d.home + fresh.filter { it !in d.home && it !in d.dock }) }
            }
        }
    }

    private fun removePackage(pkg: String) {
        rawApps.update { map -> map.filterValues { it.packageName != pkg } }
        purgeMissing(rawApps.value.keys)
    }

    /** Drops uninstalled apps from home, dock and folders so positions stay in sync with what's shown. */
    private fun purgeMissing(present: Set<String>) {
        if (present.isEmpty()) return
        dataStore.update { d ->
            val home = d.home.filter { isFolderId(it) || it in present }
            val dock = d.dock.filter { it in present }
            val folders = d.folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in present }) }
            if (home == d.home && dock == d.dock && folders == d.folders) d
            else cleanFolders(d.copy(home = home, dock = dock, folders = folders))
        }
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
                val spec = IconSpec(style.glass, tint, style.shape)
                val cached = iconCache[app.key]
                val icon = if (cached != null && cached.first == spec && cached.second === app) {
                    cached.third
                } else {
                    val bmp: Bitmap = if (style.glass) IconBaker.bake(app.glyph, tint, style.shape) else app.icon
                    bmp.asImageBitmap().also { iconCache[app.key] = Triple(spec, app, it) }
                }
                UiApp(app.key, app, renames[app.key] ?: app.label, icon)
            }
        }.awaitAll().associateBy { it.key }
    }

    /** Preview for the edit dialog. */
    suspend fun previewIcon(app: AppInfo, tint: Int?): ImageBitmap = withContext(Dispatchers.Default) {
        IconBaker.bake(app.glyph, tint, settings.value.iconShape).asImageBitmap()
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
        cleanFolders(
            it.copy(
                hidden = it.hidden + app.key,
                home = it.home - app.key,
                dock = it.dock - app.key,
                folders = it.folders.mapValues { (_, f) -> f.copy(apps = f.apps - app.key) },
            )
        )
    }

    // ---------------------------------------------------------------- Drag & drop, folders

    /** Applies a finished drag in one atomic update. Returns false if nothing changed. */
    fun drop(itemId: String, from: DragSource, to: DropTarget): Boolean {
        if (to is DropTarget.None || to is DropTarget.Uninstall) return false
        val before = dataStore.state.value
        dataStore.update { d ->
            val home = d.home.toMutableList()
            val dock = d.dock.toMutableList()
            val folders = d.folders.toMutableMap()
            val isFolder = isFolderId(itemId)

            // Reject impossible moves before touching anything
            if (to is DropTarget.Dock && (isFolder || (itemId !in dock && from !is DragSource.Dock && dock.size >= settings.value.dockCount))) {
                return@update d
            }
            if (to is DropTarget.Merge && (isFolder || to.targetId == itemId)) return@update d

            // 1) take it out of where it came from
            when (from) {
                DragSource.Home -> home.remove(itemId)
                DragSource.Dock -> dock.remove(itemId)
                is DragSource.Folder -> folders[from.id]?.let { folders[from.id] = it.copy(apps = it.apps - itemId) }
                DragSource.Drawer -> Unit
            }

            // 2) put it where it was dropped
            when (to) {
                is DropTarget.Home -> {
                    home.remove(itemId)
                    dock.remove(itemId)
                    home.add(to.index.coerceIn(0, home.size), itemId)
                }
                is DropTarget.Dock -> {
                    dock.remove(itemId)
                    home.remove(itemId)
                    dock.add(to.index.coerceIn(0, dock.size), itemId)
                }
                is DropTarget.Merge -> {
                    home.remove(itemId)
                    dock.remove(itemId)
                    val target = to.targetId
                    if (isFolderId(target)) {
                        folders[target]?.let { if (itemId !in it.apps) folders[target] = it.copy(apps = it.apps + itemId) }
                    } else {
                        val at = home.indexOf(target)
                        if (at >= 0) {
                            val id = FOLDER_PREFIX + System.currentTimeMillis().toString(36)
                            folders[id] = FolderData(defaultFolderName(), listOf(target, itemId))
                            home[at] = id
                        }
                    }
                }
                DropTarget.Remove -> Unit
                else -> Unit
            }
            cleanFolders(d.copy(home = home, dock = dock, folders = folders))
        }
        return dataStore.state.value != before
    }

    /** Folders with one app turn back into that app; empty folders disappear. */
    private fun cleanFolders(d: UserData): UserData {
        val home = d.home.toMutableList()
        val folders = d.folders.toMutableMap()
        for ((id, f) in d.folders) {
            when {
                f.apps.isEmpty() -> {
                    home.remove(id)
                    folders.remove(id)
                }
                f.apps.size == 1 -> {
                    val i = home.indexOf(id)
                    val only = f.apps.first()
                    if (i >= 0) {
                        if (only in home) home.removeAt(i) else home[i] = only
                    }
                    folders.remove(id)
                }
                id !in home -> folders.remove(id)
            }
        }
        return d.copy(home = home, folders = folders)
    }

    private fun defaultFolderName(): String =
        if (java.util.Locale.getDefault().language == "tr") "Klasör" else "Folder"

    fun renameFolder(id: String, name: String) = dataStore.update { d ->
        val f = d.folders[id] ?: return@update d
        d.copy(folders = d.folders + (id to f.copy(name = name.trim().ifBlank { defaultFolderName() })))
    }

    /** Puts the folder's apps back on the home screen where the folder was. */
    fun ungroupFolder(id: String) = dataStore.update { d ->
        val f = d.folders[id] ?: return@update d
        val home = d.home.toMutableList()
        val at = home.indexOf(id)
        if (at < 0) return@update d
        home.removeAt(at)
        home.addAll(at, f.apps.filter { it !in home })
        d.copy(home = home, folders = d.folders - id)
    }

    fun removeFromHome(id: String) = dataStore.update { d ->
        d.copy(home = d.home - id, folders = if (isFolderId(id)) d.folders - id else d.folders)
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

    // ---------------------------------------------------------------- Backup & restore

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = JSONObject()
                        .put("app", "GlassLauncher")
                        .put("version", 1)
                        .put("settings", settingsStore.exportJson())
                        .put("userdata", dataStore.exportJson())
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
                    true
                }.getOrDefault(false)
            }
            SystemActions.toast(context, if (ok) "✓ Backup saved" else "Backup failed")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { JSONObject(it.readBytes().decodeToString()) }
                }.getOrNull()
            }
            if (json == null || json.optString("app") != "GlassLauncher") {
                SystemActions.toast(context, "Not a Glass Launcher backup")
                return@launch
            }
            json.optJSONObject("settings")?.let { settingsStore.importJson(it) }
            json.optJSONObject("userdata")?.let { dataStore.importJson(it) }
            SystemActions.toast(context, "✓ Backup restored")
        }
    }
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
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}
