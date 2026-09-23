package com.streammate.tv.feature.guide

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import android.view.KeyEvent
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertNotEquals
import androidx.compose.ui.semantics.SemanticsProperties
import com.streammate.tv.testing.awaitFocused
import com.streammate.tv.testing.awaitUntil
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.toPixelMap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.database.IptvChannelEntity
import com.streammate.tv.core.database.IptvSourceStateEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.TvProgrammeEntity
import com.streammate.tv.core.database.XmlTvChannelEntity
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.feature.settings.ChannelEditorScreen
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.metadata.MetadataRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue

class GuideScreenTest {
    private val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase
    private val queryExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var queryGate: CountDownLatch? = null
    @Volatile private var queryStarted: CountDownLatch? = null
    @Volatile private var epgGate: CountDownLatch? = null
    @Volatile private var epgStarted: CountDownLatch? = null
    private val timelineReads = ConcurrentLinkedQueue<List<Any?>>()
    private val listReads = ConcurrentLinkedQueue<String>()
    private val memberReads = ConcurrentLinkedQueue<List<Any?>>()
    /** The group each page of channel rows was read for. */
    private val channelPageReads = ConcurrentLinkedQueue<String>()

    // Compose must dispose its Room collectors before their database is closed.
    // JUnit @After runs inside the Compose rule, which raced teardown on CI.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            if (::database.isInitialized) database.close()
            queryExecutor.shutdownNow()
        }
    }).around(composeRule)

    @Before
    fun createGuide() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("streammate_secure_sources", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        // A test that switches source leaves the switch saved, and the next
        // guide would open on that source rather than the test source.
        AppPreferencesRepository(context).setLastGuideSourceId(null)
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java)
            .setQueryCallback({ sql, arguments ->
                if (sql.contains("FROM channel_lists") || sql.contains("FROM channel_list_members")) {
                    listReads.add(sql)
                }
                if (sql.contains("FROM channel_list_members")) memberReads.add(arguments.toList())
                if (sql.contains("c.channelId > ?")) channelPageReads.add(arguments[2]?.toString() ?: ALL_CHANNELS_READ)
                if (sql.contains("p.programmeId AS programmeId")) {
                    timelineReads.add(arguments.toList())
                    epgGate?.let { gate ->
                        epgStarted?.countDown()
                        check(gate.await(10, TimeUnit.SECONDS)) { "Test did not release the EPG query" }
                    }
                }
            }, { it.run() })
            .setQueryExecutor { query ->
                queryExecutor.execute {
                    queryGate?.let { gate ->
                        queryStarted?.countDown()
                        check(gate.await(10, TimeUnit.SECONDS)) { "Test did not release the guide query" }
                    }
                    query.run()
                }
            }
            .build()
        val now = System.currentTimeMillis()
        val dao = database.guideDao()
        dao.upsertSourceState(
            IptvSourceStateEntity("test", "Test source", "M3U", true, 1, 0, now),
        )
        dao.upsertChannels(
            listOf(
                IptvChannelEntity(
                    sourceId = "test",
                    snapshotId = "playlist",
                    channelId = "test:one",
                    tvgId = "one.fi",
                    name = "Channel One",
                    normalizedName = "channel one",
                    groupTitle = "News",
                    logoUrl = null,
                    encryptedStreamUrl = "encrypted",
                    userAgent = null,
                    referrer = null,
                    lastSeenEpochMillis = now,
                    catchupType = "shift",
                    catchupDays = 7,
                    // Both channels need an explicit order. playlistOrder
                    // defaults to Int.MAX_VALUE and the guide sorts on it
                    // ascending, so leaving this one unset put it *after* the
                    // channel below and initial focus landed on "two".
                    playlistOrder = 1,
                ),
                IptvChannelEntity(
                    sourceId = "test",
                    snapshotId = "playlist",
                    channelId = "test:two",
                    tvgId = "two.fi",
                    name = "Channel Two",
                    normalizedName = "channel two",
                    groupTitle = "News",
                    logoUrl = null,
                    encryptedStreamUrl = "encrypted-two",
                    userAgent = null,
                    referrer = null,
                    lastSeenEpochMillis = now,
                    playlistOrder = 2,
                ),
            ),
        )
        dao.activatePlaylistSnapshot("test", "playlist", 2, now)
        dao.upsertProgrammes(
            listOf(
                TvProgrammeEntity(
                    sourceId = "test",
                    snapshotId = "epg",
                    programmeId = "current",
                    xmltvChannelId = "one.fi",
                    startEpochMillis = now - 30 * 60_000,
                    stopEpochMillis = now + 30 * 60_000,
                    title = "Current programme",
                    subtitle = "Subtitle",
                    description = "Programme description",
                    categories = "News",
                ),
                TvProgrammeEntity(
                    sourceId = "test",
                    snapshotId = "epg",
                    programmeId = "current-two",
                    xmltvChannelId = "two.fi",
                    startEpochMillis = now - 15 * 60_000,
                    stopEpochMillis = now + 45 * 60_000,
                    title = "Second programme",
                    subtitle = null,
                    description = "Second programme description",
                    categories = "News",
                ),
            ) + (1..4).map { slot ->
                TvProgrammeEntity(
                    sourceId = "test",
                    snapshotId = "epg",
                    programmeId = "bulletin-$slot",
                    xmltvChannelId = "one.fi",
                    startEpochMillis = now + (30 + (slot - 1) * 3) * 60_000L,
                    stopEpochMillis = now + (30 + slot * 3) * 60_000L,
                    title = "Bulletin $slot",
                    subtitle = null,
                    description = "Bulletin $slot description",
                    categories = "News",
                )
            },
        )
        dao.upsertXmlTvChannels(
            listOf(
                XmlTvChannelEntity("test", "epg", "one.fi", "Channel One EPG", null),
                XmlTvChannelEntity("test", "epg", "two.fi", "Channel Two EPG", null),
            ),
        )
        dao.activateEpgSnapshot("test", "epg", 2, now)
    }

    @Test
    fun timelineShowsPreviewFiltersSearchAndInitialChannelFocus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var playedChannel: String? = null
        var catchupChannel: String? = null
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(context),
                    metadataRepository = MetadataRepository(
                        database.metadataDao(),
                        SecretSettingsStore(context, TestSecretCipher),
                        OkHttpClient(),
                    ),
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = { playedChannel = it },
                    onPlayCatchup = { channelId, _, _ -> catchupChannel = channelId },
                )
            }
        }

        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsDisplayed()

        // Left off the grid lands on whichever rail row is selected: the
        // first group, which the guide opens on, with All channels above it.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsFocused()
        composeRule.onNodeWithTag("guide-filter-all").assertIsDisplayed()

        // The hero carries the actions for whatever the grid is pointing at.
        composeRule.onNodeWithTag("guide-preview-watch").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-preview-catchup").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("test:one", catchupChannel) }
        composeRule.onAllNodesWithTag("guide-time-earlier").assertCountEquals(0)
        composeRule.onAllNodesWithTag("guide-time-now").assertCountEquals(0)
        composeRule.onAllNodesWithTag("guide-time-later").assertCountEquals(0)

        // Source, sorting, category editing, channel editing, settings and
        // back have moved off the screen and behind one control, so the grid
        // gets the room. They are all still reachable.
        composeRule.onAllNodesWithTag("guide-filter-source").assertCountEquals(0)
        composeRule.onNodeWithTag("guide-options").performClick()
        composeRule.onNodeWithTag("guide-filter-source").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-sort").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-category-edit").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-channels").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-back").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-options-close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("guide-search-toggle").performClick()
        composeRule.onNodeWithTag("guide-search-field").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-channel-test:one").performClick()
        composeRule.runOnIdle { assertEquals("test:one", playedChannel) }
    }

    /**
     * The guide opens on its first group, and All channels is a state that
     * can be chosen, not the absence of one.
     *
     * Opening on All channels read every row of the source each time the
     * guide was opened: 56,000 of them and three seconds of "Loading" on the
     * Shield. An earlier guide that opened on a group had no way to the whole
     * line-up at all, which is why this one was given the entry on the rail.
     */
    @Test
    fun theGuideOpensOnItsFirstGroupAndAllChannelsCanBeChosen() {
        seedExtraChannels(3)
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:two").assertIsDisplayed()
        composeRule.onAllNodesWithTag("guide-channel-test:extra-1").assertCountEquals(0)
        assertTrue("The whole source was read to show one group", channelPageReads.none { it == ALL_CHANNELS_READ })

        // The groups stay out of the way until left is pressed from the fixed
        // channel column.
        composeRule.onAllNodesWithTag("guide-filter-all").assertCountEquals(0)
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("guide-filter-all").performClick()
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-test:extra-1").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsDisplayed()

        // A refreshed list can restore focus to the grid without sending the
        // rail a Right key. That route must close the drawer too.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performSemanticsAction(SemanticsActions.RequestFocus) { request -> request() }
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onAllNodesWithTag("guide-filter-all").assertCountEquals(0)

        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-filter-all").assertIsFocused()
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").performClick()
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-test:extra-1").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-channel-test:two").assertIsDisplayed()
    }

    /**
     * Switching source lands on its first group, as opening the guide does.
     * It landed on all of the source's channels: on the Shield, a four-second
     * read of 57,644 rows at every switch to the large source.
     */
    @Test
    fun switchingSourceLandsOnItsFirstGroupRatherThanEveryChannel() {
        seedSecondSource()
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        channelPageReads.clear()

        // Options live in the group drawer, which Left opens.
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-options").performClick()
        composeRule.onNodeWithTag("guide-filter-source").performClick()
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-second:ch-1").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onAllNodesWithTag("guide-channel-second:ch-4").assertCountEquals(0)
        composeRule.onAllNodesWithTag("guide-channel-test:one").assertCountEquals(0)
        assertTrue("The new source was read whole: $channelPageReads", channelPageReads.none { it == ALL_CHANNELS_READ })
        assertTrue(channelPageReads.contains("Alpha"))
    }

    /**
     * Closing the options after a switch puts focus on the new source's first
     * channel, the way the owner goes: Left into the drawer, up to Options,
     * OK, OK on the source, Back. The sheet's buttons leave with it, and focus
     * left to itself fell to the first focusable on the screen, the hero's
     * buttons at the top.
     */
    @Test
    fun closingTheOptionsAfterASourceSwitchLandsOnItsFirstChannel() {
        seedSecondSource()
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        pressUntilFocused(Key.DirectionUp, "guide-options")
        composeRule.onNodeWithTag("guide-options").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitFocused("guide-filter-source")
        composeRule.onNodeWithTag("guide-filter-source").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-second:ch-1").fetchSemanticsNodes().isNotEmpty() }

        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-second:ch-1")
        composeRule.onAllNodesWithTag("guide-options-sheet").assertCountEquals(0)
        // Focus in the grid closes the drawer, as choosing a group does.
        composeRule.onAllNodesWithTag("guide-filter-all").assertCountEquals(0)
    }

    /**
     * A switch starts the source afresh, the one the guide was opened on
     * included: back on it, the guide shows its first group and focus is on
     * its first channel, not on the channel the guide was opened for.
     */
    @Test
    fun switchingBackToTheOpeningSourceLandsOnItsFirstChannel() {
        seedSecondSource()
        showGuide(initialChannelId = "test:two")
        composeRule.awaitFocused("guide-channel-test:two")

        // Menu opens the options from the grid.
        composeRule.onNodeWithTag("guide-channel-test:two").performKeyInput { pressKey(Key.Menu) }
        composeRule.awaitFocused("guide-filter-source")
        composeRule.onNodeWithTag("guide-filter-source").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-second:ch-1").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("guide-filter-source").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-channel-test:one").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("guide-channel-second:ch-1").fetchSemanticsNodes().isEmpty()
        }

        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-test:one")
    }

    /**
     * Far down a long list, a switch still lands on the top of the next one.
     * The lists on the owner's Shield run to thousands of channels, and the
     * new list's first row is not on screen until the list is scrolled to it.
     */
    @Test
    fun aSwitchFromFarDownAListLandsOnTheTopOfTheNext() {
        seedExtraChannels(40, group = "News")
        seedSecondSource(alphaCount = 40)
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        pressUntilFocused(Key.DirectionDown, "guide-channel-test:extra-24", limit = 30)
        composeRule.onAllNodesWithTag("guide-channel-test:one").assertCountEquals(0)

        composeRule.onNodeWithTag("guide-channel-test:extra-24").performKeyInput { pressKey(Key.Menu) }
        composeRule.awaitFocused("guide-filter-source")
        composeRule.onNodeWithTag("guide-filter-source").performKeyInput { pressKey(Key.DirectionCenter) }
        val secondSourceRow = SemanticsMatcher("a row of the second source") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("guide-channel-second:") == true
        }
        composeRule.awaitUntil { composeRule.onAllNodes(secondSourceRow).fetchSemanticsNodes().isNotEmpty() }

        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-second:ch-1")
    }

    /**
     * Back from the group manager the guide opens with its options showing,
     * and its list is read under them. Closing them puts focus in the list,
     * not on the hero at the top.
     */
    @Test
    fun closingTheOptionsOnReturnFromTheGroupManagerPutsFocusInTheList() {
        showGuide(startInOptions = true, initialManagedGroup = "News")
        composeRule.awaitFocused("guide-filter-source")
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-test:one").fetchSemanticsNodes().isNotEmpty() }

        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-test:one")
    }

    /** Closed with nothing switched, the options give focus back to where they were opened from. */
    @Test
    fun closingTheOptionsWithoutASwitchReturnsFocusToWhereTheyWereOpened() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        // From the drawer, closed with the sheet's own Close: back on Options.
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        pressUntilFocused(Key.DirectionUp, "guide-options")
        composeRule.onNodeWithTag("guide-options").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitFocused("guide-filter-source")
        pressUntilFocused(Key.DirectionDown, "guide-options-close")
        composeRule.onNodeWithTag("guide-options-close").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitFocused("guide-options")
        composeRule.onNodeWithTag("guide-filter-all").assertIsDisplayed()

        // From the grid, with Menu, closed with Back: back on the channel.
        pressUntilFocused(Key.DirectionRight, "guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.Menu) }
        composeRule.awaitFocused("guide-filter-source")
        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-test:one")
    }

    /**
     * With one playlist the source button has nothing to switch to and changes
     * nothing. Preview 55 took the press for a switch and waited for a group
     * to be chosen for the same source, which nothing did: the rows stayed
     * under a reading notice until the guide was opened again.
     */
    @Test
    fun theSourceButtonWithOnePlaylistLeavesTheGuideAsItWas() {
        seedExtraChannels(3)
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.Menu) }
        composeRule.awaitFocused("guide-filter-source")
        composeRule.onNodeWithTag("guide-filter-source").performKeyInput { pressKey(Key.DirectionCenter) }
        pressBackOnTheRemote()
        composeRule.awaitFocused("guide-channel-test:one")

        // Still on its group, and another group's rows still arrive when it
        // is chosen: stuck, the drawer had All channels selected and no
        // group's rows were read any more.
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.awaitFocused("guide-group-${"News".hashCode()}")
        pressUntilFocused(Key.DirectionDown, "guide-group-${"Extra".hashCode()}")
        composeRule.onNodeWithTag("guide-group-${"Extra".hashCode()}").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-channel-test:extra-1").fetchSemanticsNodes().isNotEmpty() }
    }

    /** One line for the whole grid, drawn over the header and every row. */
    @Test
    fun aGridCellIsOneSemanticsNodeThatStillSaysWhatItShows() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.awaitUntil { composeRule.onAllNodesWithTag("guide-programme-current").fetchSemanticsNodes().isNotEmpty() }

        // With an accessibility service enabled Compose walks this tree many
        // times a second, skipping what lies under a node that clears its
        // descendants' semantics (SemanticsNode.replacedChildren, which the
        // test API does not expose: its unmerged children include them). So
        // every node directly in the grid is a cell, and every cell clears.
        val list = composeRule.onNodeWithTag("guide-channel-list", useUnmergedTree = true).fetchSemanticsNode()
        fun isCell(node: androidx.compose.ui.semantics.SemanticsNode): Boolean =
            node.config.getOrNull(SemanticsProperties.TestTag).orEmpty().let { it.startsWith("guide-channel-") || it.startsWith("guide-programme-") }
        fun walked(node: androidx.compose.ui.semantics.SemanticsNode): List<androidx.compose.ui.semantics.SemanticsNode> =
            node.children.flatMap { child -> listOf(child) + if (child.config.isClearingSemantics) emptyList() else walked(child) }
        val nodes = walked(list)
        assertTrue("the grid has no cells", nodes.size >= 4)
        nodes.forEach { node ->
            assertTrue("a node that is not a cell is walked in the grid: ${node.config}", isCell(node))
            assertTrue("${node.config.getOrNull(SemanticsProperties.TestTag)} leaves its texts in the walk", node.config.isClearingSemantics)
        }

        fun said(tag: String): List<String> = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals("Current programme", said("guide-programme-current").first())
        assertEquals(2, said("guide-programme-current").size)
        assertTrue(said("guide-channel-test:one").toString(), "Channel One" in said("guide-channel-test:one"))
        // A cell keeps what makes it a cell: focus, a click and its tag.
        val cell = composeRule.onNodeWithTag("guide-programme-current").fetchSemanticsNode().config
        assertTrue(cell.contains(SemanticsProperties.Focused) && cell.contains(SemanticsActions.OnClick))
        // And is still found by what it shows, as the hero above it also is.
        composeRule.onNode(
            androidx.compose.ui.test.hasTestTag("guide-programme-current") and androidx.compose.ui.test.hasText("Current programme"),
        ).assertExists()
    }

    @Test
    fun theGridDrawsASingleNowLine() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        composeRule.onAllNodesWithTag("guide-now-line").assertCountEquals(1)
    }

    @Test
    fun unifiedGuideSearchFindsChannelsAndProgrammes() = runBlocking {
        val results = GuideRepository(database.guideDao()).search("Current")

        assertEquals("programme", results.single().type)
        assertEquals("test:one", results.single().channelId)
    }

    @Test
    fun compactGuideDeduplicatesOverlappingCurrentProgrammes() = runBlocking {
        val now = System.currentTimeMillis()
        database.guideDao().upsertProgrammes(
            listOf(
                TvProgrammeEntity(
                    sourceId = "test",
                    snapshotId = "epg",
                    programmeId = "overlap",
                    xmltvChannelId = "one.fi",
                    startEpochMillis = now - 10 * 60_000,
                    stopEpochMillis = now + 10 * 60_000,
                    title = "Overlapping programme",
                    subtitle = null,
                    description = null,
                    categories = "News",
                ),
            ),
        )

        val guide = GuideRepository(database.guideDao()).observeGuide(now).first()

        assertEquals(2, guide.size)
        assertEquals(2, guide.map { it.id }.distinct().size)
    }

    @Test
    fun guideRestoresFocusToThePreviouslyWatchedChannel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(context),
                    metadataRepository = MetadataRepository(
                        database.metadataDao(),
                        SecretSettingsStore(context, TestSecretCipher),
                        OkHttpClient(),
                    ),
                    initialChannelId = "test:two",
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = {},
                    onPlayCatchup = { _, _, _ -> },
                )
            }
        }

        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("guide-channel-test:two").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-channel-test:two").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun pagingTimeLeavesFocusInTheGridRatherThanAtTheTopOfTheScreen() {
        // Paging destroys the cell that had focus, and Compose then falls back
        // to the first focusable on the screen - the source button - which
        // meant walking all the way back down for every further page.
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        // Into the programmes, then out past the end of the window.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        repeat(6) {
            composeRule.onAllNodes(isFocused()).onFirst()
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.waitForIdle()
        }

        val focused = composeRule.onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertTrue(
            "focus was thrown out of the grid and onto $focused",
            focused != null &&
                (focused.startsWith("guide-programme") || focused.startsWith("guide-channel")),
        )
    }

    @Test
    fun pagingWaitsForTheDestinationProgrammesBeforeRestoringFocus() {
        assertPagingWaitsForProgrammes(windowed = false)
    }

    @Test
    fun pagingALargeSourceWaitsForTheDestinationProgrammesBeforeRestoringFocus() {
        assertPagingWaitsForProgrammes(windowed = true)
    }

    private fun assertPagingWaitsForProgrammes(windowed: Boolean) {
        runBlocking {
            val now = System.currentTimeMillis()
            val dao = database.guideDao()
            if (windowed) {
                dao.upsertChannels((1..GUIDE_WINDOWED_READ_THRESHOLD_FOR_TEST).map { index ->
                    IptvChannelEntity(
                        sourceId = "test", snapshotId = "playlist", channelId = "test:extra-$index",
                        tvgId = "extra$index", name = "Extra $index", normalizedName = "extra $index",
                        groupTitle = "Extra", logoUrl = null, encryptedStreamUrl = "encrypted",
                        userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = 100 + index,
                    )
                })
                dao.activatePlaylistSnapshot("test", "playlist", 2 + GUIDE_WINDOWED_READ_THRESHOLD_FOR_TEST, now)
            }
            // The ordinary fixture is relative to the current minute. Late in
            // a half hour its bulletins overlap the first destination page,
            // so restoring focus to one of them is correct. Give this paging
            // test its own two programmes relative to the page boundary.
            val initialWindowStart = GuideTimeWindow.nowStart(System.currentTimeMillis())
            dao.deleteProgrammeSnapshot("test", "epg")
            dao.upsertProgrammes(listOf(TvProgrammeEntity(
                sourceId = "test", snapshotId = "epg", programmeId = "bulletin-4", xmltvChannelId = "one.fi",
                startEpochMillis = initialWindowStart + 60 * 60_000L,
                stopEpochMillis = initialWindowStart + 70 * 60_000L,
                title = "Last initial-page programme", subtitle = null, description = null, categories = "News",
            ), TvProgrammeEntity(
                sourceId = "test", snapshotId = "epg", programmeId = "next-page", xmltvChannelId = "one.fi",
                startEpochMillis = initialWindowStart + 3 * 3_600_000L,
                stopEpochMillis = initialWindowStart + 3 * 3_600_000L + 10 * 60_000L,
                title = "Next page", subtitle = null, description = null, categories = "News",
            )))
        }
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("guide-programme-bulletin-4").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-programme-bulletin-4")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

        fun page(key: Key, destination: String) {
            val gate = CountDownLatch(1)
            val started = CountDownLatch(1)
            queryStarted = started
            queryGate = gate
            try {
                composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
                assertTrue("The destination query never started", started.await(5, TimeUnit.SECONDS))
                // Longer than the old thirty-frame retry budget. Room is held
                // here while Compose lays out the destination's placeholder.
                composeRule.mainClock.advanceTimeBy(1_000)
                composeRule.onNodeWithTag("guide-channel-test:one").assertIsFocused()
            } finally {
                queryGate = null
                queryStarted = null
                gate.countDown()
            }
            composeRule.awaitFocused(destination)
        }
        page(Key.DirectionRight, "guide-programme-next-page")
        // Pages overlap by ninety minutes, so this short programme remains
        // visible in the following page and is absent from the one after it.
        page(Key.DirectionRight, "guide-programme-next-page")
        page(Key.DirectionRight, "guide-programme-test:one-none")
        page(Key.DirectionLeft, "guide-programme-next-page")
    }

    @Test
    fun reopeningTheCategoryDrawerKeepsItsViewport() {
        runBlocking {
            val now = System.currentTimeMillis()
            val dao = database.guideDao()
            dao.upsertChannels((1..30).map { index ->
                IptvChannelEntity(
                    sourceId = "test", snapshotId = "playlist", channelId = "test:group-$index",
                    tvgId = "group$index", name = "Group channel $index", normalizedName = "group channel $index",
                    groupTitle = "Group %02d".format(index), logoUrl = null, encryptedStreamUrl = "encrypted",
                    userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = 100 + index,
                )
            })
            dao.activatePlaylistSnapshot("test", "playlist", 32, now)
        }
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.awaitFocused("guide-group-${"News".hashCode()}")
        val groupTag = "guide-group-${"Group 20".hashCode()}"
        composeRule.onNodeWithTag("guide-group-list").performScrollToIndex(18)
        composeRule.onNodeWithTag(groupTag)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        composeRule.awaitFocused(groupTag)
        val before = composeRule.onNodeWithTag(groupTag).fetchSemanticsNode().boundsInRoot.top
        composeRule.onNodeWithTag(groupTag).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.awaitFocused("guide-channel-test:group-20")
        repeat(2) {
            composeRule.onNodeWithTag("guide-channel-test:group-20").performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.awaitFocused(groupTag)
            val reopened = composeRule.onNodeWithTag(groupTag).fetchSemanticsNode().boundsInRoot.top
            assertEquals("Reopening the drawer moved the selected category", before, reopened, 1f)
            composeRule.onNodeWithTag(groupTag).performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.awaitFocused("guide-channel-test:group-20")
        }
    }

    @Test
    fun aChannelRowStaysCompactEnoughToShowAGuideful() {
        // A guide is judged by how many channels it shows at once. Every
        // dimension in a row looks reasonable on its own; together they decide
        // whether eight channels fit or four do.
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        val row = composeRule.onNodeWithTag("guide-channel-test:one").fetchSemanticsNode()
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .displayMetrics.density
        val heightDp = row.size.height / density

        // The room previously used for seven cramped rows now holds six taller
        // ones, without letting a row grow into a large channel card.
        assertTrue(
            "a channel row is ${heightDp.toInt()}dp tall, which costs visible channels",
            heightDp in 43f..46f,
        )
    }

    @Test
    fun adjacentProgrammeBlocksDoNotPaintOverEachOther() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        // These three-minute bulletins used to be forced to a minimum visual
        // width and painted over the entry whose start equalled their end.
        var previous: String? = null
        listOf("current", "bulletin-1", "bulletin-2", "bulletin-3").forEach { id ->
            composeRule.onAllNodes(isFocused()).onFirst()
                .performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.awaitFocused("guide-programme-$id")

            previous?.let { earlier ->
                val before = composeRule.onNodeWithTag("guide-programme-$earlier").fetchSemanticsNode()
                val after = composeRule.onNodeWithTag("guide-programme-$id").fetchSemanticsNode()
                val beforeRight = before.positionInRoot.x + before.size.width
                assertTrue(
                    "$earlier ends at $beforeRight but $id begins at ${after.positionInRoot.x}",
                    beforeRight <= after.positionInRoot.x + 0.5f,
                )
            }
            previous = id
        }
    }

    @Test
    fun focusedCurrentProgrammeKeepsItsTealProgressStrip() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.awaitFocused("guide-programme-current")

        val pixels = composeRule.onNodeWithTag("guide-programme-current")
            .captureToImage()
            .toPixelMap()
        val cyanPixels = buildList {
            for (y in (pixels.height - 6).coerceAtLeast(0) until pixels.height) {
                for (x in pixels.width / 8 until pixels.width * 3 / 8) {
                    add(pixels[x, y])
                }
            }
        }.count { colour ->
            colour.red < 0.40f && colour.green > 0.70f && colour.blue > 0.70f
        }

        assertTrue("focused programme progress was painted over", cyanPixels > 0)
    }

    @Test
    fun organizationHidingUpdatesTheGuideAndSharedPlaybackChannelRead() {
        val organization = com.streammate.tv.iptv.repository.OrganizationRepository(database.organizationDao())
        showGuide(organization)
        composeRule.awaitFocused("guide-channel-test:one")
        runBlocking {
            organization.change(listOf(com.streammate.tv.core.database.OrganizationChange(
                com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.LIVE, "test", "name:news"),
                enabled = false, changeEnabled = true,
            )))
        }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithTag("guide-channel-test:two").fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            val repository = GuideRepository(database.guideDao(), organization = organization)
            assertTrue(repository.observeGuide(System.currentTimeMillis()).first().isEmpty())
            organization.change(listOf(
                com.streammate.tv.core.database.OrganizationChange(com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.LIVE, "test", "name:news"), enabled = true, changeEnabled = true),
                com.streammate.tv.core.database.OrganizationChange(com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.LIVE, "test", "name:news", "test:two"), enabled = false, changeEnabled = true),
            ))
            assertEquals(listOf("test:one"), repository.observeGuide(System.currentTimeMillis()).first().map { it.id })
            assertEquals(2, database.guideDao().observeEditableChannels().first().size)
        }
    }

    /**
     * A second playlist, below the test source by priority: [alphaCount]
     * channels in its first group, Alpha, then three in Beta.
     */
    private fun seedSecondSource(alphaCount: Int = 3) = runBlocking {
        val now = System.currentTimeMillis()
        val dao = database.guideDao()
        val count = alphaCount + 3
        // Below the test source by priority, so the guide starts on that one.
        dao.upsertSourceState(IptvSourceStateEntity("second", "Second source", "M3U", true, 1, -1, now))
        dao.upsertChannels((1..count).map { index ->
            IptvChannelEntity(
                sourceId = "second", snapshotId = "second-playlist", channelId = "second:ch-$index",
                tvgId = null, name = "Second $index", normalizedName = "second $index",
                groupTitle = if (index <= alphaCount) "Alpha" else "Beta", logoUrl = null, encryptedStreamUrl = "encrypted",
                userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = index,
            )
        })
        dao.activatePlaylistSnapshot("second", "second-playlist", count, now)
    }

    /** Presses [key] where focus is, as the remote does, until [testTag] holds focus. */
    private fun pressUntilFocused(key: Key, testTag: String, limit: Int = 10) {
        repeat(limit) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(hasTestTag(testTag) and isFocused()).fetchSemanticsNodes().isNotEmpty()) return
            composeRule.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
        }
        composeRule.awaitFocused(testTag)
    }

    /**
     * Back as the remote sends it, through the window: the sheet closes on the
     * activity's back dispatch, which key input sent into Compose never reaches.
     */
    private fun pressBackOnTheRemote() {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun showGuide(
        organization: com.streammate.tv.iptv.repository.OrganizationRepository? = null,
        onPlay: (String) -> Unit = {},
        initialChannelId: String? = null,
        startInOptions: Boolean = false,
        initialManagedGroup: String? = null,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = GuideRepository(database.guideDao(), organization = organization),
                    preferencesRepository = AppPreferencesRepository(context),
                    metadataRepository = MetadataRepository(
                        database.metadataDao(),
                        SecretSettingsStore(context, TestSecretCipher),
                        OkHttpClient(),
                    ),
                    initialChannelId = initialChannelId,
                    startInOptions = startInOptions,
                    initialManagedGroup = initialManagedGroup,
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = onPlay,
                    onPlayCatchup = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun focusChangesDoNotRestartCustomListQueries() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-current").fetchSemanticsNodes().isNotEmpty() &&
                listReads.any { it.contains("FROM channel_lists") }
        }
        composeRule.waitForIdle()
        val before = listReads.size
        repeat(6) {
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.awaitFocused("guide-channel-test:two")
            composeRule.onNodeWithTag("guide-channel-test:two").performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.awaitFocused("guide-channel-test:one")
        }
        // A barrier behind the Room queries ensures cancelled/restarted reads have been counted.
        queryExecutor.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals("Moving focus resubscribed to custom-list Room queries", before, listReads.size)
        assertTrue("All channels must not read custom-list memberships", memberReads.isEmpty())
    }

    @Test
    fun customListReadsAreScopedAndStillReceiveMembershipChanges() {
        val repository = GuideRepository(database.guideDao())
        val (selectedList, otherList) = runBlocking {
            val selected = repository.createCustomChannelList("Selected", 0)
            val other = repository.createCustomChannelList("Other", 1)
            repository.setCustomListMembership(selected, "test:one", true, 0)
            repository.setCustomListMembership(other, "test:two", true, 0)
            selected to other
        }
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        assertTrue(memberReads.isEmpty())
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-list-$selectedList").performClick()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onAllNodesWithTag("guide-channel-test:two").assertCountEquals(0)
        runBlocking { repository.setCustomListMembership(selectedList, "test:two", true, 1) }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-channel-test:two").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(memberReads.isNotEmpty())
        assertTrue("Only the selected list may be queried: $memberReads", memberReads.all { it == listOf(selectedList) })
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-list-$otherList").performClick()
        composeRule.awaitFocused("guide-channel-test:two")
        composeRule.onAllNodesWithTag("guide-channel-test:one").assertCountEquals(0)
        assertTrue(memberReads.any { it == listOf(otherList) })
        assertTrue(memberReads.all { it.size == 1 })
    }

    @Test
    fun smallGuideChannelsAreUsableBeforeEpgAndArrivalsKeepFocus() {
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        epgGate = gate
        epgStarted = started
        var played: String? = null
        try {
            showGuide(onPlay = { played = it })
            composeRule.awaitFocused("guide-channel-test:one")
            assertTrue("No background EPG request", started.await(5, TimeUnit.SECONDS))
            composeRule.onNodeWithTag("guide-programme-test:one-loading").assertIsDisplayed()
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.onNodeWithTag("guide-channel-test:one").assertIsFocused()
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithTag("guide-channel-test:two").assertIsFocused().performClick()
            composeRule.runOnIdle { assertEquals("test:two", played) }
        } finally {
            epgGate = null
            epgStarted = null
            gate.countDown()
        }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-current-two").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-channel-test:two").assertIsFocused()
        composeRule.onNodeWithTag("guide-preview-watch").assertIsDisplayed()
    }

    @Test
    fun favouritesAlsoShowChannelsWhileTheirEpgIsBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppPreferencesRepository(context)
        runBlocking { preferences.setFavouriteChannel("test:two", true) }
        showGuide()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-current").fetchSemanticsNodes().isNotEmpty()
        }
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        epgGate = gate
        epgStarted = started
        try {
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.onNodeWithTag("guide-filter-favourites").performClick()
            composeRule.awaitFocused("guide-channel-test:two")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            composeRule.onNodeWithTag("guide-programme-test:two-loading").assertIsDisplayed()
            composeRule.onAllNodesWithTag("guide-channel-test:one").assertCountEquals(0)
        } finally {
            epgGate = null
            epgStarted = null
            gate.countDown()
            runBlocking { preferences.setFavouriteChannel("test:two", false) }
        }
    }

    @Test
    fun threePlaylistsWithNinetyThousandProgrammesKeepReadsWindowed() {
        val now = System.currentTimeMillis()
        runBlocking {
            val dao = database.guideDao()
            for (source in listOf("test", "second", "third")) {
                if (source != "test") dao.upsertSourceState(IptvSourceStateEntity(source, source, "M3U", true, 0, 0, now))
                dao.upsertChannels((1..1_000).map { index ->
                    IptvChannelEntity(
                        sourceId = source, snapshotId = "playlist", channelId = "$source:scale-$index",
                        tvgId = "scale-$index", name = "Scale $index", normalizedName = "scale $index",
                        groupTitle = "News", logoUrl = null, encryptedStreamUrl = "encrypted",
                        userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = index + 100,
                    )
                })
                // Import-sized batches also keep the fixture from allocating 90k entities at once.
                for (batchStart in 1..1_000 step 20) {
                    dao.upsertProgrammes((batchStart until batchStart + 20).flatMap { channel ->
                        (0 until 30).map { slot ->
                            val start = now - 4 * 3_600_000L + slot * 30 * 60_000L
                            TvProgrammeEntity(source, "epg", "scale-$channel-$slot", "scale-$channel",
                                start, start + 30 * 60_000L, "Programme $channel/$slot", null,
                                "Synthetic schedule description for the large guide fixture.", "News")
                        }
                    })
                }
                dao.activatePlaylistSnapshot(source, "playlist", if (source == "test") 1_002 else 1_000, now)
                dao.activateEpgSnapshot(source, "epg", if (source == "test") 1_002 else 1_000, now)
            }
        }
        val started = android.os.SystemClock.elapsedRealtime()
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        val channelsReady = android.os.SystemClock.elapsedRealtime() - started
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.awaitFocused("guide-channel-test:two")
        composeRule.onNodeWithTag("guide-channel-list").performScrollToIndex(901)
        composeRule.awaitUntil { timelineReads.any { "test:scale-900" in it } }
        assertTrue("No unrelated custom-list membership reads", memberReads.isEmpty())
        timelineReads.forEach { read ->
            assertTrue("Unbounded channel request: ${read.size}", read.size <= 82)
            assertEquals(4 * 3_600_000L, (read[0] as Number).toLong() - (read[1] as Number).toLong())
            assertTrue(read.drop(2).all { it.toString().startsWith("test:") })
        }
        println("Synthetic 3-playlist/90k-programme guide: channels focused in ${channelsReady}ms (debug emulator, not a device benchmark)")
    }

    @Test
    fun scrollingALargeGuideReadsOnlyNearbyChannelsAndFourHours() {
        seedExtraChannels(600, group = "News")
        showGuide()
        composeRule.awaitUntil { timelineReads.isNotEmpty() }
        val first = timelineReads.first()
        assertTrue(first.size.toString(), first.size <= 82)
        assertEquals(4 * 3_600_000L, (first[0] as Number).toLong() - (first[1] as Number).toLong())
        assertTrue(first.drop(2).contains("test:one"))
        assertTrue(!first.drop(2).contains("test:extra-500"))
        composeRule.onNodeWithTag("guide-channel-list").performScrollToIndex(501)
        composeRule.awaitUntil { timelineReads.any { "test:extra-500" in it } }
        timelineReads.forEach { read ->
            assertTrue("Unbounded channel read: ${read.size}", read.size <= 82)
            assertEquals(4 * 3_600_000L, (read[0] as Number).toLong() - (read[1] as Number).toLong())
        }
    }

    @Test
    fun guideSearchFindsAnOffscreenProgrammeWithoutLoadingEveryTimeline() {
        seedExtraChannels(600, group = "News")
        runBlocking {
            val now = System.currentTimeMillis()
            database.guideDao().upsertProgrammes(listOf(TvProgrammeEntity(
                "test", "epg", "offscreen", "extra500", now - 60_000, now + 60_000,
                "Offscreen show", null, null, "",
            )))
        }
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-search-toggle").performClick()
        composeRule.onNodeWithTag("guide-search-field").performTextInput("Offscreen show")
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-offscreen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-channel-test:extra-500").assertIsDisplayed()
        assertTrue(timelineReads.all { it.size <= 82 })
    }

    /** In a group of their own, or in [group]: "News" is the one the guide opens on. */
    private fun seedExtraChannels(count: Int, group: String = "Extra") = runBlocking {
        val now = System.currentTimeMillis()
        database.guideDao().upsertChannels((1..count).map { index ->
            IptvChannelEntity(
                sourceId = "test", snapshotId = "playlist", channelId = "test:extra-$index",
                tvgId = "extra$index", name = "Extra $index", normalizedName = "extra $index",
                groupTitle = group, logoUrl = null, encryptedStreamUrl = "encrypted",
                userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = 100 + index,
            )
        })
        database.guideDao().activatePlaylistSnapshot("test", "playlist", 2 + count, now)
    }

    @Test
    fun namedChannelListsLargerThanSqlitesBindLimitDoNotReadEpg() = runBlocking {
        seedExtraChannels(1_100)
        val channels = GuideRepository(database.guideDao()).observeChannelsForIds(
            (1..1_100).map { "test:extra-$it" },
        ).first()
        assertEquals(1_100, channels.size)
        assertTrue(channels.all { it.programmes.isEmpty() })
        assertTrue(timelineReads.isEmpty())
    }

    @Test
    fun aSearchThatKeepsTheSameChannelsStillReceivesEpgUpdates() {
        showGuide()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-current").fetchSemanticsNodes().isNotEmpty()
        }
        val readsBeforeSearch = timelineReads.size
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-search-toggle").performClick()
        composeRule.onNodeWithTag("guide-search-field").performTextInput("Channel")
        composeRule.awaitUntil { timelineReads.size > readsBeforeSearch }
        runBlocking {
            val now = System.currentTimeMillis()
            database.guideDao().upsertProgrammes(listOf(TvProgrammeEntity(
                "test", "epg", "current", "one.fi", now - 60_000, now + 60_000,
                "Updated after search", null, null, "",
            )))
        }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithText("Updated after search", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun loadingTheNextPageDoesNotPullFocusOutOfTheCategoryDrawer() {
        showGuide()
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-current").fetchSemanticsNodes().isNotEmpty()
        }
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        epgGate = gate
        epgStarted = started
        try {
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.MediaNext) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsFocused()
        } finally {
            epgGate = null
            epgStarted = null
            gate.countDown()
        }
        composeRule.awaitUntil {
            composeRule.onAllNodesWithTag("guide-programme-test:one-none").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsFocused()
    }

    @Test
    fun aLargeSourceShowsItsRowsAndThenTheProgrammesOnScreen() {
        // Past the windowed-read threshold: rows come first, programmes for
        // the rows on screen follow from a second, small read.
        runBlocking {
            val now = System.currentTimeMillis()
            val dao = database.guideDao()
            dao.upsertChannels(
                (1..GUIDE_WINDOWED_READ_THRESHOLD_FOR_TEST).map { index ->
                    IptvChannelEntity(
                        sourceId = "test", snapshotId = "playlist", channelId = "test:big-$index",
                        tvgId = "big$index.fi", name = "Big $index", normalizedName = "big $index",
                        groupTitle = "News", logoUrl = null, encryptedStreamUrl = "encrypted-big-$index",
                        userAgent = null, referrer = null, lastSeenEpochMillis = now, playlistOrder = 100 + index,
                    )
                },
            )
            dao.activatePlaylistSnapshot("test", "playlist", 2 + GUIDE_WINDOWED_READ_THRESHOLD_FOR_TEST, now)
            dao.upsertProgrammes(
                listOf(
                    TvProgrammeEntity(
                        sourceId = "test", snapshotId = "epg", programmeId = "big-now", xmltvChannelId = "big1.fi",
                        startEpochMillis = now - 20 * 60_000, stopEpochMillis = now + 40 * 60_000,
                        title = "Big programme now", subtitle = null, description = null, categories = "Sport",
                    ),
                ),
            )
            dao.activateEpgSnapshot("test", "epg", 7, now)
        }
        showGuide()
        // The guide opens on its first group, 403 channels here: past the threshold.
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("guide-channel-test:big-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.awaitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Big programme now", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun okOnAProgrammeStillAheadOffersItsActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reminded = mutableListOf<String>()
        composeRule.setContent {
            StreamMateTheme {
                GuideScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(context),
                    metadataRepository = MetadataRepository(
                        database.metadataDao(),
                        SecretSettingsStore(context, TestSecretCipher),
                        OkHttpClient(),
                    ),
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = {},
                    onPlayCatchup = { _, _, _ -> },
                    onToggleReminder = { _, programme -> reminded += programme.id },
                )
            }
        }
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("guide-programme-bulletin-1").fetchSemanticsNodes().isNotEmpty()
        }
        // Bulletin 1 starts in half an hour: OK on it offers actions rather than playing.
        composeRule.onNodeWithTag("guide-programme-bulletin-1").performClick()
        composeRule.awaitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("guide-action-remind").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-action-favourite").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-action-remind").performClick()
        composeRule.awaitUntil(timeoutMillis = 5_000) { reminded.isNotEmpty() }
        assertEquals(listOf("bulletin-1"), reminded)
    }

    @Test
    fun upFromTheTopRowReachesTheInfoBoxButtons() {
        showGuide()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("guide-channel-test:one").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsFocused()
        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.awaitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("guide-preview-watch").assertIsFocused(); true }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("guide-preview-watch").assertIsFocused()
    }

    @Test
    fun theGuideCanBePagedThroughTimeAndSaysWhichDayItIsShowing() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        val today = composeRule.onNodeWithTag("guide-window-day", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

        // Transport keys move time. Arrow keys still belong to the programmes.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.MediaNext) }
        composeRule.waitForIdle()

        val tomorrow = composeRule.onNodeWithTag("guide-window-day", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

        assertNotEquals("the guide never left today", today, tomorrow)

        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.MediaPrevious) }
        composeRule.waitForIdle()

        val backAgain = composeRule.onNodeWithTag("guide-window-day", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        assertEquals("paging back did not return to where it started", today, backAgain)
    }

    @Test
    fun aRestrictedProfileSeesOnlyItsGroups() {
        val preferences = AppPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val liveRoom = com.streammate.tv.core.model.LibraryRoom.LIVE
        val profile = com.streammate.tv.app.Profiles.DEFAULT_ID
        runBlocking { preferences.setAllowedGroups(profile, liveRoom, setOf("name:news")) }
        try {
            showGuide(com.streammate.tv.iptv.repository.OrganizationRepository(database.organizationDao(), preferences))
            // Both channels are News, which this profile may see.
            composeRule.awaitFocused("guide-channel-test:one")

            runBlocking { preferences.setAllowedGroups(profile, liveRoom, setOf("name:sports")) }
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("guide-channel-test:one").fetchSemanticsNodes().isEmpty() &&
                    composeRule.onAllNodesWithTag("guide-channel-test:two").fetchSemanticsNodes().isEmpty()
            }

            runBlocking { preferences.setAllowedGroups(profile, liveRoom, setOf("name:news")) }
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("guide-channel-test:two").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            runBlocking { preferences.setAllowedGroups(profile, liveRoom, emptySet()) }
        }
    }

    @Test
    fun typingAChannelNumberMovesFocusToThatChannel() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        composeRule.onNodeWithTag("guide-channel-test:one").performKeyInput { pressKey(Key.Two) }

        composeRule.onNodeWithTag("channel-dial").assertIsDisplayed()
        // The number completes on its own two seconds after the last digit.
        composeRule.awaitFocused("guide-channel-test:two")
    }

    @Test
    fun channelManagementPersistsHiddenPreference() {
        composeRule.setContent {
            StreamMateTheme {
                ChannelEditorScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.awaitFocused("channel-editor-item-test:one")
        composeRule.onNodeWithTag("channel-editor-item-test:one").assertIsDisplayed()
        composeRule.onNodeWithTag("channel-editor-name").assertIsDisplayed()
        composeRule.onNodeWithTag("channel-editor-sort").assertIsDisplayed()
        composeRule.onNodeWithTag("channel-editor-group-${"News".hashCode()}").assertIsDisplayed()
        // The detail pane is a scrolling Column and this control sits below the
        // fold at 1080p, so it has to be scrolled to before it counts as shown.
        composeRule.onNodeWithTag("channel-editor-cycle-epg").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("channel-editor-new-list-name").performClick()
        composeRule.onNodeWithTag("channel-editor-new-list-name").performTextInput("News list")
        composeRule.onNodeWithTag("channel-editor-new-list-name").performImeAction()
        composeRule.onNodeWithTag("channel-editor-create-list").performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("channel-editor-cycle-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("channel-editor-hidden").performScrollTo().performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runBlocking { database.guideDao().channelPreference("test:one")?.hidden == true }
        }
    }

    @Test
    fun channelManagementSavesALogoAddressAndANumber() {
        composeRule.setContent {
            StreamMateTheme {
                ChannelEditorScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = AppPreferencesRepository(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.awaitFocused("channel-editor-item-test:one")
        composeRule.onNodeWithTag("channel-editor-logo-url").performScrollTo().performClick()
        composeRule.onNodeWithTag("channel-editor-logo-url").performTextInput("http://logo.example/one.png")
        composeRule.onNodeWithTag("channel-editor-number").performScrollTo().performClick()
        composeRule.onNodeWithTag("channel-editor-number").performTextInput("12")
        composeRule.onNodeWithTag("channel-editor-save").performScrollTo().performClick()
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            runBlocking {
                val preference = database.guideDao().channelPreference("test:one")
                preference?.customLogoUrl == "http://logo.example/one.png" && preference.channelNumber == 12
            }
        }
        // The list row carries the number the viewer gave the channel.
        composeRule.awaitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("12").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun channelManagementCanKeepHiddenChannelsOutOfTheList() {
        val preferences = AppPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent {
            StreamMateTheme {
                ChannelEditorScreen(
                    guideRepository = GuideRepository(database.guideDao()),
                    preferencesRepository = preferences,
                    onBack = {},
                )
            }
        }
        try {
            composeRule.awaitFocused("channel-editor-item-test:one")
            composeRule.onNodeWithTag("channel-editor-hidden").performScrollTo().performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                runBlocking { database.guideDao().channelPreference("test:one")?.hidden == true }
            }
            composeRule.onNodeWithTag("channel-editor-show-hidden").performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("channel-editor-item-test:one").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("channel-editor-item-test:two").assertIsDisplayed()
            composeRule.onNodeWithTag("channel-editor-show-hidden").performClick()
            composeRule.awaitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("channel-editor-item-test:one").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            runBlocking { preferences.setEditorsShowHidden(true) }
        }
    }
}

private object TestSecretCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "test:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
}

/** One more than the guide's windowed-read threshold. */
private const val GUIDE_WINDOWED_READ_THRESHOLD_FOR_TEST = 401
private const val ALL_CHANNELS_READ = "<all channels>"
