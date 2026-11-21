package org.suraksha.core

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ModelParityTest {
    companion object {
        private fun resource(name: String) = requireNotNull(ModelParityTest::class.java.classLoader.getResourceAsStream(name)) {
            "Required exported asset missing: $name"
        }.bufferedReader().use { it.readText() }
        private val suffixes by lazy { PublicSuffixList(resource("public_suffix_list.dat")) }
        private val features by lazy { UrlFeatures(suffixes) }
        private val urlModel by lazy { UrlModel(resource("url_model.json")) }
        private val textModel by lazy { TextModel(resource("text_model.json")) }
        private val samples by lazy { Json.parseToJsonElement(resource("score_samples.json")).jsonObject.getValue("samples").jsonArray }
        private val engine by lazy { VerdictEngine.load(::resource) }
    }

    @Test fun allFiftyHeldOutRowsMatchPython() {
        assertEquals(50, samples.size)
        samples.forEachIndexed { index, item ->
            val row = item.jsonObject
            assertEquals("test", row.getValue("split").jsonPrimitive.content)
            val vector = features.extract(requireNotNull(UrlParser.parse(row.getValue("url").jsonPrimitive.content)))
            val expected = row.getValue("features").jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            assertArrayEquals("Feature vector at sample $index", expected, vector, 0.000001)
            assertEquals("URL score at sample $index", row.getValue("url_score").jsonPrimitive.double, urlModel.score(vector), 1e-10)
            assertEquals("Text score at sample $index", row.getValue("text_score").jsonPrimitive.double,
                textModel.score(row.getValue("message").jsonPrimitive.content), 1e-10)
        }
    }

    @Test fun heldOutUrlsWereNotInsertedIntoBlocklist() {
        val blocklist = Blocklist(resource("blocklist.txt"))
        samples.forEach { row ->
            val url = requireNotNull(UrlParser.parse(row.jsonObject.getValue("url").jsonPrimitive.content))
            assertFalse("Test host leaked into blocklist", blocklist.contains(url.host))
        }
    }

    @Test fun normalTrustedLinksAreSilentAndDeceptiveDomainsAreNotTrusted() {
        listOf("https://youtube.com/watch?v=hello", "https://www.google.com/search?q=weather",
            "https://en.wikipedia.org/wiki/India").forEach {
            assertEquals(Level.SAFE, engine.evaluate(it, "").level)
        }
        assertNotEquals(Level.SAFE, engine.evaluate("https://youtube.com.evil.xyz/verify", "").level)
        assertNotEquals(Level.SAFE, engine.evaluate("https://bit.ly/a", "").level)
    }

    @Test fun heldOutDemoMessagesRunThroughRealPipeline() {
        val demos = Json.parseToJsonElement(resource("demo_samples.json")).jsonObject.getValue("samples").jsonArray
        assertTrue(demos.size >= 7)
        val pipeline = MessagePipeline(engine)
        demos.forEach { item ->
            val row = item.jsonObject
            val results = pipeline.scan(row.getValue("message").jsonPrimitive.content, "test", false)
            val expected = Level.valueOf(row.getValue("expected_level").jsonPrimitive.content)
            if (expected == Level.SAFE) assertTrue(results.all { it.verdict.level == Level.SAFE })
            else assertTrue("Held-out demo did not produce the expected real verdict",
                results.any { it.verdict.level == expected })
        }
    }

    @Test fun malformedLinkCannotSuppressDangerousScamLanguage() {
        val row = samples.map { it.jsonObject }.first {
            it.getValue("text_score").jsonPrimitive.double >= textModel.dangerousThreshold
        }
        val message = TextModel.normalize(row.getValue("message").jsonPrimitive.content)
        val invalid = "https://example.com:70000"
        val textVerdict = engine.evaluateText(message)
        assertEquals(Level.DANGEROUS, textVerdict.level)
        val result = MessagePipeline(engine).scan("$message $invalid", "regression", false).single()
        assertEquals(Level.DANGEROUS, result.verdict.level)
        assertEquals(textVerdict.score, result.verdict.score, 1e-10)
        assertTrue(ReasonCode.SCAM_LANGUAGE in result.verdict.reasonCodes)
        assertTrue(ReasonCode.INVALID_URL in result.verdict.reasonCodes)
        assertEquals(Level.SUSPICIOUS, engine.evaluate(invalid, "").level)
        assertEquals(setOf(ReasonCode.INVALID_URL), engine.evaluate(invalid, "").reasonCodes)
    }

    @Test fun allReasonsHavePlainLanguageTranslations() {
        ReasonCode.entries.forEach { code ->
            listOf("en", "hi", "pa").forEach { language -> assertTrue(ReasonMessages.get(code, language).length > 10) }
        }
    }

    @Test fun recordJvmLatencyWithoutClaimingPhoneTiming() {
        val rows = samples.map { it.jsonObject }
        repeat(10) {
            val row = rows[it]
            engine.evaluate(row.getValue("url").jsonPrimitive.content, row.getValue("message").jsonPrimitive.content)
        }
        val timings = rows.map { row ->
            val start = System.nanoTime()
            engine.evaluate(row.getValue("url").jsonPrimitive.content, row.getValue("message").jsonPrimitive.content)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val median = timings[25]
        val p95 = timings[47]
        val report = """{"platform":"desktop-jvm","samples":50,"median_ms":$median,"p95_ms":$p95,"phone_verified":false}"""
        File("build/reports/core-latency.json").apply { parentFile.mkdirs(); writeText(report) }
        println("Core JVM inference: median $median ms, p95 $p95 ms. Phone timing pending.")
    }
}
