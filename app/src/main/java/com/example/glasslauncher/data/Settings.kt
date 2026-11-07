package com.example.glasslauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class IconColorMode { AUTO, SINGLE, CLEAR }
enum class IconShape { SQUIRCLE, ROUNDED, CIRCLE, SQUARE, TEARDROP }
enum class GestureAction { NONE, APP_DRAWER, NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, SEARCH, LAUNCHER_SETTINGS, OPEN_APP }
enum class PageEffect { SLIDE, CUBE, STACK, ZOOM, ROTATE, FADE }
enum class AnimSpeed(val factor: Float) { RELAXED(0.7f), NORMAL(1f), FAST(1.35f), FASTER(1.8f) }
enum class OpenAnim { ZOOM, REVEAL, SYSTEM }
enum class DrawerSort { ALPHABETICAL, MOST_USED, NEWEST }
enum class SearchBarPos { BOTTOM, TOP, HIDDEN }
enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

data class LauncherSettings(
    // Icons
    val glassIcons: Boolean = true,
    val iconColorMode: IconColorMode = IconColorMode.AUTO,
    val iconColor: Int = 0xFF5C8DFF.toInt(),
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconSize: Int = 58,
    val labelSize: Int = 12,
    val notificationDots: Boolean = false,
    // Home
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val showLabels: Boolean = true,
    val showGlance: Boolean = true,
    val clock24h: Boolean = true,
    val searchBarPos: SearchBarPos = SearchBarPos.BOTTOM,
    val showPageDots: Boolean = true,
    val pageEffect: PageEffect = PageEffect.SLIDE,
    val addNewAppsToHome: Boolean = false,
    // Dock
    val showDock: Boolean = true,
    val dockBackground: Boolean = true,
    val dockCount: Int = 5,
    // Drawer
    val drawerColumns: Int = 4,
    val drawerLabels: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.ALPHABETICAL,
    val showSuggestions: Boolean = true,
    val drawerFastScroll: Boolean = true,
    val drawerDim: Float = 0.30f,
    // Glass
    val refraction: Float = 1.6f,
    val blur: Float = 1.2f,
    val frost: Float = 0.06f,
    val rimLight: Boolean = true,
    // Motion
    val animSpeed: AnimSpeed = AnimSpeed.NORMAL,
    val openAnim: OpenAnim = OpenAnim.ZOOM,
    val haptics: Boolean = true,
    // Gestures
    val swipeUp: GestureAction = GestureAction.APP_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val doubleTap: GestureAction = GestureAction.NONE,
    val swipeUpApp: String = "",
    val swipeDownApp: String = "",
    val doubleTapApp: String = "",
    // System
    val hideStatusBar: Boolean = false,
    val orientation: ScreenOrientation = ScreenOrientation.AUTO,
    val maxRefreshRate: Boolean = true,
)

