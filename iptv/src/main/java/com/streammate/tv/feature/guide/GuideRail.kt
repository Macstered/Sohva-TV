package com.streammate.tv.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.iptv.R

/**
 * The group rail: favourites, everything, the lists someone has made and the
 * groups the provider ships.
 *
 * "All channels" is a state of its own rather than a shortcut to the first
 * group. The guide used to pick a group on arrival, which meant there was no
 * way to see the whole line-up at once and no way back to it.
 */
@Composable
internal fun GuideGroupRail(
    channelFilter: ChannelFilter,
    selectedGroup: String?,
    selectedListId: String?,
    groups: List<String>,
    allGroups: List<String>,
    groupCounts: Map<String, Int>,
    customLists: List<Pair<String, String>>,
    showFavourites: Boolean = true,
    showRecent: Boolean = true,
    manualPositions: Map<String, Long> = emptyMap(),
    favouriteCount: Int,
    allChannelsCount: Int,
    hiddenGroups: Set<String>,
    categoryEditMode: Boolean,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onShowFavourites: () -> Unit,
    onShowRecent: () -> Unit,
    onShowAllChannels: () -> Unit,
    onGroup: (String) -> Unit,
    onCustomList: (String) -> Unit,
    onToggleGroupHidden: (String) -> Unit,
    onOpenOptions: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onExitToGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    val listState = rememberLazyListState()
    val allChannelsSelected = channelFilter == ChannelFilter.ALL &&
        selectedGroup == null &&
        selectedListId == null
    val railKeys = buildList {
        if (showFavourites) add("favourites")
        add("all")
        if (showRecent) add("recent")
        addAll(customLists.map { "list:" + it.first })
        addAll(groups.map { "group:$it" })
    }.let { keys -> if (manualPositions.isEmpty()) keys else keys.sortedBy { if (it == "all") Long.MIN_VALUE else manualPositions[it] ?: Long.MAX_VALUE } }
    val selectedKey = when {
        channelFilter == ChannelFilter.FAVOURITES -> "favourites"
        allChannelsSelected -> "all"
        channelFilter == ChannelFilter.RECENT -> "recent"
        selectedListId != null -> "list:$selectedListId"
        selectedGroup != null -> "group:$selectedGroup"
        else -> null
    }
    val selectedIndex = railKeys.indexOf(selectedKey).takeIf { it >= 0 }
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.scrollToItem(it) }
    }
    Column(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                onExitToGuide()
                true
            } else {
                false
            }
        },
    ) {
        Text(
            text = stringResource(R.string.guide_groups),
            modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            color = palette.textDim,
            fontSize = typography.overline.fontSize,
            lineHeight = typography.overline.lineHeight,
            fontWeight = FontWeight.Bold,
            letterSpacing = typography.overline.letterSpacing,
        )
        // Everything that is not a group lives behind this: sources, sorting,
        // hiding categories, channel editing, settings, back. One control
        // rather than a toolbar, so the guide itself gets the screen. It sits
        // on its own line because the rail is narrow and Finnish words are
        // long enough to push a label off the end of a shared row.
        TvActionButton(
            label = stringResource(R.string.guide_options),
            icon = TvIcons.Info,
            onClick = onOpenOptions,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            compact = true,
            // Somewhere for left off the grid to land while nothing in the
            // list is selected - in category editing, or with no groups at
            // all. An unattached requester on that edge throws.
            focusRequester = selectedItemFocusRequester.takeIf { selectedIndex == null },
            testTag = "guide-options",
        )
        if (searchVisible) {
            TvUrlField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = stringResource(R.string.guide_search_hint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                testTag = "guide-search-field",
                keyboardType = KeyboardType.Text,
                compact = true,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            items(if (categoryEditMode) allGroups.map { "group:$it" } else railKeys, key = { it }) { key ->
                val group = key.removePrefix("group:")
                val listId = key.removePrefix("list:")
                val hidden = hiddenGroups.any { it.equals(group, ignoreCase = true) }
                val selected = !categoryEditMode && key == selectedKey
                val label = when (key) {
                    "favourites" -> stringResource(R.string.guide_filter_favourites)
                    "recent" -> stringResource(R.string.guide_filter_recent)
                    "all" -> stringResource(R.string.guide_all_channels)
                    else -> if (key.startsWith("list:")) customLists.firstOrNull { it.first == listId }?.second.orEmpty() else group
                }
                TvListRow(
                    label = label,
                    icon = when { categoryEditMode -> if (hidden) TvIcons.Close else TvIcons.Check; key == "favourites" -> TvIcons.Star; else -> null },
                    trailing = when {
                        categoryEditMode -> stringResource(if (hidden) R.string.guide_category_hidden else R.string.guide_category_visible)
                        key == "favourites" -> favouriteCount.takeIf { it > 0 }?.toString()
                        key == "all" -> allChannelsCount.takeIf { it > 0 }?.toString()
                        key.startsWith("group:") -> groupCounts[group]?.toString()
                        else -> null
                    },
                    onClick = {
                        when {
                            categoryEditMode -> onToggleGroupHidden(group)
                            key == "favourites" -> onShowFavourites()
                            key == "recent" -> onShowRecent()
                            key == "all" -> onShowAllChannels()
                            key.startsWith("list:") -> onCustomList(listId)
                            else -> onGroup(group)
                        }
                    },
                    selected = selected,
                    focusRequester = if (selected) selectedItemFocusRequester else null,
                    testTag = when { key.startsWith("list:") -> "guide-list-$listId"; key.startsWith("group:") -> "guide-group-${group.hashCode()}"; else -> "guide-filter-$key" },
                    dense = true,
                )
            }
        }
    }
}
