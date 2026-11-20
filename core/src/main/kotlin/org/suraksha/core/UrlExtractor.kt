package org.suraksha.core

/** Bounded extraction supporting Latin/IDN domains and Indic text around links. */
object UrlExtractor {
    private const val ASCII_LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
    private const val IDN_LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}\\p{M}-]{0,61}[\\p{L}\\p{N}\\p{M}])?"
    private const val HOST_END = "(?![\\p{L}\\p{N}\\p{M}_-]|\\.[\\p{L}\\p{N}\\p{M}])"
    private const val END = "(?::[0-9]{1,5})?(?:[/?#][^\\s<>\\\"।॥]*)?"
    private val links = Regex(
        "(?i)(?:https?://[^\\s<>\\\"।॥]+|" +
            "(?<![A-Za-z0-9_@./-])(?:$ASCII_LABEL\\.)+(?:xn--[A-Za-z0-9-]+|[A-Za-z]{2,63})$HOST_END$END|" +
            "(?<![\\p{L}\\p{N}_@./-])(?:$IDN_LABEL\\.)+[\\p{L}\\p{M}]{2,63}$HOST_END$END|" +
            "(?<![A-Za-z0-9_@./-])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$END)"
    )
    private val joinedLinks = Regex(",(?=https?://)", RegexOption.IGNORE_CASE)
    fun extract(text: String): List<String> = links.findAll(text.take(16_384))
        .flatMap { joinedLinks.split(it.value).asSequence() }
        .map(::trimPunctuation)
        .filter(String::isNotBlank)
        .map { if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it" }
        .distinct().take(24).toList()

    private fun trimPunctuation(input: String): String {
        var value = input.trimEnd('.', ',', ';', ':', '!', '?', '\'', '“', '”', '’', '।', '॥')
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (value.endsWith(close) && value.count { it == close } > value.count { it == open }) value = value.dropLast(1)
        }
        return value
    }
}
