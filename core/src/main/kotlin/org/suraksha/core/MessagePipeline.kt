package org.suraksha.core

import java.net.URI
import java.security.MessageDigest

data class ScanResult(val domain: String, val verdict: Verdict, val timestamp: Long)

/** Shared boundary for notification delivery and simulation. No message text leaves it. */
class MessagePipeline(private val scorer: UrlScorer, private val clock: () -> Long = System::currentTimeMillis) {
    private val recent = LinkedHashMap<String, Long>()

    @Synchronized
    fun scan(message: String, source: String, deduplicate: Boolean = true): List<ScanResult> {
        val now = clock()
        recent.entries.removeAll { now - it.value >= 60_000L || now < it.value }
        return UrlExtractor.extract(message).mapNotNull { url ->
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest("$source\u0000$url".toByteArray()).joinToString("") { "%02x".format(it) }
            if (deduplicate && fingerprint in recent) return@mapNotNull null
            recent[fingerprint] = now
            while (recent.size > 256) recent.remove(recent.keys.first())
            val host = runCatching { URI(url).host ?: URI(url).rawAuthority?.substringAfterLast('@') }.getOrNull()
                ?: "unknown"
            ScanResult(host.lowercase(), scorer.evaluate(url, message), now)
        }
    }
    @Synchronized fun clear() { recent.clear() }
}
