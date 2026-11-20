package org.suraksha.core

import java.net.IDN
import java.net.URI
import java.util.Locale

data class ParsedUrl(
    val raw: String, val host: String, val path: String, val query: String,
    val https: Boolean, val port: Int, val userInfo: Boolean, val ip: Boolean
)

object UrlParser {
    private val ipv4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    fun asciiHost(value: String): String? = runCatching {
        val host = value.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty() || host.length > 253 || host.any { it.isWhitespace() }) return null
        if (host.contains(':')) return host.removeSurrounding("[", "]").takeIf {
            it.count { c -> c == ':' } >= 2 && it.all { c -> c in "0123456789abcdef:." }
        }
        IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }.getOrNull()

    fun parse(input: String): ParsedUrl? = runCatching {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > 16_384 || trimmed.any { it.code < 32 }) return null
        val raw = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = URI(raw)
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        val authority = uri.rawAuthority ?: return null
        val hostPort = authority.substringAfterLast('@')
        val hostPart = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        val host = asciiHost(hostPart) ?: return null
        val ip = host.contains(':') || (ipv4.matches(host) && host.split('.').all { it.toInt() in 0..255 })
        if (!ip && (!host.contains('.') || host.split('.').any { it.isEmpty() })) return null
        if (ipv4.matches(host) && !ip) return null
        val portPart = if (hostPort.startsWith("[")) hostPort.substringAfter("]", "").removePrefix(":")
            else hostPort.substringAfter(':', "")
        val port = if (portPart.isEmpty()) -1 else portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        ParsedUrl(raw, host, uri.rawPath.orEmpty(), uri.rawQuery.orEmpty(),
            uri.scheme.equals("https", true), port, authority.contains('@'), ip)
    }.getOrNull()
}
