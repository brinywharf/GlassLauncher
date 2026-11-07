package com.example.glasslauncher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class FolderData(val name: String, val apps: List<String>)

const val FOLDER_PREFIX = "folder:"
fun isFolderId(id: String) = id.startsWith(FOLDER_PREFIX)

/**
 * Everything the user arranged or customised, keyed by "package/activity".
 * Home entries are app keys or folder ids ("folder:…").
 */
data class UserData(
    val initialized: Boolean = false,
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val colors: Map<String, Int> = emptyMap(),
    val launches: Map<String, Int> = emptyMap(),
    val widgets: List<Int> = emptyList(),
    val folders: Map<String, FolderData> = emptyMap(),
)

class UserDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("userdata", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()

    fun update(block: (UserData) -> UserData) {
        val next = block(_state.value)
        save(next)
        _state.value = next
    }

    fun exportJson(): JSONObject = PrefsJson.export(prefs)

    fun importJson(o: JSONObject) {
        PrefsJson.import(prefs, o)
        _state.value = load()
    }

    private fun load() = UserData(
        initialized = prefs.getBoolean("initialized", false),
        home = prefs.getString("home", null).toStringList(),
        dock = prefs.getString("dock", null).toStringList(),
        hidden = prefs.getString("hidden", null).toStringList().toSet(),
        renames = prefs.getString("renames", null).toStringMap(),
        colors = prefs.getString("colors", null).toIntMap(),
        launches = prefs.getString("launches", null).toIntMap(),
        widgets = prefs.getString("widgets", null).toStringList().mapNotNull { it.toIntOrNull() },
        folders = prefs.getString("folders", null).toFolders(),
    )

    private fun save(d: UserData) = prefs.edit {
        putBoolean("initialized", d.initialized)
        putString("home", JSONArray(d.home).toString())
        putString("dock", JSONArray(d.dock).toString())
        putString("hidden", JSONArray(d.hidden.toList()).toString())
        putString("renames", JSONObject(d.renames).toString())
        putString("colors", JSONObject(d.colors).toString())
        putString("launches", JSONObject(d.launches).toString())
        putString("widgets", JSONArray(d.widgets.map { it.toString() }).toString())
        putString("folders", JSONObject().apply {
            d.folders.forEach { (id, f) -> put(id, JSONObject().put("name", f.name).put("apps", JSONArray(f.apps))) }
        }.toString())
    }
}

private fun String?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return runCatching {
        val a = JSONArray(this)
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())
}

private fun String?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return runCatching {
        val o = JSONObject(this)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())
}

private fun String?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return runCatching {
        val o = JSONObject(this)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())
}

private fun String?.toFolders(): Map<String, FolderData> {
    if (this == null) return emptyMap()
    return runCatching {
        val o = JSONObject(this)
        o.keys().asSequence().associateWith { id ->
            val f = o.getJSONObject(id)
            val a = f.getJSONArray("apps")
            FolderData(f.optString("name"), List(a.length()) { a.getString(it) })
        }
    }.getOrDefault(emptyMap())
}
