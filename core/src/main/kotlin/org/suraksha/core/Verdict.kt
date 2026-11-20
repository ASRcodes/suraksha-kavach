package org.suraksha.core

enum class Level { SAFE, SUSPICIOUS, DANGEROUS }
enum class ReasonCode {
    DEMO_ONLY, TRUSTED_DOMAIN, KNOWN_PHISHING, PUNYCODE, LOOKALIKE,
    BRAND_IN_SUBDOMAIN, IP_HOST, RISKY_TLD, MANY_HYPHENS, LONG_URL,
    SHORTENER, CREDENTIAL_PATH, USER_INFO, URL_MODEL, SCAM_LANGUAGE,
    INVALID_URL, KNOWN_BAD_HASH, RISKY_PERMISSIONS, SUSPICIOUS_FILENAME,
    UNREADABLE_FILE, NO_KNOWN_THREAT
}
data class Verdict(val level: Level, val score: Double, val reasonCodes: Set<ReasonCode>) {
    init { require(score.isFinite() && score in 0.0..1.0) }
}
fun interface UrlScorer {
    fun evaluate(url: String, message: String): Verdict
    fun evaluateBatch(urls: List<String>, message: String): List<Verdict> = urls.map { evaluate(it, message) }
    fun evaluateText(message: String): Verdict = Verdict(Level.SAFE, 0.0, emptySet())
}
/** Debug-only wiring check. All URLs follow the same rule; there are no trigger links. */
class DemoScorer : UrlScorer {
    override fun evaluate(url: String, message: String) =
        Verdict(Level.DANGEROUS, 1.0, setOf(ReasonCode.DEMO_ONLY))
}
