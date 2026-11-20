package org.suraksha.core

import org.junit.Assert.*
import org.junit.Test

class DomainSafetyTest {
    private val psl = PublicSuffixList("com\norg\nnet\nxyz\nin\nco.in\nco.uk\nio\ngithub.io\n*.ck\n!www.ck\n*.kawasaki.jp\n!city.kawasaki.jp")
    @Test fun usesRegistrableDomainWithPublicAndPrivateSuffixes() {
        assertEquals("bbc.co.uk", psl.registrableDomain("news.bbc.co.uk"))
        assertEquals("sbi.co.in", psl.registrableDomain("www.sbi.co.in"))
        assertEquals("tenant.github.io", psl.registrableDomain("tenant.github.io"))
        assertNull(psl.registrableDomain("github.io"))
        assertEquals("www.ck", psl.registrableDomain("www.ck"))
        assertEquals("a.b.ck", psl.registrableDomain("a.b.ck"))
        assertEquals("city.kawasaki.jp", psl.registrableDomain("a.city.kawasaki.jp"))
    }
    @Test fun allowlistCannotBeTrickedBySuffixOrUserInfo() {
        val allow = Allowlist("youtube.com\ngoogle.com\nsbi.co.in", psl)
        assertTrue(allow.contains("m.youtube.com"))
        assertFalse(allow.contains("youtube.com.evil.xyz"))
        assertFalse(allow.contains("notgoogle.com"))
        assertFalse(allow.contains(UrlParser.parse("https://google.com@evil.xyz")!!.host))
    }
    @Test fun blocklistHonorsLabelBoundaries() {
        val block = Blocklist("fraud.example\nbad.tenant.github.io")
        assertTrue(block.contains("x.fraud.example"))
        assertFalse(block.contains("notfraud.example"))
        assertFalse(block.contains("other.tenant.github.io"))
    }
    @Test fun parsesUnicodeAndIpsWithoutAnyDnsLookup() {
        assertEquals("xn--bcher-kva.de", UrlParser.parse("https://bücher.de")!!.host)
        assertTrue(UrlParser.parse("http://192.0.2.1/login")!!.ip)
        assertTrue(UrlParser.parse("https://[2001:db8::1]/")!!.ip)
        assertNull(UrlParser.parse("http://999.0.2.1"))
        assertNull(UrlParser.parse("javascript:alert(1)"))
        assertNull(UrlParser.parse("https://example.org:70000"))
    }
    @Test fun findsShortenersDeceptiveSubdomainsAndLookalikes() {
        val features = UrlFeatures(psl)
        assertTrue(ReasonCode.SHORTENER in features.flags(UrlParser.parse("https://bit.ly/a")!!))
        assertTrue(ReasonCode.BRAND_IN_SUBDOMAIN in features.flags(UrlParser.parse("https://youtube.com.evil.xyz")!!))
        assertTrue(ReasonCode.LOOKALIKE in features.flags(UrlParser.parse("https://g00gle.com")!!))
        assertTrue(ReasonCode.PUNYCODE in features.flags(UrlParser.parse("https://xn--pple-43d.com")!!))
    }
    @Test fun extractsIndicTextAndBalancedPunctuation() {
        assertEquals("https://google.com", UrlExtractor.extract("देखें:google.com।").single())
        assertEquals("https://example.org/wiki/Foo_(bar)", UrlExtractor.extract("(https://example.org/wiki/Foo_(bar)).").single())
        assertEquals("https://192.0.2.1/path", UrlExtractor.extract("ਹੁਣ 192.0.2.1/path ਖੋਲ੍ਹੋ").single())
        assertEquals("https://उदाहरण.भारत", UrlExtractor.extract("देखें उदाहरण.भारत").single())
    }
    @Test fun mixedScriptBareHostsAreNeverTruncatedIntoTrustedDomains() {
        val allow = Allowlist("google.com", psl)
        listOf("देखेंgoogle.com", "google.comਪਤਾ", "bücher.de", "аррӏe.com").forEach { input ->
            val extracted = UrlExtractor.extract(input).single()
            assertEquals("https://$input", extracted)
            val parsed = UrlParser.parse(extracted)
            // Rejection by IDNA is also conservative: an unreadable host cannot be trusted.
            assertFalse(parsed != null && allow.contains(parsed.host))
        }
    }

    @Test fun boundsAdversarialInputsAndLinkCount() {
        val many = (1..100).joinToString(" ") { "https://site$it.example/path" }
        assertEquals(24, UrlExtractor.extract(many).size)
        assertTrue(UrlExtractor.extract("x".repeat(100_000)).isEmpty())
    }
}
