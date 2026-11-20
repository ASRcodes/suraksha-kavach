package org.suraksha.core

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** NFKC + lower + whitespace collapse, codepoint character n-grams, sublinear TF-IDF, L2, logistic. */
class TextModel(json: String) {
    private val root = Json.parseToJsonElement(json).jsonObject
    val suspiciousThreshold = root.getValue("suspicious_threshold").jsonPrimitive.double
    val dangerousThreshold = root.getValue("dangerous_threshold").jsonPrimitive.double
    private val vocabulary = root.getValue("vocabulary").jsonObject.mapValues { it.value.jsonPrimitive.int }
    private val idf = root.getValue("idf").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
    private val weights = root.getValue("weights").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
    private val bias = root.getValue("bias").jsonPrimitive.double
    private val minN = root.getValue("ngram_min").jsonPrimitive.int
    private val maxN = root.getValue("ngram_max").jsonPrimitive.int
    companion object {
        private val whitespace = Regex("[\\s\\p{Z}\\u0085]+")
        private val links = Regex(
            """(?i)https?://\S+|www\.\S+|(?<![\p{L}\p{N}_@])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:[a-z]{2,63})(?::[0-9]{1,5})?(?:[/?#]\S*)?"""
        )
        fun normalize(text: String): String {
            val base = whitespace.replace(
                Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT), " ").trim()
            val points = base.codePoints().limit(4096).toArray()
            return whitespace.replace(links.replace(String(points, 0, points.size), " "), " ").trim()
        }
    }
    init {
        require(root.getValue("schema_version").jsonPrimitive.int == 1)
        require(root.getValue("normalization").jsonPrimitive.content == "nfkc-lower-space-strip-url-v2")
        require(minN in 1..5 && maxN in minN..5 && idf.size == weights.size && vocabulary.size == weights.size)
    }
    fun score(text: String): Double {
        val points = normalize(text).codePoints().toArray()
        if (points.isEmpty()) return 0.0
        val counts = HashMap<Int, Int>()
        for (n in minN..maxN) {
            if (points.size < n) continue
            for (start in 0..points.size - n) {
                val gram = String(points, start, n)
                vocabulary[gram]?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        var norm = 0.0
        var weighted = 0.0
        counts.forEach { (index, count) ->
            val value = (1.0 + ln(count.toDouble())) * idf[index]
            norm += value * value
            weighted += value * weights[index]
        }
        val logit = bias + if (norm > 0) weighted / sqrt(norm) else 0.0
        return if (logit >= 0) 1.0 / (1.0 + exp(-logit)) else exp(logit) / (1.0 + exp(logit))
    }
}
