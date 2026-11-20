package org.suraksha.core

import java.security.MessageDigest
import java.util.Locale

data class ScanResult(val domain: String, val verdict: Verdict, val timestamp: Long)

/** Both notification delivery and simulation use this boundary. Only metadata leaves it. */
class MessagePipeline(private val scorer: UrlScorer, private val clock: () -> Long = System::currentTimeMillis) {
    private val recent = LinkedHashMap<String, Long>()
    @Synchronized
    fun scan(message: String, source: String, deduplicate: Boolean = true): List<ScanResult> {
        val boundedMessage = message.take(16_384)
        if (boundedMessage.isBlank()) return emptyList()
        val now = clock()
        recent.entries.removeAll { now - it.value >= 60_000L || now < it.value }
        val urls = UrlExtractor.extract(boundedMessage)
        val candidates = if (urls.isEmpty()) listOf("") else urls
        val ready = candidates.filter { url ->
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest("$source\u0000$url\u0000$boundedMessage".toByteArray()).joinToString("") {
                    "%02x".format(Locale.ROOT, it)
                }
            if (deduplicate && fingerprint in recent) false else {
                recent[fingerprint] = now
                while (recent.size > 256) recent.remove(recent.keys.first())
                true
            }
        }
        if (ready.isEmpty()) return emptyList()
        if (urls.isEmpty()) {
            val verdict = scorer.evaluateText(boundedMessage)
            return if (verdict.level == Level.SAFE) emptyList() else listOf(ScanResult("", verdict, now))
        }
        val verdicts = scorer.evaluateBatch(ready, boundedMessage)
        return ready.zip(verdicts).map { (url, verdict) ->
            ScanResult(UrlParser.parse(url)?.host.orEmpty(), verdict, now)
        }
    }
    @Synchronized fun clear() { recent.clear() }
}
