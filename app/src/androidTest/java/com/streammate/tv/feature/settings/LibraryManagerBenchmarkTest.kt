package com.streammate.tv.feature.settings

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.*
import com.streammate.tv.iptv.repository.ManagedLibrary
import com.streammate.tv.iptv.repository.OrganizationReadState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long the channel manager takes to move focus from one group to the
 * next on a large library, with some groups and channels hidden the way a
 * tester leaves them. A tester on a slow Google TV dongle waited one to two
 * seconds per group; the numbers here are relative, not that device's.
 */
@RunWith(AndroidJUnit4::class)
class LibraryManagerBenchmarkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingThroughTheGroupsOfALargeLibrary() = movingThroughTheGroups(groups = 40, perGroup = 60, label = "large")

    /** The same moves on a tiny library: the emulator's own cost per move, to read the large run against. */
    @Test fun movingThroughTheGroupsOfATinyLibrary() = movingThroughTheGroups(groups = 40, perGroup = 2, label = "tiny")

    private fun movingThroughTheGroups(groups: Int, perGroup: Int, label: String) {
        val items = (0 until groups).flatMap { g ->
            (0 until perGroup).map { c ->
                OrganizationItem(
                    id = "source:$g:$c", sourceId = "source", title = "Channel $g/$c",
                    groupName = "Finland - Group ${g.toString().padStart(2, '0')}", groupKey = "id:$g",
                    imageUrl = null, providerOrder = g * perGroup + c,
                )
            }
        }
        // Every third group hidden whole, every fourth channel hidden singly.
        val rules = buildList {
            for (g in 0 until groups step 3) add(OrganizationRule(OrganizationKey(LibraryRoom.LIVE, "source", "id:$g"), enabled = false))
            items.filterIndexed { index, _ -> index % 4 == 0 }.forEach { add(OrganizationRule(OrganizationKey(LibraryRoom.LIVE, "source", it.groupKey, it.id), enabled = false)) }
        }
        compose.setContent {
            StreamMateTheme {
                LibraryManagerContent(
                    room = LibraryRoom.LIVE,
                    library = ManagedLibrary(items, mapOf("source" to "Provider"), OrganizationReadState(LibraryOrganization(rules))),
                    initialGroup = "Finland - Group 00", onRoom = {}, onBack = {}, onChange = {},
                )
            }
        }
        fun tag(g: Int) = "manager-group-" + organizationGroupKey("Finland - Group ${g.toString().padStart(2, '0')}")
        // The groups are counted off the main thread, which the idle wait does not cover.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag(0)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag(0)).assertIsFocused()
        compose.waitForIdle()
        val timings = mutableListOf<Long>()
        repeat(12) { step ->
            val started = System.nanoTime()
            compose.onNodeWithTag(tag(step)).performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            compose.onNodeWithTag(tag(step + 1)).assertIsFocused()
            timings += (System.nanoTime() - started) / 1_000_000
        }
        Log.i("LibraryManagerBenchmark", "$label library, group moves ms: $timings (median ${timings.sorted()[timings.size / 2]})")
        assertTrue(timings.isNotEmpty())
    }
}
