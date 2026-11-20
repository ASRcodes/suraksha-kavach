package org.suraksha.kavach

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.suraksha.core.*
import org.suraksha.kavach.audio.WarningAudio
import org.suraksha.kavach.data.SecretStore
import org.suraksha.kavach.family.*
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhaseThreeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as KavachTestApplication

    @Test fun guardianRequiresConsentAndPermissionAndPersistsRateLimit() = runBlocking {
        val prefix = "test_" + UUID.randomUUID().toString() + "_"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(prefix + name, mode)
        }
        val secrets = SecretStore(context)
        val attempts = mutableListOf<Triple<String, String, Long>>()
        val sender = SmsSender { number, message, attempt -> attempts += Triple(number, message, attempt) }
        var permitted = false
        var time = 10_000_000L
        fun manager() = GuardianManager(context, secrets, sender, { permitted }, { time })
        val danger = ScanResult("private-domain.example", Verdict(Level.DANGEROUS, 1.0, setOf(ReasonCode.URL_MODEL)), time)
        try {
            var guardian = manager()
            assertTrue(guardian.configure("+1 202 555 0142", false))
            guardian.onDanger(danger)
            assertTrue(attempts.isEmpty())
            guardian.setEnabled(true)
            guardian.onDanger(danger)
            assertEquals(GuardianStatus.NEEDS_PERMISSION, guardian.state.value.status)
            assertTrue(attempts.isEmpty())
            permitted = true
            guardian.onDanger(danger.copy(verdict = Verdict(Level.DANGEROUS, 1.0, setOf(ReasonCode.DEMO_ONLY))))
            guardian.onDanger(danger.copy(verdict = Verdict(Level.SUSPICIOUS, 0.5, setOf(ReasonCode.SHORTENER))))
            assertTrue(attempts.isEmpty())
            guardian.onDanger(danger)
            assertEquals(1, attempts.size)
            assertEquals("+12025550142", attempts.single().first)
            assertEquals(GuardianManager.MESSAGE, attempts.single().second)
            assertFalse(attempts.single().second.contains(danger.domain))
            guardian.recordResult(time, true)
            assertEquals(GuardianStatus.SENT, guardian.state.value.status)
            guardian = manager()
            time -= 1
            guardian.onDanger(danger)
            assertEquals(1, attempts.size)
            time += GuardianPolicy.WINDOW_MS
            guardian.onDanger(danger)
            assertEquals(1, attempts.size)
            time += 1
            guardian.onDanger(danger)
            assertEquals(2, attempts.size)
            assertFalse(context.getSharedPreferences("sealed_secrets", 0).all.toString().contains("2025550142"))
        } finally {
            app.deleteSharedPreferences(prefix + "guardian_settings")
            app.deleteSharedPreferences(prefix + "sealed_secrets")
        }
    }

    @Test fun allBundledWarningsDecodeWithoutAnOnlineTtsEngine() {
        for (language in listOf("en", "hi", "pa")) {
            app.resources.openRawResourceFd(WarningAudio.resource(language)).use { fd ->
                val media = MediaMetadataRetriever()
                try {
                    media.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    val duration = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    assertTrue("$language duration", duration in 3_000..30_000)
                    assertEquals("yes", media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                } finally { media.release() }
            }
        }
    }

    @Test fun authorizedOverlayShowsTheWarningAndDismisses() {
        assertTrue("Grant SYSTEM_ALERT_WINDOW only on the isolated test emulator", Settings.canDrawOverlays(app))
        val result = ScanResult("overlay-test.example", Verdict(Level.DANGEROUS, 1.0, setOf(ReasonCode.URL_MODEL)), System.currentTimeMillis())
        compose.runOnIdle {
            app.settings.setOverlay(true)
            assertTrue(app.overlays.show(result))
        }
        try {
            compose.onNodeWithText(compose.activity.getString(R.string.alert_title)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.understand)).assertIsDisplayed()
            compose.waitForIdle()
            var captured: Bitmap? = null
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            for (attempt in 0 until 30) {
                val frame = automation.takeScreenshot()
                if (frame != null) {
                    val pixel = frame.getPixel(10, frame.height / 2)
                    if (android.graphics.Color.red(pixel) > 100 && android.graphics.Color.red(pixel) > 2 * android.graphics.Color.green(pixel)) {
                        captured = frame
                        break
                    }
                    frame.recycle()
                }
                android.os.SystemClock.sleep(100)
            }
            val bitmap = requireNotNull(captured) { "The overlay must be drawn above the app, not only present in semantics" }
            File(app.cacheDir, "phase3-overlay.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            compose.onNodeWithText(compose.activity.getString(R.string.understand)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText(compose.activity.getString(R.string.alert_title)).assertDoesNotExist()
        } finally {
            compose.runOnIdle { app.overlays.dismiss(); app.settings.setOverlay(false) }
        }
    }
}
