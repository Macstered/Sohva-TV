package com.streammate.tv.feature.catalogue.v2

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.catalogue.CatalogueGrouping
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogueBrowserV2ScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledHistoryFallsBackToTheFirstEnabledGroup() {
        composeRule.setContent { StreamMateTheme {
            CatalogueBrowserV2Screen(
                state = CatalogueBrowserState(
                    mode = CatalogueMode.MOVIES,
                    playlistGroups = listOf(CatalogueCategory("Action", 3)),
                    playlistGroupsReady = true,
                    selectedPartition = CatalogueBrowsePartition.PlaylistGroup("Action"),
                ),
                historyEnabled = false,
                onSelectPartition = {}, onOpenEntry = {}, onBack = {},
            )
        } }
        composeRule.waitUntil(3_000) {
            runCatching { composeRule.onNodeWithTag("catalogue-v2-group-${"Action".hashCode()}").assertIsFocused() }.isSuccess
        }
    }

    @Test
    fun historyIsTheInitialFocusedGroup() {
        composeRule.setContent {
            StreamMateTheme {
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        selectedPartition = CatalogueBrowsePartition.History,
                        wall = CatalogueBrowserWall(
                            CatalogueBrowseRequest(
                                CatalogueMode.MOVIES,
                                CatalogueBrowsePartition.History,
                            ),
                            null,
                            emptyList(),
                        ),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSelectPartition = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(3_000) {
            runCatching {
                composeRule.onNodeWithTag("catalogue-v2-history").assertIsFocused()
            }.isSuccess
        }
        composeRule.onNodeWithTag("catalogue-v2-history").assertIsFocused()
    }

    @Test
    fun optionsExposeRefreshAndEditingKeepsHiddenProviderGroupsReachable() {
        val action = CatalogueCategory("Action", 4)
        val hidden = CatalogueCategory("Hidden", 2)
        var refreshCount = 0
        var hiddenChange: Pair<String, Boolean>? = null

        composeRule.setContent {
            StreamMateTheme {
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        allPlaylistGroups = listOf(action, hidden),
                        playlistGroups = listOf(action),
                        playlistGroupsReady = true,
                        selectedPartition = CatalogueBrowsePartition.PlaylistGroup("Action"),
                    ),
                    hiddenPlaylistGroups = setOf("Hidden"),
                    onRefresh = {
                        refreshCount += 1
                        Result.success("Updated")
                    },
                    onSetPlaylistGroupHidden = { group, isHidden ->
                        hiddenChange = group to isHidden
                    },
                    onSelectPartition = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalogue-v2-options").performClick()
        composeRule.onNodeWithTag("catalogue-v2-refresh").performClick()
        composeRule.waitForIdle()
        assertEquals(1, refreshCount)

        composeRule.onNodeWithTag("catalogue-v2-category-edit").performClick()
        composeRule.onNodeWithTag("catalogue-v2-category-${"Hidden".hashCode()}")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("Hidden" to false, hiddenChange)
    }

    @Test
    fun providerGroupGridRendersAndChangesSelectionWithoutRecreatingTheScreen() {
        val action = CatalogueBrowsePartition.PlaylistGroup("Action")
        var selected: CatalogueBrowsePartition? = null
        val request = CatalogueBrowseRequest(CatalogueMode.MOVIES, action)
        val entry = CatalogueBrowseEntry(
            contentKey = "movie",
            target = CatalogueBrowseTarget.Movie("source", "1"),
            providerTitle = "Action Movie",
            playlistGroup = "Action",
            providerPosterUrl = null,
            year = 2024,
            rating = "8.1",
            genres = emptySet(),
            metadataOverride = null,
        )

        composeRule.setContent {
            StreamMateTheme {
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        playlistGroups = listOf(
                            CatalogueCategory("Action", 1),
                            CatalogueCategory("Drama", 2),
                        ),
                        playlistGroupsReady = true,
                        selectedPartition = action,
                        wall = CatalogueBrowserWall(request, null, listOf(entry)),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSelectPartition = { selected = it },
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalogue-v2-wall-current").assertIsDisplayed()
        composeRule.onNodeWithText("Action Movie").assertIsDisplayed()
        composeRule.onNodeWithTag("catalogue-v2-group-${"Drama".hashCode()}").performClick()

        assertEquals(CatalogueBrowsePartition.PlaylistGroup("Drama"), selected)
    }

    @Test
    fun sixThousandTitleWallRemainsLazyWhenJumpingToTheEnd() {
        val action = CatalogueBrowsePartition.PlaylistGroup("Action")
        val request = CatalogueBrowseRequest(CatalogueMode.MOVIES, action)
        val entries = List(6_000) { index ->
            CatalogueBrowseEntry(
                contentKey = "movie-$index",
                target = CatalogueBrowseTarget.Movie("source", index.toString()),
                providerTitle = "Movie $index",
                playlistGroup = "Action",
                providerPosterUrl = null,
                year = 2024,
                rating = "8.1",
                genres = emptySet(),
                metadataOverride = null,
            )
        }
        composeRule.setContent {
            StreamMateTheme {
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        playlistGroups = listOf(CatalogueCategory("Action", entries.size)),
                        playlistGroupsReady = true,
                        selectedPartition = action,
                        wall = CatalogueBrowserWall(request, null, entries),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSelectPartition = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()

        val started = SystemClock.elapsedRealtimeNanos()
        composeRule.onNodeWithTag("catalogue-v2-wall-current").performScrollToIndex(entries.lastIndex)
        composeRule.waitForIdle()
        val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000

        Log.i("CatalogueV2Bench", "grid_items=${entries.size} jump_ms=$elapsedMillis")
        composeRule.onNodeWithText("Movie 5999").assertIsDisplayed()
        assertTrue("V2 grid jump took ${elapsedMillis}ms", elapsedMillis < 3_000)
    }

    @Test
    fun detailDetourRestoresTheFocusedWallItem() {
        val action = CatalogueBrowsePartition.PlaylistGroup("Kids")
        val request = CatalogueBrowseRequest(CatalogueMode.MOVIES, action)
        val entries = List(30) { index ->
            CatalogueBrowseEntry(
                contentKey = "movie-$index",
                target = CatalogueBrowseTarget.Movie("source", index.toString()),
                providerTitle = "Movie $index",
                playlistGroup = "Kids",
                providerPosterUrl = null,
                year = 2024,
                rating = null,
                genres = emptySet(),
                metadataOverride = null,
            )
        }
        val session = CatalogueBrowserSession(CatalogueMode.MOVIES)
        val showBrowser = mutableStateOf(true)

        composeRule.setContent {
            StreamMateTheme {
                if (showBrowser.value) {
                    CatalogueBrowserV2Screen(
                        state = CatalogueBrowserState(
                            mode = CatalogueMode.MOVIES,
                            playlistGroups = listOf(CatalogueCategory("Kids", entries.size)),
                            playlistGroupsReady = true,
                            selectedPartition = action,
                            wall = CatalogueBrowserWall(request, null, entries),
                            wallLoadState = CatalogueWallLoadState.READY,
                        ),
                        onSelectPartition = {},
                        onOpenEntry = { showBrowser.value = false },
                        onBack = {},
                        session = session,
                    )
                } else {
                    androidx.tv.material3.Text("Details")
                }
            }
        }

        composeRule.onNodeWithTag("catalogue-v2-wall-current").performScrollToIndex(20)
        composeRule.onNodeWithText("Movie 20").performClick()
        composeRule.onNodeWithText("Details").assertIsDisplayed()

        composeRule.runOnIdle { showBrowser.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Movie 20").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun changingGroupsAlwaysStartsTheNewWallAtTheTop() {
        fun entries(group: String) = List(60) { index ->
            CatalogueBrowseEntry(
                contentKey = "$group-$index",
                target = CatalogueBrowseTarget.Movie("source", "$group-$index"),
                providerTitle = "$group Movie $index",
                playlistGroup = group,
                providerPosterUrl = null,
                year = 2024,
                rating = null,
                genres = emptySet(),
                metadataOverride = null,
            )
        }
        val actionEntries = entries("Action")
        val dramaEntries = entries("Drama")
        val selectedGroup = mutableStateOf("Action")

        composeRule.setContent {
            StreamMateTheme {
                val group = selectedGroup.value
                val partition = CatalogueBrowsePartition.PlaylistGroup(group)
                val wallEntries = if (group == "Action") actionEntries else dramaEntries
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        playlistGroups = listOf(
                            CatalogueCategory("Action", actionEntries.size),
                            CatalogueCategory("Drama", dramaEntries.size),
                        ),
                        playlistGroupsReady = true,
                        selectedPartition = partition,
                        wall = CatalogueBrowserWall(
                            CatalogueBrowseRequest(CatalogueMode.MOVIES, partition),
                            null,
                            wallEntries,
                        ),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSelectPartition = {
                        selectedGroup.value = (it as CatalogueBrowsePartition.PlaylistGroup).name!!
                    },
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        val wall = composeRule.onNodeWithTag("catalogue-v2-wall-current")
        wall.performScrollToIndex(30)
        composeRule.onNodeWithTag("catalogue-v2-group-${"Drama".hashCode()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Drama Movie 0").assertIsDisplayed()

        wall.performScrollToIndex(30)
        composeRule.onNodeWithTag("catalogue-v2-group-${"Action".hashCode()}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Action Movie 0").assertIsDisplayed()
    }

    @Test
    fun genreRailSelectsADatabaseAddressablePartition() {
        val action = CatalogueBrowsePartition.Genre(CatalogueGenre.ACTION)
        val drama = CatalogueBrowsePartition.Genre(CatalogueGenre.DRAMA)
        var selected: CatalogueBrowsePartition? = null
        val entry = CatalogueBrowseEntry(
            contentKey = "action-movie",
            target = CatalogueBrowseTarget.Movie("source", "1"),
            providerTitle = "Action Movie",
            playlistGroup = "Provider group",
            providerPosterUrl = null,
            year = 2024,
            rating = null,
            genres = setOf(CatalogueGenre.ACTION),
            metadataOverride = null,
        )

        composeRule.setContent {
            StreamMateTheme {
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        grouping = CatalogueGrouping.GENRE,
                        genreFacets = listOf(
                            CatalogueBrowseFacet(action, 4),
                            CatalogueBrowseFacet(drama, 9),
                        ),
                        genreFacetsReady = true,
                        selectedPartition = action,
                        wall = CatalogueBrowserWall(
                            CatalogueBrowseRequest(CatalogueMode.MOVIES, action),
                            null,
                            listOf(entry),
                        ),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSelectPartition = { selected = it },
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalogue-v2-genre-genre:drama").performClick()
        assertEquals(drama, selected)
    }

    @Test
    fun searchFieldEditsTheQueryWithoutRecreatingTheBrowser() {
        val partition = CatalogueBrowsePartition.PlaylistGroup("Action")
        val search = mutableStateOf("")

        composeRule.setContent {
            StreamMateTheme {
                val query = search.value
                CatalogueBrowserV2Screen(
                    state = CatalogueBrowserState(
                        mode = CatalogueMode.MOVIES,
                        search = query,
                        playlistGroups = listOf(CatalogueCategory("Action", 1)),
                        playlistGroupsReady = true,
                        selectedPartition = partition,
                        wall = CatalogueBrowserWall(
                            CatalogueBrowseRequest(CatalogueMode.MOVIES, partition, query),
                            null,
                            emptyList(),
                        ),
                        wallLoadState = CatalogueWallLoadState.READY,
                    ),
                    onSearchChange = { search.value = it },
                    onSelectPartition = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("catalogue-v2-search").performClick()
        composeRule.onNodeWithTag("catalogue-v2-search").performTextInput("Strong")
        composeRule.waitForIdle()

        assertEquals("Strong", search.value)
    }
}
