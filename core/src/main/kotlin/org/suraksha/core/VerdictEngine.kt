package org.suraksha.core

/** Priority: exact registrable-domain allowlist, blocklist, heuristics, learned models. */
class VerdictEngine(
    private val allowlist: Allowlist,
    private val blocklist: Blocklist,
    private val features: UrlFeatures,
    private val urlModel: UrlModel,
    private val textModel: TextModel
) : UrlScorer {
    private val heuristics = Heuristics(features)
    override fun evaluate(url: String, message: String): Verdict = evaluateWithText(url, textModel.score(message))
    override fun evaluateBatch(urls: List<String>, message: String): List<Verdict> {
        val textScore = textModel.score(message)
        return urls.map { evaluateWithText(it, textScore) }
    }
    override fun evaluateText(message: String): Verdict = fromText(textModel.score(message))

    private fun fromText(score: Double): Verdict = when {
        score >= textModel.dangerousThreshold -> Verdict(Level.DANGEROUS, score, setOf(ReasonCode.SCAM_LANGUAGE))
        score >= textModel.suspiciousThreshold -> Verdict(Level.SUSPICIOUS, score, setOf(ReasonCode.SCAM_LANGUAGE))
        else -> Verdict(Level.SAFE, score, emptySet())
    }

    private fun evaluateWithText(input: String, textScore: Double): Verdict {
        val url = UrlParser.parse(input) ?: return Verdict(Level.SUSPICIOUS, 0.5, setOf(ReasonCode.INVALID_URL))
        // The allowlist is a domain prior, not permission to ignore an explicit scam message.
        if (allowlist.contains(url.host) && !url.userInfo && !url.host.split('.').any { it.startsWith("xn--") }) {
            val textVerdict = fromText(textScore)
            return if (textVerdict.level != Level.SAFE) textVerdict
                else Verdict(Level.SAFE, 0.01, setOf(ReasonCode.TRUSTED_DOMAIN))
        }
        if (blocklist.contains(url.host)) return Verdict(Level.DANGEROUS, 0.99, setOf(ReasonCode.KNOWN_PHISHING))
        val heuristic = heuristics.evaluate(url)
        val modelScore = urlModel.score(features.extract(url))
        val reasons = heuristic.reasons.toMutableSet()
        if (modelScore >= urlModel.suspiciousThreshold) reasons += ReasonCode.URL_MODEL
        if (textScore >= textModel.suspiciousThreshold) reasons += ReasonCode.SCAM_LANGUAGE
        val level = when {
            heuristic.score >= 0.75 || modelScore >= urlModel.dangerousThreshold ||
                textScore >= textModel.dangerousThreshold -> Level.DANGEROUS
            heuristic.forceSuspicious || heuristic.score >= 0.45 ||
                modelScore >= urlModel.suspiciousThreshold || textScore >= textModel.suspiciousThreshold -> Level.SUSPICIOUS
            else -> Level.SAFE
        }
        return Verdict(level, maxOf(heuristic.score, modelScore, textScore), reasons)
    }

    companion object {
        fun load(read: (String) -> String): VerdictEngine {
            val suffixes = PublicSuffixList(read("public_suffix_list.dat"))
            return VerdictEngine(
                Allowlist(read("top_domains.txt"), suffixes), Blocklist(read("blocklist.txt")),
                UrlFeatures(suffixes), UrlModel(read("url_model.json")), TextModel(read("text_model.json"))
            )
        }
    }
}
