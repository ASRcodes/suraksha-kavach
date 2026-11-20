package org.suraksha.core

import org.junit.Assert.*
import org.junit.Test

class PhaseZeroTest {
    @Test fun extractsUnicodeSurroundingTextAndBareDomains() {
        assertEquals(listOf("https://example.org/help", "https://example.net"),
            UrlExtractor.extract("अभी example.org/help खोलें। ਹੁਣ https://example.net।"))
    }
    @Test fun ignoresEmailAddressesAndEmptyText() {
        assertTrue(UrlExtractor.extract("hello person@example.org").isEmpty())
        assertTrue(UrlExtractor.extract("").isEmpty())
    }
    @Test fun keepsDeceptiveFullHostForLaterScoring() {
        assertEquals("https://youtube.com.evil.xyz/login", UrlExtractor.extract("youtube.com.evil.xyz/login").single())
    }
    @Test fun demoHasNoSpecialCaseForAnyDomain() {
        val pipeline = MessagePipeline(DemoScorer())
        listOf("google.com", "example.org", "unrelated.example").forEach {
            val verdict = pipeline.scan(it, "test", false).single().verdict
            assertEquals(Level.DANGEROUS, verdict.level)
            assertEquals(setOf(ReasonCode.DEMO_ONLY), verdict.reasonCodes)
        }
    }
    @Test fun repeatsExpireAndCacheCanBeCleared() {
        var time = 10L
        val pipeline = MessagePipeline(DemoScorer()) { time }
        assertEquals(1, pipeline.scan("example.org", "sms").size)
        assertTrue(pipeline.scan("example.org", "sms").isEmpty())
        time += 60_000L
        assertEquals(1, pipeline.scan("example.org", "sms").size)
        pipeline.clear()
        assertEquals(1, pipeline.scan("example.org", "sms").size)
    }
    @Test fun resultContainsOnlyAllowedMetadata() {
        val result = MessagePipeline(DemoScorer()).scan("private message https://example.org/secret", "test").single()
        assertEquals("example.org", result.domain)
        assertFalse(result.toString().contains("private message"))
        assertFalse(result.toString().contains("secret"))
    }
}
