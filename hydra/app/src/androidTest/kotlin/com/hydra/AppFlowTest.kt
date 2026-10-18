package com.hydra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Run on a device/emulator: ./gradlew :app:connectedDebugAndroidTest. Not executed in the authoring environment. */
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun invalidHeightShowsError() {
        rule.onNodeWithContentDescription("Profile").performClick()
        rule.onNodeWithText("Height (cm)").performTextInput("0")
        rule.onNodeWithText("Save profile").performScrollTo().performClick()
        rule.onNodeWithText("must be between", substring = true).assertExists()
    }

    @Test fun quickAddAppearsInLog() {
        rule.onNodeWithText("150").performClick() // quick-add (duplicate guard may ignore a repeat within 60 s)
        rule.onNodeWithContentDescription("Water Log").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("150 mL").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsShowsPrivacyNotice() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("All data stays on this phone", substring = true).assertExists()
    }
}
