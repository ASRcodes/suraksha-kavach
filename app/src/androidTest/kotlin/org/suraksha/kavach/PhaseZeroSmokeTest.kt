package org.suraksha.kavach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhaseZeroSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoMessageUsesSharedPipelineAndOpensRedAlert() {
        compose.activity.runOnUiThread { (compose.activity.application as KavachApplication).setDemo(true) }
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.simulate_message)).performScrollTo().performClick()
        compose.onNodeWithTag("messageInput").performTextInput("A harmless test: example.org")
        compose.onNodeWithText(compose.activity.getString(R.string.check_message)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.demo_badge)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.alert_title)).assertIsDisplayed()
        compose.onNodeWithText("example.org").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.understand)).performScrollTo().performClick()
        compose.activity.runOnUiThread { (compose.activity.application as KavachApplication).setDemo(false) }
    }
}
