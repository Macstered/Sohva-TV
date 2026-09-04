package com.streammate.tv.feature.catalogue.v2

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.streammate.tv.app.StreamMateTheme
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.feature.catalogue.CatalogueGrouping
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.iptv.repository.CatalogueCategory
import com.streammate.tv.testing.awaitUntil
import org.junit.Rule
import org.junit.Test

class CatalogueBrowserV2RailFocusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun leftScrollsAnOffscreenSelectedProviderGroupIntoView() {
        val groups = List(30) { CatalogueCategory("Group $it", 1) }
        val partition = CatalogueBrowsePartition.PlaylistGroup(groups[24].name)
        showWall(partition, groups = groups)
        pressLeftFromFirstPoster()
        assertFocus("catalogue-v2-group-${groups[24].name.hashCode()}")
    }

    @Test
    fun leftReturnsToTheSelectedGenreEvenWithoutHistory() {
        val facets = CatalogueGenre.entries.map { CatalogueBrowseFacet(CatalogueBrowsePartition.Genre(it), 1) }
        val selected = facets.last().partition as CatalogueBrowsePartition.Genre
        showWall(selected, facets = facets, historyEnabled = false)
        pressLeftFromFirstPoster()
        assertFocus("catalogue-v2-genre-genre:${selected.genre.wireValue}")
    }

    @Test
    fun seriesCustomFilterRetainsTheProviderBadgeAndLeftReturnsToThatFilter() {
        val facets = List(24) { CatalogueBrowseFacet(CatalogueBrowsePartition.CustomGroup("saved-$it"), null, "Saved $it") }
        showWall(facets.last().partition, facets = facets, mode = CatalogueMode.SERIES)
        composeRule.onNodeWithText("HDR10", useUnmergedTree = true).assertIsDisplayed()
        pressLeftFromFirstPoster()
        assertFocus("catalogue-v2-genre-custom:saved-23")
    }

    @Test
    fun leftHasAnOptionsFallbackWhenNoRailDestinationExists() {
        showWall(CatalogueBrowsePartition.PlaylistGroup(null), historyEnabled = false)
        pressLeftFromFirstPoster()
        assertFocus("catalogue-v2-options")
    }

    private fun showWall(
        partition: CatalogueBrowsePartition,
        groups: List<CatalogueCategory> = emptyList(),
        facets: List<CatalogueBrowseFacet> = emptyList(),
        historyEnabled: Boolean = true,
        mode: CatalogueMode = CatalogueMode.MOVIES,
    ) {
        val entry = CatalogueBrowseEntry(
            contentKey = "entry", target = if (mode == CatalogueMode.MOVIES) CatalogueBrowseTarget.Movie("source", "1") else CatalogueBrowseTarget.Series("source", "1"),
            providerTitle = TITLE, playlistGroup = null, providerPosterUrl = null,
            year = 2024, rating = null, genres = emptySet(), metadataOverride = null,
        )
        composeRule.setContent { StreamMateTheme {
            CatalogueBrowserV2Screen(
                state = CatalogueBrowserState(
                    mode = mode,
                    grouping = if (facets.isEmpty()) CatalogueGrouping.PLAYLIST else CatalogueGrouping.GENRE,
                    playlistGroups = groups, playlistGroupsReady = true,
                    genreFacets = facets, genreFacetsReady = true,
                    selectedPartition = partition,
                    wall = CatalogueBrowserWall(CatalogueBrowseRequest(mode, partition), null, listOf(entry)),
                    wallLoadState = CatalogueWallLoadState.READY,
                ),
                historyEnabled = historyEnabled,
                onSelectPartition = {}, onOpenEntry = {}, onBack = {},
            )
        } }
        composeRule.waitForIdle()
    }

    private fun pressLeftFromFirstPoster() {
        composeRule.onNodeWithText(TITLE).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithText(TITLE).assertIsFocused()
        composeRule.onNodeWithText(TITLE).performKeyInput { pressKey(Key.DirectionLeft) }
    }

    private fun assertFocus(tag: String) {
        composeRule.awaitUntil { runCatching { composeRule.onNodeWithTag(tag).assertIsFocused() }.isSuccess }
        composeRule.onNodeWithTag(tag).assertIsDisplayed().assertIsFocused()
    }

    private companion object { const val TITLE = "Title HDR10" }
}
