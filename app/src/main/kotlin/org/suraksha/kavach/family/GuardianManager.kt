package org.suraksha.kavach.family

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suraksha.core.*
import org.suraksha.kavach.KavachApplication
import org.suraksha.kavach.data.SecretStore

enum class GuardianStatus { OFF, READY, NEEDS_PERMISSION, REQUESTED, SENT, FAILED, RATE_LIMITED }
data class GuardianState(val configured: Boolean = false, val enabled: Boolean = false,
    val maskedNumber: String = "", val status: GuardianStatus = GuardianStatus.OFF)

fun interface SmsSender { fun send(destination: String, message: String, attempt: Long) }

class AndroidSmsSender(private val context: Context) : SmsSender {
    override fun send(destination: String, message: String, attempt: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("SMS permission is not granted")
        val sent = PendingIntent.getBroadcast(context, 670, Intent(context, GuardianSmsResult::class.java)
            .setAction(context.packageName + ".GUARDIAN_SENT").putExtra("attempt", attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
        requireNotNull(manager).sendTextMessage(destination, null, message, sent, null)
    }
}

@SuppressLint("UseKtx") // Check durable commits before permitting a carrier SMS.
class GuardianManager(private val context: Context, private val secrets: SecretStore,
                      private val sender: SmsSender, private val permission: () -> Boolean = {
                          ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                      }, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("guardian_settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(GuardianState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    fun refresh() {
        val number = runCatching { number() }.getOrNull()
        val enabled = prefs.getBoolean("enabled", false)
        val savedStatus = runCatching { GuardianStatus.valueOf(prefs.getString("status", "READY")!!) }.getOrDefault(GuardianStatus.READY)
        mutable.value = GuardianState(number != null, enabled, number?.takeLast(4).orEmpty(),
            when {
                number == null || !enabled -> GuardianStatus.OFF
                !permission() -> GuardianStatus.NEEDS_PERMISSION
                else -> savedStatus
            })
    }

    fun configure(input: String, enabled: Boolean): Boolean {
        val normalized = GuardianPolicy.normalizeNumber(input) ?: return false
        secrets.write("guardian_number", normalized.toByteArray())
        check(prefs.edit().putBoolean("enabled", enabled).putString("status", GuardianStatus.READY.name).commit())
        refresh()
        return true
    }
    fun setEnabled(enabled: Boolean) {
        if (enabled && number() == null) return
        check(prefs.edit().putBoolean("enabled", enabled).putString("status", GuardianStatus.READY.name).commit())
        refresh()
    }
    fun number(): String? = secrets.read("guardian_number")?.let { bytes ->
        try { GuardianPolicy.normalizeNumber(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
    }

    suspend fun onDanger(result: ScanResult) = mutex.withLock {
        if (result.verdict.level != Level.DANGEROUS || result.verdict.reasonCodes.any { it == ReasonCode.DEMO_ONLY || it == ReasonCode.EICAR_TEST }) return@withLock
        if (!prefs.getBoolean("enabled", false)) return@withLock
        val destination = runCatching { number() }.getOrNull() ?: return@withLock
        if (!permission()) { refresh(); return@withLock }
        val now = clock()
        val previous = if (prefs.contains("last_attempt")) prefs.getLong("last_attempt", 0) else null
        if (!GuardianPolicy.canAttempt(now, previous)) { setStatus(GuardianStatus.RATE_LIMITED); return@withLock }
        // Persist before the irreversible send, so process death cannot reset the limit.
        if (!prefs.edit().putLong("last_attempt", now).putString("status", GuardianStatus.REQUESTED.name).commit()) {
            setStatus(GuardianStatus.FAILED); return@withLock
        }
        refresh()
        try { sender.send(destination, MESSAGE, now) } catch (_: Exception) { setStatus(GuardianStatus.FAILED) }
    }

    fun recordResult(attempt: Long, success: Boolean) {
        if (prefs.contains("last_attempt") && prefs.getLong("last_attempt", 0) == attempt)
            setStatus(if (success) GuardianStatus.SENT else GuardianStatus.FAILED)
    }
    private fun setStatus(status: GuardianStatus) {
        prefs.edit().putString("status", status.name).apply()
        refresh()
    }
    companion object {
        const val MESSAGE = "A serious scam warning appeared on your family member's phone. Please call them. - Suraksha Kavach"
    }
}

class GuardianSmsResult : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != context.packageName + ".GUARDIAN_SENT") return
        val app = context.applicationContext as KavachApplication
        app.guardian.recordResult(intent.getLongExtra("attempt", -1), resultCode == Activity.RESULT_OK)
    }
}
