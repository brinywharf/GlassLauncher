package com.example.glasslauncher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap,
) {
    val key: String get() = "$packageName/$activityName"
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val wallpaperFile = File(app.filesDir, "wallpaper")

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _pinned = MutableStateFlow(loadPinned())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

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

    // ---------- Apps ----------

    fun refreshApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val iconPx = (56 * context.resources.displayMetrics.density).toInt()
                val user = android.os.Process.myUserHandle()
                launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != context.packageName }
                    .map { info ->
                        AppInfo(
                            label = info.label.toString(),
                            packageName = info.componentName.packageName,
                            activityName = info.componentName.className,
                            icon = info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = list
            // First run: pin the first four apps so the dock isn't empty.
            if (!prefs.contains(KEY_PINNED) && list.isNotEmpty()) {
                savePinned(list.take(4).map { it.key })
            }
        }
    }

    fun launch(app: AppInfo) {
        runCatching {
            launcherApps.startMainActivity(
                ComponentName(app.packageName, app.activityName),
                android.os.Process.myUserHandle(),
                null,
                null,
            )
        }
    }

    fun openAppInfo(app: AppInfo) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(app: AppInfo) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---------- Dock ----------

    fun togglePin(app: AppInfo) {
        val current = _pinned.value
        val next = if (app.key in current) current - app.key
        else (current + app.key).takeLast(MAX_DOCK)
        savePinned(next)
    }

    private fun loadPinned(): List<String> =
        prefs.getString(KEY_PINNED, null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    private fun savePinned(keys: List<String>) {
        prefs.edit { putString(KEY_PINNED, keys.joinToString(",")) }
        _pinned.value = keys
    }

    // ---------- Wallpaper ----------
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
            val source = ImageDecoder.createSource(wallpaperFile)
            val bitmap: Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Downsample big photos to roughly screen size (keeps memory sane).
                val scale = maxOf(
                    1,
                    minOf(info.size.width / dm.widthPixels, info.size.height / dm.heightPixels),
                )
                decoder.setTargetSampleSize(scale)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }

    companion object {
        private const val KEY_PINNED = "pinned"
        const val MAX_DOCK = 5
    }
}
