package org.suraksha.core

/** Bounded, offline extraction. Inputs and complete URLs must never be persisted or logged. */
object UrlExtractor {
    private const val LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}\\p{M}-]{0,61}[\\p{L}\\p{N}\\p{M}])?"
    private val links = Regex(
        "(?i)(?:https?://[^\\s<>\\\"।॥]+|(?<![A-Za-z0-9_@./-])(?:$LABEL\\.)+(?:[A-Za-z]{2,24}|xn--[A-Za-z0-9-]+)(?::[0-9]{1,5})?(?:[/?#][^\\s<>\\\"।॥]*)?)"
    )
    fun extract(text: String): List<String> = links.findAll(text.take(16_384))
        .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '“', '”', '’', '।', '॥') }
        .filter { it.isNotBlank() }
        .map { if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it" }
        .distinct().take(24).toList()
}
