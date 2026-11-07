package com.example.glasslauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class WidgetProviderItem(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val icon: ImageBitmap?,
    val cols: Int,
    val rows: Int,
)

/** Hosts home-screen widgets: lists them, binds them, runs their setup screens. */
class WidgetController(private val activity: ComponentActivity) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = AppWidgetHost(activity, HOST_ID)

    /** Set by the UI: called with the id once a widget is ready to show. */
    var onWidgetAdded: (Int) -> Unit = {}

    private var pendingBind: Pair<Int, AppWidgetProviderInfo>? = null
    private var configuringId = -1

    private val bindLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingBind ?: return@registerForActivityResult
        pendingBind = null
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish(pending.first, pending.second)
        else host.deleteAppWidgetId(pending.first)
    }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrFinish(id, info)
        } else {
            pendingBind = id to info
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            runCatching { bindLauncher.launch(intent) }.onFailure {
                pendingBind = null
                host.deleteAppWidgetId(id)
            }
        }
    }

    fun remove(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    private fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            configuringId = id
            val started = runCatching {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
            }.isSuccess
            if (!started) {
                configuringId = -1
                onWidgetAdded(id)
            }
        } else {
            onWidgetAdded(id)
        }
    }

    /** Forwarded from MainActivity.onActivityResult. */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_CONFIGURE || configuringId == -1) return
        val id = configuringId
        configuringId = -1
        if (resultCode == Activity.RESULT_OK) onWidgetAdded(id) else host.deleteAppWidgetId(id)
    }

    suspend fun loadProviders(): List<WidgetProviderItem> = withContext(Dispatchers.IO) {
        val pm = activity.packageManager
        val dm = activity.resources.displayMetrics
        val iconPx = (40 * dm.density).toInt()
        manager.installedProviders.map { info ->
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            val icon = runCatching {
                info.loadIcon(activity, dm.densityDpi)?.toBitmap(iconPx, iconPx)?.asImageBitmap()
            }.getOrNull()
            WidgetProviderItem(
                info = info,
                label = runCatching { info.loadLabel(pm) }.getOrNull() ?: appLabel,
                appLabel = appLabel,
                icon = icon,
                cols = max(1, ((info.minWidth / dm.density + 30) / 70).toInt()),
                rows = max(1, ((info.minHeight / dm.density + 30) / 70).toInt()),
            )
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    companion object {
        private const val HOST_ID = 2048
        private const val REQ_CONFIGURE = 4711
    }
}
