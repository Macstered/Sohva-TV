package com.streammate.tv.feature.guide

import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onFirst
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GuideScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: StreamMateDatabase

    @Before
    fun createGuide() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("streammate_secure_sources", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
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

    @After
    fun closeGuide() = database.close()

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

        // Left off the grid lands on whichever rail row is selected. The guide
        // opens on All channels now rather than picking a group for you, so
        // that is where it goes.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-filter-all").assertIsFocused()
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsDisplayed()

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
     * All channels is a state, not the absence of one.
     *
     * The guide used to select the first group on arrival, so the whole
     * line-up could never be seen at once and there was no way back to it once
     * a group had been picked.
     */
    @Test
    fun theGuideOpensOnAllChannelsAndCanReturnToIt() {
        showGuide()
        composeRule.awaitFocused("guide-channel-test:one")

        // The groups stay out of the way until left is pressed from the fixed
        // channel column.
        composeRule.onAllNodesWithTag("guide-filter-all").assertCountEquals(0)
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-filter-all").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsDisplayed()

        // A refreshed list can restore focus to the grid without sending the
        // rail a Right key. That route must close the drawer too.
        composeRule.onNodeWithTag("guide-channel-test:one")
            .performSemanticsAction(SemanticsActions.RequestFocus) { request -> request() }
        composeRule.awaitFocused("guide-channel-test:one")
        composeRule.onAllNodesWithTag("guide-filter-all").assertCountEquals(0)

        composeRule.onNodeWithTag("guide-channel-test:one")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("guide-group-${"News".hashCode()}").assertIsFocused()
        composeRule.onNodeWithTag("guide-filter-all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide-channel-test:one").assertIsDisplayed()
        composeRule.onNodeWithTag("guide-channel-test:two").assertIsDisplayed()
    }

    /** One line for the whole grid, drawn over the header and every row. */
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

    private fun showGuide(organization: com.streammate.tv.iptv.repository.OrganizationRepository? = null) {
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
                    onBack = {},
                    onSettings = {},
                    onChannels = {},
                    onPlay = {},
                    onPlayCatchup = { _, _, _ -> },
                )
            }
        }
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
}

private object TestSecretCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "test:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
}