private inline fun <reified T : Enum<T>> SharedPreferences.enumOf(key: String, default: T): T =
    runCatching { enumValueOf<T>(getString(key, null) ?: default.name) }.getOrDefault(default)

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

    fun exportJson(): JSONObject = PrefsJson.export(prefs)

    fun importJson(o: JSONObject) {
        PrefsJson.import(prefs, o)
        _state.value = load()
    }

    private fun load(): LauncherSettings {
        val d = defaults
        val p = prefs
        return LauncherSettings(
            glassIcons = p.getBoolean("glassIcons", d.glassIcons),
            iconColorMode = p.enumOf("iconColorMode", d.iconColorMode),
            iconColor = p.getInt("iconColor", d.iconColor),
            iconShape = p.enumOf("iconShape", d.iconShape),
            iconSize = p.getInt("iconSize", d.iconSize),
            labelSize = p.getInt("labelSize", d.labelSize),
            notificationDots = p.getBoolean("notificationDots", d.notificationDots),
            homeColumns = p.getInt("homeColumns", d.homeColumns),
            homeRows = p.getInt("homeRows", d.homeRows),
            showLabels = p.getBoolean("showLabels", d.showLabels),
            showGlance = p.getBoolean("showGlance", d.showGlance),
            clock24h = p.getBoolean("clock24h", d.clock24h),
            searchBarPos = p.enumOf("searchBarPos", d.searchBarPos),
            showPageDots = p.getBoolean("showPageDots", d.showPageDots),
            pageEffect = p.enumOf("pageEffect", d.pageEffect),
            addNewAppsToHome = p.getBoolean("addNewAppsToHome", d.addNewAppsToHome),
            showDock = p.getBoolean("showDock", d.showDock),
            dockBackground = p.getBoolean("dockBackground", d.dockBackground),
            dockCount = p.getInt("dockCount", d.dockCount),
            drawerColumns = p.getInt("drawerColumns", d.drawerColumns),
            drawerLabels = p.getBoolean("drawerLabels", d.drawerLabels),
            drawerSort = p.enumOf("drawerSort", d.drawerSort),
            showSuggestions = p.getBoolean("showSuggestions", d.showSuggestions),
            drawerFastScroll = p.getBoolean("drawerFastScroll", d.drawerFastScroll),
            drawerDim = p.getFloat("drawerDim", d.drawerDim),
            refraction = p.getFloat("refraction", d.refraction),
            blur = p.getFloat("blur", d.blur),
            frost = p.getFloat("frost", d.frost),
            rimLight = p.getBoolean("rimLight", d.rimLight),
            animSpeed = p.enumOf("animSpeed", d.animSpeed),
            openAnim = p.enumOf("openAnim", d.openAnim),
            haptics = p.getBoolean("haptics", d.haptics),
            swipeUp = p.enumOf("swipeUp", d.swipeUp),
            swipeDown = p.enumOf("swipeDown", d.swipeDown),
            doubleTap = p.enumOf("doubleTap", d.doubleTap),
            swipeUpApp = p.getString("swipeUpApp", d.swipeUpApp) ?: "",
            swipeDownApp = p.getString("swipeDownApp", d.swipeDownApp) ?: "",
            doubleTapApp = p.getString("doubleTapApp", d.doubleTapApp) ?: "",
            hideStatusBar = p.getBoolean("hideStatusBar", d.hideStatusBar),
            orientation = p.enumOf("orientation", d.orientation),
            maxRefreshRate = p.getBoolean("maxRefreshRate", d.maxRefreshRate),
        )
    }

    private fun save(s: LauncherSettings) = prefs.edit {
        putBoolean("glassIcons", s.glassIcons)
        putString("iconColorMode", s.iconColorMode.name)
        putInt("iconColor", s.iconColor)
        putString("iconShape", s.iconShape.name)
        putInt("iconSize", s.iconSize)
        putInt("labelSize", s.labelSize)
        putBoolean("notificationDots", s.notificationDots)
        putInt("homeColumns", s.homeColumns)
        putInt("homeRows", s.homeRows)
        putBoolean("showLabels", s.showLabels)
        putBoolean("showGlance", s.showGlance)
        putBoolean("clock24h", s.clock24h)
        putString("searchBarPos", s.searchBarPos.name)
        putBoolean("showPageDots", s.showPageDots)
        putString("pageEffect", s.pageEffect.name)
        putBoolean("addNewAppsToHome", s.addNewAppsToHome)
        putBoolean("showDock", s.showDock)
        putBoolean("dockBackground", s.dockBackground)
        putInt("dockCount", s.dockCount)
        putInt("drawerColumns", s.drawerColumns)
        putBoolean("drawerLabels", s.drawerLabels)
        putString("drawerSort", s.drawerSort.name)
        putBoolean("showSuggestions", s.showSuggestions)
        putBoolean("drawerFastScroll", s.drawerFastScroll)
        putFloat("drawerDim", s.drawerDim)
        putFloat("refraction", s.refraction)
        putFloat("blur", s.blur)
        putFloat("frost", s.frost)
        putBoolean("rimLight", s.rimLight)
        putString("animSpeed", s.animSpeed.name)
        putString("openAnim", s.openAnim.name)
        putBoolean("haptics", s.haptics)
        putString("swipeUp", s.swipeUp.name)
        putString("swipeDown", s.swipeDown.name)
        putString("doubleTap", s.doubleTap.name)
        putString("swipeUpApp", s.swipeUpApp)
        putString("swipeDownApp", s.swipeDownApp)
        putString("doubleTapApp", s.doubleTapApp)
        putBoolean("hideStatusBar", s.hideStatusBar)
        putString("orientation", s.orientation.name)
        putBoolean("maxRefreshRate", s.maxRefreshRate)
    }
}

/** Typed export/import of a SharedPreferences file (used for backup & restore). */
object PrefsJson {
    fun export(prefs: SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((k, v) in prefs.all) {
            val e = JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is String -> e.put("t", "s").put("v", v)
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    fun import(prefs: SharedPreferences, o: JSONObject) {
        prefs.edit(commit = true) {
            clear()
            for (k in o.keys()) {
                val e = o.optJSONObject(k) ?: continue
                when (e.optString("t")) {
                    "b" -> putBoolean(k, e.getBoolean("v"))
                    "i" -> putInt(k, e.getInt("v"))
                    "l" -> putLong(k, e.getLong("v"))
                    "f" -> putFloat(k, e.getDouble("v").toFloat())
                    "s" -> putString(k, e.getString("v"))
                }
            }
        }
    }
}
