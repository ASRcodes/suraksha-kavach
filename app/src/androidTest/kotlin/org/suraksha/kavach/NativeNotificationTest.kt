package org.suraksha.kavach

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.suraksha.core.Level

/** The host sends one virtual SMS after readiness. No real number is contacted. */
@RunWith(AndroidJUnit4::class)
class NativeNotificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun virtualSmsNotificationReachesTheActualListenerAndRedScreen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("native_sms") == "true")
        val app = compose.activity.application as KavachTestApplication
        runBlocking { withTimeout(60_000) { while (app.modelState.value == LoadState.LOADING || !app.listenerConnected.value) delay(100) } }
        app.setDemo(false); app.settings.setProtection(true); app.settings.setLanguage("en")
        val started = System.currentTimeMillis()
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "READY_FOR_EMULATED_SMS\n") })
        compose.waitUntil(60_000) { app.history.value.any { it.timestamp >= started && it.level == Level.DANGEROUS.name } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.alert_title)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.alert_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.understand)).performClick()
        assertTrue(app.sms.isEmpty())
    }
}
