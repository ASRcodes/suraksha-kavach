package org.suraksha.kavach

import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.suraksha.core.*
import org.suraksha.kavach.data.AppSettings
import org.suraksha.kavach.data.ModelStore
import org.suraksha.kavach.notifications.AlertDispatcher
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhaseFiveDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as KavachTestApplication
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun ready() = runBlocking {
        withTimeout(60_000) { while (app.modelState.value == LoadState.LOADING || app.storageState.value == LoadState.LOADING) delay(100) }
        assertEquals(LoadState.READY, app.modelState.value)
        assertEquals(LoadState.READY, app.storageState.value)
        app.setDemo(false); app.settings.finishSetup(); app.settings.setProtection(true); app.settings.setLanguage("en"); app.alerts.cancel()
    }

    @Test fun androidScoresMatchAllFiftyPythonRowsAndRecordLatency() = runBlocking {
        ready()
        val rows = JSONObject(instrumentation.context.assets.open("score_samples.json").bufferedReader().use { it.readText() }).getJSONArray("samples")
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        val features = UrlFeatures(PublicSuffixList(asset("public_suffix_list.dat")))
        val url = UrlModel(asset("url_model.json"))
        val text = TextModel(asset("text_model.json"))
        assertEquals(50, rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val vector = features.extract(requireNotNull(UrlParser.parse(row.getString("url"))))
            val expected = row.getJSONArray("features")
            assertArrayEquals(DoubleArray(expected.length()) { expected.getDouble(it) }, vector, 1e-6)
            assertEquals(row.getDouble("url_score"), url.score(vector), 1e-10)
            assertEquals(row.getDouble("text_score"), text.score(row.getString("message")), 1e-10)
        }
        val loadedAt = System.nanoTime()
        val engine = ModelStore(app).load()
        val loadMs = (System.nanoTime() - loadedAt) / 1_000_000.0
        repeat(10) { val row = rows.getJSONObject(it); engine.evaluate(row.getString("url"), row.getString("message")) }
        val timings = (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            val start = System.nanoTime()
            engine.evaluate(row.getString("url"), row.getString("message"))
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val report = JSONObject().put("platform", "android").put("api", Build.VERSION.SDK_INT)
            .put("model", Build.MODEL).put("emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
            .put("samples", 50).put("parity_tolerance", 1e-10).put("all_scores_match", true)
            .put("model_load_ms", loadMs).put("median_ms", timings[25]).put("p95_ms", timings[47])
            .put("max_ms", timings.last()).put("p95_under_200_ms", timings[47] < 200)
        File(app.cacheDir, "android-latency.json").writeText(report.toString(2))
    }

    @Test fun pausedProtectionDeduplicationAndNotificationLevelsStayConsistent() = runBlocking {
        ready()
        val marker = "dedup-" + System.nanoTime()
        val message = "A normal link: https://youtube.com/watch?v=family"
        val first = app.processMessage(message, marker)
        assertTrue(first.results.isNotEmpty())
        assertTrue(first.results.all { it.verdict.level == Level.SAFE })
        assertTrue(app.processMessage(message, marker).results.isEmpty())
        try {
            app.settings.setProtection(false)
            val samples = JSONObject(app.assets.open("demo_samples.json").bufferedReader().use { it.readText() }).getJSONArray("samples")
            val danger = (0 until samples.length()).map { samples.getJSONObject(it) }.first { it.getString("kind") == "danger" }
            assertTrue(app.processMessage(danger.getString("message"), marker).results.isEmpty())
            assertTrue(app.processMessage("https://google.com", marker, simulate = true).results.all { it.verdict.level == Level.SAFE })
            val manager = app.getSystemService(NotificationManager::class.java)
            app.alerts.cancel()
            app.alerts.post(ScanResult("example.org", Verdict(Level.SAFE, 0.0, emptySet()), 1))
            assertTrue(manager.activeNotifications.none { it.id == AlertDispatcher.ID || it.id == AlertDispatcher.CAUTION_ID })
            app.alerts.post(ScanResult("example.org", Verdict(Level.SUSPICIOUS, 0.5, setOf(ReasonCode.SHORTENER)), 1), false)
            withTimeout(5_000) { while (manager.activeNotifications.none { it.id == AlertDispatcher.CAUTION_ID }) delay(100) }
            assertEquals(AlertDispatcher.CAUTION_CHANNEL, manager.activeNotifications.single { it.id == AlertDispatcher.CAUTION_ID }.notification.channelId)
        } finally { app.alerts.cancel(); app.settings.setProtection(true) }
    }

    @Test fun hindiAndPunjabiWarningsKeepBothActionsVisible() {
        ready()
        val samples = JSONObject(app.assets.open("demo_samples.json").bufferedReader().use { it.readText() }).getJSONArray("samples")
        try {
            for (language in listOf("hi", "pa")) {
                val sample = (0 until samples.length()).map { samples.getJSONObject(it) }
                    .first { it.getString("kind") == "danger" && it.getString("language") == language }
                val result = runBlocking { app.processMessage(sample.getString("message"), "language-ui", true).results.maxBy { it.verdict.level.ordinal } }
                assertEquals(Level.DANGEROUS, result.verdict.level)
                compose.runOnIdle { app.settings.setLanguage(language); compose.activity.startActivity(AlertActivity.intent(compose.activity, result)) }
                val localized = AppSettings.localized(app)
                val title = localized.getString(R.string.alert_title)
                compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(title).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.call_guardian)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.understand)).assertIsDisplayed()
                captureRed("warning-$language")
                compose.onNodeWithText(localized.getString(R.string.replay_warning)).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.call_guardian)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.understand)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.understand)).performClick()
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<AlertActivity>().forEach { it.finish() }
                app.settings.setLanguage("en")
            }
        }
    }

    private fun captureRed(name: String) {
        compose.waitForIdle()
        var image: Bitmap? = null
        repeat(30) {
            if (image == null) {
                val frame = instrumentation.uiAutomation.takeScreenshot()
                if (frame != null) {
                    val pixel = frame.getPixel(10, frame.height / 2)
                    if (android.graphics.Color.red(pixel) > 100 && android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) * 2) image = frame else frame.recycle()
                }
                if (image == null) android.os.SystemClock.sleep(100)
            }
        }
        val bitmap = requireNotNull(image)
        val scale = app.resources.configuration.fontScale.toString().replace('.', '_')
        File(app.cacheDir, "$name-font-$scale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
