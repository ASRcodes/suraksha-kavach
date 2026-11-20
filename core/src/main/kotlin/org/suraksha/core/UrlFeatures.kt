package org.suraksha.core

import java.net.IDN
import java.util.Locale
import kotlin.math.ln

/** Version 1. Keep exactly aligned with ml/features.py; parity fixtures enforce it. */
class UrlFeatures(private val suffixes: PublicSuffixList) {
    companion object {
        val NAMES = listOf(
            "url_length", "host_length", "path_length", "query_length", "host_dots",
            "host_hyphens", "url_at", "url_percent", "url_digits", "digit_ratio",
            "path_depth", "query_pairs", "is_https", "is_ip", "has_punycode",
            "is_shortener", "risky_tld", "credential_path", "brand_subdomain",
            "lookalike", "subdomain_depth", "host_entropy", "non_ascii", "has_port", "has_user_info"
        )
        val SHORTENERS = setOf("bit.ly", "t.co", "tinyurl.com", "is.gd", "cutt.ly", "rb.gy",
            "shorturl.at", "lnkd.in", "goo.gl", "ow.ly", "rebrand.ly", "s.id", "tiny.cc")
        val RISKY_TLDS = setOf("xyz", "top", "click", "work", "zip", "mov", "cam", "buzz", "icu", "loan", "rest", "support")
        val BRANDS = mapOf("google" to "google.com", "youtube" to "youtube.com", "paypal" to "paypal.com",
            "amazon" to "amazon.com", "microsoft" to "microsoft.com", "apple" to "apple.com",
            "whatsapp" to "whatsapp.com", "facebook" to "facebook.com", "instagram" to "instagram.com",
            "sbi" to "sbi.co.in", "hdfc" to "hdfcbank.com", "icici" to "icicibank.com",
            "paytm" to "paytm.com", "phonepe" to "phonepe.com", "netflix" to "netflix.com")
        private val credentials = Regex("(?i)(login|signin|sign-in|verify|verification|kyc|password|otp|account[-_/]?update|secure[-_/]?bank|refund|challan)")
        private val skeletonMap = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x',
            'у' to 'y', 'і' to 'i', 'ј' to 'j', 'ӏ' to 'l', '0' to 'o', '1' to 'l',
            '3' to 'e', '5' to 's', '7' to 't'
        )
        private fun oneEditAway(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0; var j = 0; var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                if (a.length >= b.length) i++
                if (b.length >= a.length) j++
            }
            return edits + (a.length - i) + (b.length - j) == 1
        }
    }

    fun flags(url: ParsedUrl): Set<ReasonCode> {
        val reasons = linkedSetOf<ReasonCode>()
        val registered = suffixes.registrableDomain(url.host).orEmpty()
        val subdomain = url.host.removeSuffix(registered).trimEnd('.')
        if (url.host.split('.').any { it.startsWith("xn--") }) reasons += ReasonCode.PUNYCODE
        if (url.ip) reasons += ReasonCode.IP_HOST
        if (url.host.substringAfterLast('.') in RISKY_TLDS) reasons += ReasonCode.RISKY_TLD
        if (url.host.count { it == '-' } >= 4) reasons += ReasonCode.MANY_HYPHENS
        if (url.raw.length > 160 || url.host.length > 70) reasons += ReasonCode.LONG_URL
        if (url.host in SHORTENERS || registered in SHORTENERS) reasons += ReasonCode.SHORTENER
        if (credentials.containsMatchIn(url.path + "?" + url.query)) reasons += ReasonCode.CREDENTIAL_PATH
        if (url.userInfo) reasons += ReasonCode.USER_INFO
        if (BRANDS.any { (brand, official) ->
                registered != official && subdomain.split('.', '-').any { it == brand }
            }) reasons += ReasonCode.BRAND_IN_SUBDOMAIN
        val label = IDN.toUnicode(registered.substringBefore('.')).lowercase(Locale.ROOT)
        val skeleton = label.map { skeletonMap[it] ?: it }.joinToString("")
        if (BRANDS.any { (brand, official) ->
                brand.length >= 4 && registered != official && label != brand &&
                    (skeleton == brand || oneEditAway(skeleton, brand))
            }) reasons += ReasonCode.LOOKALIKE
        return reasons
    }

    fun extract(url: ParsedUrl): DoubleArray {
        val flags = flags(url)
        val digits = url.raw.count { it in '0'..'9' }
        val registered = suffixes.registrableDomain(url.host)
        val subdomains = if (registered == null) 0 else url.host.split('.').size - registered.split('.').size
        val entropy = url.host.groupingBy { it }.eachCount().values.sumOf { count ->
            val probability = count.toDouble() / url.host.length
            -probability * ln(probability) / ln(2.0)
        }
        fun has(reason: ReasonCode) = if (reason in flags) 1.0 else 0.0
        return doubleArrayOf(
            url.raw.length.toDouble(), url.host.length.toDouble(), url.path.length.toDouble(),
            url.query.length.toDouble(), url.host.count { it == '.' }.toDouble(),
            url.host.count { it == '-' }.toDouble(), url.raw.count { it == '@' }.toDouble(),
            url.raw.count { it == '%' }.toDouble(), digits.toDouble(), digits.toDouble() / url.raw.length,
            url.path.split('/').count { it.isNotEmpty() }.toDouble(),
            if (url.query.isEmpty()) 0.0 else url.query.count { it == '&' } + 1.0,
            if (url.https) 1.0 else 0.0, if (url.ip) 1.0 else 0.0,
            has(ReasonCode.PUNYCODE), has(ReasonCode.SHORTENER), has(ReasonCode.RISKY_TLD),
            has(ReasonCode.CREDENTIAL_PATH), has(ReasonCode.BRAND_IN_SUBDOMAIN), has(ReasonCode.LOOKALIKE),
            subdomains.toDouble(), entropy, if (url.raw.any { it.code > 127 }) 1.0 else 0.0,
            if (url.port >= 0) 1.0 else 0.0, if (url.userInfo) 1.0 else 0.0
        ).map { it.toFloat().toDouble() }.toDoubleArray()
    }
}
