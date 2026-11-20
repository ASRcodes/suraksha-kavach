package org.suraksha.core

object GuardianPolicy {
    const val WINDOW_MS = 30 * 60_000L
    fun canAttempt(now: Long, lastAttempt: Long?): Boolean =
        now >= 0 && (lastAttempt == null || (now >= lastAttempt && now - lastAttempt >= WINDOW_MS))

    fun normalizeNumber(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it !in "+0123456789 ()-" }) return null
        val compact = trimmed.filter { it == '+' || it in '0'..'9' }
        if (compact.count { it == '+' } > 1 || ('+' in compact && !compact.startsWith('+'))) return null
        val digits = compact.removePrefix("+")
        if (digits.length !in 7..15 || digits.all { it == '0' }) return null
        return compact
    }
}
