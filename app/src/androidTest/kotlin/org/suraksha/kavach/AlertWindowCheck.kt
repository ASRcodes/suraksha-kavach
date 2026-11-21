package org.suraksha.kavach

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

/** Semantics can remain present underneath a system dialog or during a handoff. */
internal fun ComposeTestRule.waitForFocusedAlert(timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) {
        var focused = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            focused = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<AlertActivity>()
                .any { it.window.decorView.hasWindowFocus() }
        }
        focused
    }
}
