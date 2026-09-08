package com.streammate.tv.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePickerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyProfileIsATileAndTheActiveOneIsFocusedFirst() {
        val chosen = mutableListOf<String>()
        composeRule.setContent {
            StreamMateTheme {
                ProfilePickerScreen(
                    profiles = listOf(Profile("p1", "Kids", 2)),
                    activeProfileId = "p1",
                    onChoose = { chosen += it.id },
                )
            }
        }
        composeRule.onNodeWithText("Who is watching?").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-tile-default").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-tile-p1").assertIsFocused()
        composeRule.onNodeWithTag("profile-tile-default").performClick()
        assertEquals(listOf("default"), chosen)
    }
}
