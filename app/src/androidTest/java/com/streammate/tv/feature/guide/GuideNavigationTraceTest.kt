package com.streammate.tv.feature.guide

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performKeyInput
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
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * The owner's way of browsing the guide, as a script: down a long list a row
 * at a time, along a row, and back up. Run while a system trace records, the
 * guide's named sections (`Guide:Screen`, `Guide:Row`, `Guide:ProgrammeCell`
 * and the rest) say what a key press costs and where, without a person or a
 * television; the two markers this writes, `GuideNavigation:vertical` and
 * `GuideNavigation:horizontal`, say which presses a stretch of trace belongs
 * to. Its own timings go to the log. An emulator's figures are not a Shield's,
 * and a debug build's are not a release's: read them against each other.
 */
class GuideNavigationTraceTest {
    private val composeRule = createComposeRule()
    private lateinit var database: StreamMateDatabase

    // Compose must dispose its Room collectors before their database is closed.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            if (::database.isInitialized) database.close()
        }
    }).around(composeRule)

    @Before
    fun createGuide() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        val now = System.currentTimeMillis()
        val dao = database.guideDao()
        dao.upsertSourceState(IptvSourceStateEntity("test", "Test source", "M3U", true, 1, 0, now))
        dao.upsertChannels((1..CHANNELS).map { index ->
            IptvChannelEntity(
                sourceId = "test",
                snapshotId = "playlist",
                channelId = "test:" + index.toString().padStart(4, '0'),
                tvgId = "channel$index.example",
                name = "Channel number $index HD",
                normalizedName = "channel number $index hd",
                groupTitle = "Group ${index % 12}",
                logoUrl = null,
                encryptedStreamUrl = "encrypted",
                userAgent = null,
                referrer = null,
                lastSeenEpochMillis = now,
                playlistOrder = index,
            )
        })
        dao.activatePlaylistSnapshot("test", "playlist", CHANNELS, now)
        // A listing every forty minutes, so a row carries five or six blocks as a real one does.
        val firstStart = now - now % SLOT_MILLIS - 2 * SLOT_MILLIS
        (1..CHANNELS).chunked(200).forEach { chunk ->
            dao.upsertProgrammes(chunk.flatMap { index ->
                (0 until SLOTS).map { slot ->
                    TvProgrammeEntity(
                        sourceId = "test",
                        snapshotId = "epg",
                        programmeId = "p$index-$slot",
                        xmltvChannelId = "channel$index.example",
                        startEpochMillis = firstStart + slot * SLOT_MILLIS,
                        stopEpochMillis = firstStart + (slot + 1) * SLOT_MILLIS,
                        title = "Programme $slot of channel $index",
                        subtitle = null,
                        description = "What happens in programme $slot of channel $index, at some length, as a listing tends to say it.",
                        categories = if (slot % 2 == 0) "News" else "Sport",
                    )
                }
            })
        }
        dao.activateEpgSnapshot("test", "epg", CHANNELS * SLOTS, now)
    }

    @Test
    fun browseDownAlongAndBackUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(context),
                    metadataRepository = MetadataRepository(database.metadataDao(), SecretSettingsStore(context, PlainCipher), OkHttpClient()),
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = {},
                    onPlayCatchup = { _, _, _ -> },
                )
            }
        }
        composeRule.awaitFocused("guide-channel-test:0001")

        fun press(key: Key, times: Int) = repeat(times) {
            composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
            composeRule.waitForIdle()
        }
        fun timed(marker: String, presses: Int, block: () -> Unit) {
            Trace.beginSection(marker)
            val started = SystemClock.elapsedRealtime()
            block()
            val millis = SystemClock.elapsedRealtime() - started
            Trace.endSection()
            Log.i(TAG, "$marker: $presses presses in $millis ms, ${millis / presses} ms a press")
            println("GUIDE-NAVIGATION $marker presses=$presses millis=$millis")
        }

        press(Key.DirectionDown, 12) // Past the first screenful, so what follows scrolls.
        timed("GuideNavigation:vertical", 2 * VERTICAL) {
            press(Key.DirectionDown, VERTICAL)
            press(Key.DirectionUp, VERTICAL)
        }
        timed("GuideNavigation:horizontal", 2 * HORIZONTAL) {
            press(Key.DirectionRight, HORIZONTAL)
            press(Key.DirectionLeft, HORIZONTAL)
        }
    }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(encoded: String): String = encoded
    }

    private companion object {
        const val TAG = "GuideNavigationTrace"
        const val CHANNELS = 600
        const val SLOTS = 8
        const val SLOT_MILLIS = 40 * 60_000L
        const val VERTICAL = 40
        const val HORIZONTAL = 3
    }
}
