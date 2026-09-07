package com.streammate.tv.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.core.database.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAlertDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun watchAndNotNowEachAnswerTheAlert() {
        val answers = mutableListOf<String>()
        val now = 1_000_000L
        composeRule.setContent {
            StreamMateTheme {
                ReminderAlertDialog(
                    reminder = ReminderEntity("programme:c:1", ReminderEntity.KIND_PROGRAMME, null, "c", "Kick-off", "Channel One", now + 60_000L, now),
                    now = now,
                    onWatch = { answers += "watch" },
                    onDismiss = { answers += "later" },
                )
            }
        }
        composeRule.onNodeWithText("Kick-off starts in a minute").assertIsDisplayed()
        composeRule.onNodeWithText("Channel One").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-alert-watch").performClick()
        composeRule.onNodeWithTag("reminder-alert-later").performClick()
        assertEquals(listOf("watch", "later"), answers)
    }

    @Test
    fun theOverlayPromptOffersTheSettingsAndNotNow() {
        val answers = mutableListOf<String>()
        composeRule.setContent {
            StreamMateTheme {
                ReminderOverlayPromptDialog(onOpenSettings = { answers += "settings" }, onDismiss = { answers += "later" })
            }
        }
        composeRule.onNodeWithText("Let reminders open Sohva TV").assertIsDisplayed()
        composeRule.onNodeWithTag("reminder-overlay-open").performClick()
        composeRule.onNodeWithTag("reminder-overlay-later").performClick()
        assertEquals(listOf("settings", "later"), answers)
    }
}
