package com.example.glasslauncher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IconColorMode { AUTO, SINGLE, CLEAR }

data class LauncherSettings(
    // Icons
    val glassIcons: Boolean = true,
    val iconColorMode: IconColorMode = IconColorMode.AUTO,
    val iconColor: Int = 0xFF5C8DFF.toInt(),
    val iconSize: Int = 58,
    val showLabels: Boolean = true,
    // Layout
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val drawerColumns: Int = 4,
    val dockCount: Int = 5,
    // Glass
    val refraction: Float = 1.6f,
    val blur: Float = 1.2f,
    val frost: Float = 0.06f,
    val rimLight: Boolean = true,
    // Home
    val showGlance: Boolean = true,
    val showSearchBar: Boolean = true,
    val showSuggestions: Boolean = true,
    val clock24h: Boolean = true,
    // Gestures & extras
    val swipeDownNotifications: Boolean = true,
    val doubleTapLock: Boolean = false,
    val notificationDots: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val defaults: LauncherSettings = run {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        if (tablet) LauncherSettings(homeColumns = 6, homeRows = 5, drawerColumns = 6, dockCount = 6, iconSize = 64)
        else LauncherSettings()
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherSettings> = _state.asStateFlow()

    fun update(block: (LauncherSettings) -> LauncherSettings) {
        val next = block(_state.value)
        save(next)
        _state.value = next
    }

    fun reset() {
        prefs.edit { clear() }
        _state.value = defaults
    }

    private fun load(): LauncherSettings {
        val d = defaults
        return LauncherSettings(
            glassIcons = prefs.getBoolean("glassIcons", d.glassIcons),
            iconColorMode = runCatching {
                IconColorMode.valueOf(prefs.getString("iconColorMode", null) ?: d.iconColorMode.name)
            }.getOrDefault(d.iconColorMode),
            iconColor = prefs.getInt("iconColor", d.iconColor),
            iconSize = prefs.getInt("iconSize", d.iconSize),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            homeColumns = prefs.getInt("homeColumns", d.homeColumns),
            homeRows = prefs.getInt("homeRows", d.homeRows),
            drawerColumns = prefs.getInt("drawerColumns", d.drawerColumns),
            dockCount = prefs.getInt("dockCount", d.dockCount),
            refraction = prefs.getFloat("refraction", d.refraction),
            blur = prefs.getFloat("blur", d.blur),
            frost = prefs.getFloat("frost", d.frost),
            rimLight = prefs.getBoolean("rimLight", d.rimLight),
            showGlance = prefs.getBoolean("showGlance", d.showGlance),
            showSearchBar = prefs.getBoolean("showSearchBar", d.showSearchBar),
            showSuggestions = prefs.getBoolean("showSuggestions", d.showSuggestions),
            clock24h = prefs.getBoolean("clock24h", d.clock24h),
            swipeDownNotifications = prefs.getBoolean("swipeDownNotifications", d.swipeDownNotifications),
            doubleTapLock = prefs.getBoolean("doubleTapLock", d.doubleTapLock),
            notificationDots = prefs.getBoolean("notificationDots", d.notificationDots),
        )
    }

    private fun save(s: LauncherSettings) = prefs.edit {
        putBoolean("glassIcons", s.glassIcons)
        putString("iconColorMode", s.iconColorMode.name)
        putInt("iconColor", s.iconColor)
        putInt("iconSize", s.iconSize)
        putBoolean("showLabels", s.showLabels)
        putInt("homeColumns", s.homeColumns)
        putInt("homeRows", s.homeRows)
        putInt("drawerColumns", s.drawerColumns)
        putInt("dockCount", s.dockCount)
        putFloat("refraction", s.refraction)
        putFloat("blur", s.blur)
        putFloat("frost", s.frost)
        putBoolean("rimLight", s.rimLight)
        putBoolean("showGlance", s.showGlance)
        putBoolean("showSearchBar", s.showSearchBar)
        putBoolean("showSuggestions", s.showSuggestions)
        putBoolean("clock24h", s.clock24h)
        putBoolean("swipeDownNotifications", s.swipeDownNotifications)
        putBoolean("doubleTapLock", s.doubleTapLock)
        putBoolean("notificationDots", s.notificationDots)
    }
}
