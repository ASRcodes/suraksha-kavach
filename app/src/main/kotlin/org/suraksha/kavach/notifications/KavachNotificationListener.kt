package org.suraksha.kavach.notifications

import android.app.Notification
import android.content.ComponentName
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.suraksha.kavach.KavachApplication

class KavachNotificationListener : NotificationListenerService() {
    private val graph get() = application as KavachApplication
    // Bodies remain only in this bounded, transient queue; no sender or body is written to disk.
    private val incoming = Channel<Pair<String, String>>(32, BufferOverflow.DROP_OLDEST)
    private var worker: Job? = null
    override fun onCreate() {
        super.onCreate()
        worker = graph.scope.launch { for ((text, source) in incoming) graph.processMessage(text, source) }
    }
    override fun onListenerConnected() { graph.listenerConnected.value = true }
    override fun onListenerDisconnected() {
        graph.listenerConnected.value = false
        if (packageName in NotificationManagerCompat.getEnabledListenerPackages(this))
            runCatching { requestRebind(ComponentName(this, KavachNotificationListener::class.java)) }
    }
    override fun onDestroy() {
        incoming.cancel(); worker?.cancel()
        graph.listenerConnected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val supported = setOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
            runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull())
        if (sbn.packageName !in supported || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (!graph.settings.state.value.protectionEnabled) return
        val text = runCatching {
            val styled = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
            val latest = styled?.messages?.lastOrNull()?.text
            val extras = sbn.notification.extras
            (latest ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.take(16_384)?.toString().orEmpty()
        }.getOrDefault("")
        if (text.isNotBlank()) incoming.trySend(text to sbn.packageName)
    }
}
