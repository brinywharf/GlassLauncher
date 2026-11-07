package com.example.glasslauncher

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.glasslauncher.services.LockService
import com.example.glasslauncher.ui.t

object SystemActions {

    fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val statusBar = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(statusBar)
        }
    }

    fun lockScreen(context: Context) {
        if (LockService.lock()) return
        toast(context, t("Turn on Glass Launcher in Accessibility to lock with double-tap",
            "Çift dokunarak kilitlemek için Erişilebilirlik'te Glass Launcher'ı aç"))
        openAccessibilitySettings(context)
    }

    fun isLockServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(context, LockService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openAccessibilitySettings(context: Context) =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationAccessSettings(context: Context) =
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openHomeSettings(context: Context) =
        start(context, Intent(Settings.ACTION_HOME_SETTINGS))

    fun openUrl(context: Context, url: String) =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun openClock(context: Context) =
        start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun webSearch(context: Context, query: String) {
        val ok = start(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
        if (!ok) openUrl(context, "https://www.google.com/search?q=" + Uri.encode(query))
    }

    data class Battery(val percent: Int, val charging: Boolean)

    fun battery(context: Context): Battery {
        val bm = context.getSystemService(BatteryManager::class.java)
        return Battery(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), bm.isCharging)
    }

    fun nextAlarmMillis(context: Context): Long? =
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
}
