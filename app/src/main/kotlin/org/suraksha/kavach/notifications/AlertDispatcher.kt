package org.suraksha.kavach.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.suraksha.core.*
import org.suraksha.kavach.AlertActivity
import org.suraksha.kavach.R
import org.suraksha.kavach.data.AppSettings

class AlertDispatcher(private val context: Context) {
    companion object {
        const val CHANNEL = "danger_alerts"
        const val CAUTION_CHANNEL = "caution_alerts"
        const val ID = 401
        const val CAUTION_ID = 402
    }
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val localized = AppSettings.localized(context)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, localized.getString(R.string.alert_channel),
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = localized.getString(R.string.alert_channel_description)
            enableVibration(true)
        })
        manager.createNotificationChannel(NotificationChannel(CAUTION_CHANNEL, localized.getString(R.string.caution_channel),
            NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun post(result: ScanResult, allowFullScreen: Boolean = true) {
        if (result.verdict.level == Level.SAFE) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val localized = AppSettings.localized(context)
        val danger = result.verdict.level == Level.DANGEROUS
        val id = if (danger) ID else CAUTION_ID
        val channel = if (danger) CHANNEL else CAUTION_CHANNEL
        val pending = PendingIntent.getActivity(context, id, AlertActivity.intent(context, result),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = localized.getString(if (danger && result.domain.isEmpty()) R.string.alert_message_title else if (danger) R.string.alert_title else R.string.caution_title)
        val language = localized.resources.configuration.locales[0].language
        val reason = result.verdict.reasonCodes.firstOrNull()?.let { ReasonMessages.get(it, language) }
            ?: localized.getString(R.string.alert_advice)
        val public = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.app_name))
            .setContentText(localized.getString(R.string.private_warning)).build()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.replace('\n', ' '))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(pending)
            .setPriority(if (danger) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setColor(if (danger) Color.rgb(153, 27, 27) else Color.rgb(174, 102, 0))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true).setOnlyAlertOnce(true)
        if (danger && allowFullScreen && (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()))
            builder.setFullScreenIntent(pending, true)
        try { manager.notify(id, builder.build()) } catch (_: SecurityException) { /* Revocation is a normal runtime event. */ }
    }
    fun cancel() { manager.cancel(ID); manager.cancel(CAUTION_ID) }
}
