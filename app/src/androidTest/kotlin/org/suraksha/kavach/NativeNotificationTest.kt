package org.suraksha.kavach

import android.app.KeyguardManager
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.suraksha.core.Level
import org.suraksha.kavach.notifications.AlertDispatcher

/** The host sends one virtual SMS after readiness. No real number is contacted. */
@RunWith(AndroidJUnit4::class)
class NativeNotificationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun virtualSmsNotificationReachesTheActualListenerAndRedScreen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("native_sms") == "true")
        val app = compose.activity.application as KavachTestApplication
        runBlocking { withTimeout(60_000) { while (app.modelState.value == LoadState.LOADING || !app.listenerConnected.value) delay(100) } }
        app.setDemo(false); app.settings.setProtection(true); app.settings.setLanguage("en")
        val locked = InstrumentationRegistry.getArguments().getString("native_locked") == "true"
        val previous = app.settings.state.value
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val notifications = app.getSystemService(NotificationManager::class.java)
        fun status(message: String) = instrumentation.sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
        try {
            app.settings.setOverlay(false)
            app.settings.setVoice(true)
            compose.runOnIdle { app.overlays.dismiss(); app.alerts.cancel() }
            if (locked) {
                val keyguard = app.getSystemService(KeyguardManager::class.java)
                assertFalse("Use a dedicated emulator without a PIN", keyguard.isKeyguardSecure)
                status("READY_TO_LOCK_SCREEN")
                compose.waitUntil(60_000) {
                    var noResumedActivity = false
                    instrumentation.runOnMainSync {
                        noResumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED).isEmpty()
                    }
                    keyguard.isKeyguardLocked && noResumedActivity
                }
                if (Build.VERSION.SDK_INT >= 34) assertTrue(notifications.canUseFullScreenIntent())
                status("LOCKED_BACKGROUND_CONFIRMED")
            }
            val started = System.currentTimeMillis()
            status("READY_FOR_EMULATED_SMS")
            compose.waitUntil(60_000) { app.history.value.any { it.timestamp >= started && it.level == Level.DANGEROUS.name } }
            compose.waitForFocusedAlert()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.alert_title)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.alert_title)).assertIsDisplayed()
            if (locked) {
                assertTrue("The warning must have a real full-screen notification", notifications.activeNotifications
                    .any { it.id == AlertDispatcher.ID && it.notification.fullScreenIntent != null })
                status("LOCKED_FULL_SCREEN_WARNING_VISIBLE")
            }
            compose.onNodeWithText(compose.activity.getString(R.string.understand)).performClick()
            assertTrue(app.sms.isEmpty())
        } finally {
            app.settings.setOverlay(previous.overlayEnabled)
            app.settings.setVoice(previous.voiceEnabled)
        }
    }
}
