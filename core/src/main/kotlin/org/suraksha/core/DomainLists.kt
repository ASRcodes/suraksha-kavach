package org.suraksha.core

/** Full offline PSL, including private hosting suffixes. No last-two-label shortcuts. */
class PublicSuffixList(text: String) {
    private val exact = HashSet<String>()
    private val wildcard = HashSet<String>()
    private val exceptions = HashSet<String>()
    init {
        text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("//") }.forEach { rule ->
            val target = when {
                rule.startsWith("!.") -> return@forEach
                rule.startsWith("!") -> exceptions
                rule.startsWith("*.") -> wildcard
                else -> exact
            }
            UrlParser.asciiHost(rule.removePrefix("!").removePrefix("*."))?.let(target::add)
        }
    }
    fun registrableDomain(input: String): String? {
        val host = UrlParser.asciiHost(input) ?: return null
        if (host.contains(':') || host.split('.').all { it.toIntOrNull() != null }) return null
        val labels = host.split('.')
        var suffixLength = 1 // PSL implicit wildcard rule.
        for (i in labels.indices) {
            val suffix = labels.drop(i).joinToString(".")
            if (suffix in exceptions) {
                suffixLength = labels.size - i - 1
                break
            }
            if (suffix in exact) suffixLength = maxOf(suffixLength, labels.size - i)
            if (i > 0 && suffix in wildcard) suffixLength = maxOf(suffixLength, labels.size - i + 1)
        }
        return if (labels.size > suffixLength) labels.takeLast(suffixLength + 1).joinToString(".") else null
    }
}

class Allowlist(text: String, private val suffixes: PublicSuffixList) {
    private val domains = domainLines(text).filter { suffixes.registrableDomain(it) == it }.toSet()
    fun contains(host: String): Boolean = suffixes.registrableDomain(host)?.let { it in domains } == true
}

class Blocklist(text: String) {
    private val hosts = domainLines(text)
    /** Match a domain or its children, never a textual suffix like notexample.com. */
    fun contains(host: String): Boolean {
        val normalized = UrlParser.asciiHost(host) ?: return false
        var candidate = normalized
        while (true) {
            if (candidate in hosts) return true
            if (!candidate.contains('.')) return false
            candidate = candidate.substringAfter('.')
        }
    }
}

internal fun domainLines(text: String): Set<String> = text.lineSequence()
    .map { it.substringBefore('#').trim() }.filter(String::isNotEmpty)
    .mapNotNull(UrlParser::asciiHost).toSet()
