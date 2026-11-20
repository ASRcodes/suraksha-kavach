package org.suraksha.kavach.notifications

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.launch
import org.suraksha.kavach.KavachApplication

class KavachNotificationListener : NotificationListenerService() {
    private val graph get() = application as KavachApplication
    override fun onListenerConnected() { graph.listenerConnected.value = true }
    override fun onListenerDisconnected() { graph.listenerConnected.value = false }
    override fun onDestroy() { graph.listenerConnected.value = false; super.onDestroy() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val supported = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.mms", Telephony.Sms.getDefaultSmsPackage(this))
        if (sbn.packageName !in supported || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!graph.demoEnabled.value) return
        val extras = sbn.notification.extras
        val text = runCatching {
            listOfNotNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                extras.getCharSequence(Notification.EXTRA_TEXT)).joinToString("\n").take(16_384)
        }.getOrNull().orEmpty()
        if (text.isBlank()) return
        graph.scope.launch { graph.processMessage(text, sbn.packageName) }
    }
}
