package com.streammate.tv.feature.settings

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.iptv.R
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.repository.ManagedLibrary
import com.streammate.tv.iptv.repository.OrganizationReadState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryManagerFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun togglingAChannelRetainsFocusAndLeftReturnsToGroup() {
        var changes = 0
        compose.setContent {
            var rules by remember { mutableStateOf(emptyList<OrganizationRule>()) }
            StreamMateTheme {
                LibraryManagerContent(
                    room = LibraryRoom.LIVE,
                    library = ManagedLibrary(listOf(OrganizationItem("a", "source", "A", "Sports"), OrganizationItem("b", "source", "B", "Sports")), mapOf("source" to "Provider"), OrganizationReadState(LibraryOrganization(rules))),
                    initialGroup = "Sports", onRoom = {}, onBack = {},
                    onChange = { writes ->
                        val next = rules.associateBy { it.key }.toMutableMap()
                        writes.forEach { write ->
                            val old = next[write.key] ?: OrganizationRule(write.key)
                            next[write.key] = old.copy(enabled = if (write.changeEnabled) write.enabled else old.enabled, sort = if (write.changeSort) write.sort else old.sort, position = if (write.changePosition) write.position else old.position)
                        }
                        rules = next.values.toList(); changes++
                    },
                )
            }
        }
        compose.onNodeWithTag("manager-group-name:sports").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("manager-item-a").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, changes)
        compose.onNodeWithTag("manager-item-a").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("manager-group-name:sports").assertIsFocused()
    }

    @Test fun backClosesActionDialogWithoutExitingManager() {
        var exits = 0
        compose.setContent { StreamMateTheme {
            LibraryManagerContent(LibraryRoom.LIVE, ManagedLibrary(listOf(OrganizationItem("a", "source", "A", "Sports"))), "Sports", onRoom = {}, onChange = {}, onBack = { exits++ })
        } }
        compose.onNodeWithTag("manager-group-name:sports").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.Back) }
        compose.waitForIdle()
        assertEquals(0, exits)
        compose.onNodeWithTag("manager-group-name:sports").assertIsFocused()
    }

    @Test fun failedSaveLeavesTheRowFocusedAndShowsRecoveryMessage() {
        compose.setContent { StreamMateTheme {
            LibraryManagerContent(LibraryRoom.LIVE, ManagedLibrary(listOf(OrganizationItem("a", "source", "A", "Sports"))), "Sports", onRoom = {}, onChange = { error("Synthetic write failure") }, onBack = {})
        } }
        compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag("manager-group-name:sports").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("manager-group-name:sports").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("manager-item-a").performKeyInput { pressKey(Key.DirectionCenter) }
        val message = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.manager_save_error)
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag("manager-item-a").assertIsFocused()
        compose.onNodeWithTag("manager-undo").assertDoesNotExist()
    }

    @Test fun bulkDisableRequiresConfirmationAndBackDoesNotWrite() {
        var writes = 0
        compose.setContent { StreamMateTheme {
            LibraryManagerContent(LibraryRoom.LIVE, ManagedLibrary(listOf(OrganizationItem("a", "source", "A", "Sports"), OrganizationItem("b", "source", "B", "Sports"))), "Sports", onRoom = {}, onChange = { writes++ }, onBack = {})
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag("manager-group-name:sports").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("manager-group-name:sports").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("manager-bulk").performClick()
        compose.onNodeWithText(context.getString(R.string.manager_disable_selected)).performClick()
        assertEquals(0, writes)
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.Back) }
        assertEquals(0, writes)
        compose.onNodeWithTag("manager-item-a").assertIsFocused()
    }

    @Test fun manualMoveCancelsWithoutWritingAndPlacesWithOk() {
        var written = emptyList<com.streammate.tv.core.database.OrganizationChange>()
        compose.setContent { StreamMateTheme {
            LibraryManagerContent(LibraryRoom.LIVE, ManagedLibrary((1..12).map { OrganizationItem("c$it", "source", "Channel ${it.toString().padStart(2, '0')}", "Sports", providerOrder = it) }), "Sports", onRoom = {}, onChange = { written = it }, onBack = {})
        } }
        val moveLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.manager_move)
        compose.onNodeWithTag("manager-group-name:sports").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("manager-item-c1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText(moveLabel).performClick()
        compose.onNodeWithTag("manager-item-c1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Back) }
        assertTrue(written.isEmpty())
        compose.onNodeWithTag("manager-item-c1").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText(moveLabel).performClick()
        compose.onNodeWithTag("manager-item-c1").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1L, written.first { it.key.itemKey == "c1" }.position)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir, "manager-preview.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
