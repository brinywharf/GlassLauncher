package com.example.glasslauncher.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** Packages that currently have notifications (for notification dots). */
object NotificationDots {
    val packages = MutableStateFlow<Set<String>>(emptySet())
}

class DotsService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() {
        NotificationDots.packages.value = emptySet()
    }

    private fun refresh() {
        NotificationDots.packages.value = runCatching {
            activeNotifications.orEmpty()
                .filter { !it.isOngoing && it.packageName != packageName }
                .map { it.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }
}

/** Only used to lock the screen on double-tap. It reads nothing on screen. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        private var instance: LockService? = null
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
