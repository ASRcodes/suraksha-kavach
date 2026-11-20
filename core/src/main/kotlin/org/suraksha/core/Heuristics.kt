package org.suraksha.core

data class HeuristicResult(val score: Double, val reasons: Set<ReasonCode>, val forceSuspicious: Boolean)

class Heuristics(private val features: UrlFeatures) {
    fun evaluate(url: ParsedUrl): HeuristicResult {
        val reasons = features.flags(url)
        val weights = mapOf(
            ReasonCode.PUNYCODE to 0.20, ReasonCode.LOOKALIKE to 0.45,
            ReasonCode.BRAND_IN_SUBDOMAIN to 0.45, ReasonCode.IP_HOST to 0.30,
            ReasonCode.RISKY_TLD to 0.10, ReasonCode.MANY_HYPHENS to 0.15,
            ReasonCode.LONG_URL to 0.10, ReasonCode.SHORTENER to 0.35,
            ReasonCode.CREDENTIAL_PATH to 0.15, ReasonCode.USER_INFO to 0.50
        )
        return HeuristicResult(reasons.sumOf { weights[it] ?: 0.0 }.coerceAtMost(0.99), reasons,
            reasons.any { it in setOf(ReasonCode.SHORTENER, ReasonCode.PUNYCODE, ReasonCode.IP_HOST) })
    }
}
