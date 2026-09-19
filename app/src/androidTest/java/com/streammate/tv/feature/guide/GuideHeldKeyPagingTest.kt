package com.streammate.tv.feature.guide

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.TvProgrammeEntity
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.testing.awaitFocused
import com.streammate.tv.testing.awaitUntil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Repeated D-pad events must not escape the timeline while a time page is loading. */
class GuideHeldKeyPagingTest {
    private val composeRule = createComposeRule()
    private lateinit var database: StreamMateDatabase
    private lateinit var repository: GuideRepository
    private var firstStart = 0L
    private var initialWindowStart = 0L
    @Volatile private var queryGate: CountDownLatch? = null
    @Volatile private var queryStarted: CountDownLatch? = null
    private val programmeReads = AtomicInteger()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            queryGate?.countDown()
            if (::database.isInitialized) database.close()
        }
    }).around(composeRule)

    @Before
    fun createGuide() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if ("p.programmeId AS programmeId" in sql) {
                    programmeReads.incrementAndGet()
                    queryGate?.let { gate ->
                        queryStarted?.countDown()
                        check(gate.await(15, TimeUnit.SECONDS)) { "Test did not release the EPG read" }
                    }
                }
            }, { it.run() })
            .build()
        repository = GuideRepository(database.guideDao())
        val now = System.currentTimeMillis()
        initialWindowStart = GuideTimeWindow.nowStart(now)
        firstStart = initialWindowStart - SLOTS_BEHIND * SLOT_MILLIS
        val dao = database.guideDao()
        dao.upsertSourceState(IptvSourceStateEntity("test", "Test source", "M3U", true, 1, 0, now))
        dao.upsertChannels((1..CHANNELS).map { index ->
            IptvChannelEntity(
                sourceId = "test", snapshotId = "playlist", channelId = "test:$index",
                tvgId = "channel$index.example", name = "Channel $index", normalizedName = "channel $index",
                groupTitle = "Group", logoUrl = null, encryptedStreamUrl = "encrypted", userAgent = null,
                referrer = null, lastSeenEpochMillis = now, playlistOrder = index,
            )
        })
        dao.activatePlaylistSnapshot("test", "playlist", CHANNELS, now)
        (1..CHANNELS).forEach { index ->
            dao.upsertProgrammes((0 until SLOTS).map { slot ->
                TvProgrammeEntity(
                    sourceId = "test", snapshotId = "epg", programmeId = "c$index-s$slot",
                    xmltvChannelId = "channel$index.example",
                    startEpochMillis = firstStart + slot * SLOT_MILLIS,
                    stopEpochMillis = firstStart + (slot + 1) * SLOT_MILLIS,
                    title = "Slot $slot", subtitle = null, description = null, categories = "News",
                )
            })
        }
        dao.activateEpgSnapshot("test", "epg", CHANNELS * SLOTS, now)
    }

    @Test
    fun rightHeldDuringSlowPagesReturnsToTimelineAndCanPageAgain() = held(forward = true)

    @Test
    fun leftHeldDuringSlowPagesReturnsToTimelineAndCanPageAgain() = held(forward = false)

    private fun showGuide() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppPreferencesRepository(context)
        val metadata = MetadataRepository(database.metadataDao(), SecretSettingsStore(context, PlainCipher), OkHttpClient())
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = repository,
                    preferencesRepository = preferences,
                    metadataRepository = metadata,
                    onBack = {}, onSettings = {}, onChannels = {}, onPlay = {}, onPlayCatchup = { _, _, _ -> },
                )
            }
        }
        composeRule.awaitFocused("guide-channel-test:1")
        composeRule.awaitUntil { visibleSlots().isNotEmpty() }
    }

    private fun held(forward: Boolean) {
        showGuide()
        var windowStart = initialWindowStart
        if (!forward) {
            // Stay away from Now, where Left intentionally returns to the channel.
            composeRule.onNodeWithTag("guide-channel-list").performKeyInput { pressKey(Key.MediaNext) }
            windowStart += GuideTimeWindow.DAY_MILLIS
            composeRule.awaitFocused(programmeTag(slotAt(windowStart)))
        }
        val code = if (forward) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val boundary = if (forward) visibleSlots().max() else visibleSlots().min()
        composeRule.onNodeWithTag(programmeTag(boundary))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        composeRule.awaitFocused(programmeTag(boundary))
        val downTime = SystemClock.uptimeMillis()
        var repeatCount = 0
        try {
            repeat(4) {
                val gate = CountDownLatch(1)
                val started = CountDownLatch(1)
                queryStarted = started
                queryGate = gate
                val readsBefore = programmeReads.get()
                try {
                    // Six half-hour cells fit a page. Walk to its edge without
                    // releasing the button or assigning focus between pages.
                    var presses = 0
                    while (started.count != 0L && presses++ < 8) {
                        sendKey(downTime, code, KeyEvent.ACTION_DOWN, repeatCount++)
                    }
                    assertTrue("Destination read never started", started.await(5, TimeUnit.SECONDS))
                    composeRule.mainClock.advanceTimeBy(1_000)
                    // Keep the real repeat events coming during the slow read.
                    // Progress is measured in completed pages, not speed.
                    repeat(12) { sendKey(downTime, code, KeyEvent.ACTION_DOWN, repeatCount++) }
                    composeRule.onNodeWithTag("guide-channel-test:1").assertIsFocused()
                    assertEquals("Repeats started overlapping page reads", readsBefore + 1, programmeReads.get())
                } finally {
                    queryGate = null
                    queryStarted = null
                    gate.countDown()
                }
                windowStart += if (forward) GuideTimeWindow.PAGE_MILLIS else -GuideTimeWindow.PAGE_MILLIS
                val destination = if (forward) slotAt(windowStart) else slotAt(windowStart + WINDOW_MILLIS) - 1
                composeRule.awaitFocused(programmeTag(destination))
            }
        } finally {
            sendKey(downTime, code, KeyEvent.ACTION_UP, 0)
        }
        val before = focusedSlot()!!
        composeRule.onNodeWithTag("guide-channel-list").performKeyInput {
            pressKey(if (forward) Key.DirectionRight else Key.DirectionLeft)
        }
        composeRule.awaitFocused(programmeTag(before + if (forward) 1 else -1))
    }

    private fun sendKey(downTime: Long, code: Int, action: Int, repeat: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeySync(
            KeyEvent(downTime, SystemClock.uptimeMillis(), action, code, repeat),
        )
        composeRule.waitForIdle()
    }

    private fun visibleSlots(): List<Int> = composeRule.onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("First channel programme") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("guide-programme-c1-s") == true
        },
    ).fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag)?.substringAfter("guide-programme-c1-s")?.toIntOrNull() }

    private fun focusedSlot(): Int? = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)?.substringAfter("guide-programme-c1-s", "")?.toIntOrNull()

    private fun slotAt(time: Long): Int = ((time - firstStart) / SLOT_MILLIS).toInt()
    private fun programmeTag(slot: Int): String = "guide-programme-c1-s$slot"

    private object PlainCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }

    private companion object {
        const val CHANNELS = 12
        const val SLOT_MILLIS = 30 * 60_000L
        const val WINDOW_MILLIS = 3 * 60 * 60_000L
        const val SLOTS_BEHIND = 48
        const val SLOTS = 48 + 96
    }
}
