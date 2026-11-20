package org.suraksha.core

import java.text.Normalizer
import java.util.Locale

enum class HashKind { EICAR_TEST, MALWARE }
data class ApkSignals(val permissions: Set<String>, val accessibilityService: Boolean = false, val deviceAdmin: Boolean = false)

/** Requested permissions are warning signs, not proof that an APK is malicious. */
class FileRules(private val badHashes: Map<String, HashKind>) {
    init { require(badHashes.keys.all { it.matches(Regex("[0-9a-f]{64}")) }) }
    fun evaluate(sha256: String, fileName: String, apk: ApkSignals? = null,
                 apkExpected: Boolean = false, readable: Boolean = true): Verdict {
        val reasons = linkedSetOf(ReasonCode.FILE_SCAN)
        fun result(level: Level, score: Double, code: ReasonCode): Verdict {
            reasons += code
            return Verdict(level, score, reasons)
        }
        if (!readable) return result(Level.SUSPICIOUS, 0.5, ReasonCode.UNREADABLE_FILE)
        when (badHashes[sha256.lowercase(Locale.ROOT)]) {
            HashKind.EICAR_TEST -> return result(Level.DANGEROUS, 1.0, ReasonCode.EICAR_TEST)
            HashKind.MALWARE -> return result(Level.DANGEROUS, 1.0, ReasonCode.KNOWN_BAD_HASH)
            null -> Unit
        }
        if (apkExpected && apk == null) return result(Level.SUSPICIOUS, 0.5, ReasonCode.UNREADABLE_FILE)
        if (apk != null) {
            val permissions = apk.permissions.map { it.removePrefix("android.permission.") }.toSet()
            val sms = permissions.any { it in setOf("READ_SMS", "RECEIVE_SMS", "SEND_SMS") }
            val overlay = "SYSTEM_ALERT_WINDOW" in permissions
            val install = permissions.any { it in setOf("REQUEST_INSTALL_PACKAGES", "INSTALL_PACKAGES") }
            val accessibility = apk.accessibilityService || "BIND_ACCESSIBILITY_SERVICE" in permissions
            val admin = apk.deviceAdmin || "BIND_DEVICE_ADMIN" in permissions
            val risky = sms || overlay || install || accessibility || admin
            val combination = (sms && (overlay || install || accessibility || admin)) ||
                ((accessibility || admin) && (overlay || install))
            val normalized = Normalizer.normalize(fileName, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
                .replace(Regex("[\\s_\\-]+"), "")
            val lure = listOf("challan", "kyc", "pmkisan", "चालान", "केवाईसी", "ਚਲਾਨ").any { it in normalized }
            if (risky) reasons += ReasonCode.RISKY_PERMISSIONS
            if (lure) reasons += ReasonCode.SUSPICIOUS_FILENAME
            if (combination) return Verdict(Level.DANGEROUS, 0.95, reasons)
            if (risky || lure) return Verdict(Level.SUSPICIOUS, 0.6, reasons)
        }
        return result(Level.SAFE, 0.0, ReasonCode.NO_KNOWN_THREAT)
    }
}
