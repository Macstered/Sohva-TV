package com.streammate.tv.feature.settings

import com.streammate.tv.testing.awaitUntil
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ParentalPinScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun correctPinUnlocksChannel() {
        var unlocked = false
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                StreamMateTheme {
                    ParentalPinScreen(
                        channelName = "Lukittu kanava",
                        pinConfigured = true,
                        onVerify = { it == "2468" },
                        onUnlocked = { unlocked = true },
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("parental-pin").assertIsDisplayed().performTextInput("2468")
        composeRule.onNodeWithTag("parental-unlock").performClick()

        composeRule.awaitUntil(timeoutMillis = 10_000) { unlocked }
        composeRule.runOnIdle { assertTrue(unlocked) }
    }
}
