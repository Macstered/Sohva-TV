package com.streammate.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.iptv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.iptv.repository.EditableChannel
import com.streammate.tv.iptv.repository.CustomChannelList
import com.streammate.tv.iptv.repository.ChannelListMembership
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.XmlTvChannelOption
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class ChannelEditorSort {
    PLAYLIST,
    NAME,
}

@Composable
fun ChannelEditorScreen(
    guideRepository: GuideRepository,
    preferencesRepository: AppPreferencesRepository,
    onBack: () -> Unit,
) {
    val palette = StreamMateThemeTokens.palette
    val channels by guideRepository.observeEditableChannels()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val customLists by guideRepository.observeCustomChannelLists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listMemberships by guideRepository.observeChannelListMemberships()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val appPreferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
    )
    val coroutineScope = rememberCoroutineScope()
    val allLabel = stringResource(R.string.guide_filter_all)
    val listCreatedMessage = stringResource(R.string.channels_list_created)
    val savedMessage = stringResource(R.string.channels_saved)
    val shownMessage = stringResource(R.string.channels_shown)
    val hiddenMessage = stringResource(R.string.channels_hidden)
    val lockedMessage = stringResource(R.string.channels_locked)
    val unlockedMessage = stringResource(R.string.channels_unlocked)
    val orderUpdatedMessage = stringResource(R.string.channels_order_updated)
    val resetDoneMessage = stringResource(R.string.channels_reset_done)
    val addedToListMessage = stringResource(R.string.channels_added_to_list)
    val removedFromListMessage = stringResource(R.string.channels_removed_from_list)
    val listDeletedMessage = stringResource(R.string.channels_list_deleted)
    val firstFocus = remember { FocusRequester() }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(ChannelEditorSort.PLAYLIST) }
    var searchQuery by remember { mutableStateOf("") }
    var newListName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val sources = channels.distinctBy(EditableChannel::sourceId)
    val sourceChannels = channels.filter { selectedSourceId == null || it.sourceId == selectedSourceId }
    val groups = sourceChannels.mapNotNull(EditableChannel::displayGroupTitle).distinct()
    val filteredChannels = sourceChannels.filter { channel ->
        (selectedGroup == null || channel.displayGroupTitle == selectedGroup) &&
            (searchQuery.isBlank() ||
                channel.displayName.contains(searchQuery, ignoreCase = true) ||
                channel.displayGroupTitle.orEmpty().contains(searchQuery, ignoreCase = true))
    }.let { filtered ->
        when (sortMode) {
            ChannelEditorSort.PLAYLIST -> filtered.sortedWith(
                compareBy<EditableChannel> { it.sortOrder ?: Int.MAX_VALUE }
                    .thenBy { it.playlistOrder }
                    .thenBy { it.displayName.lowercase() },
            )
            ChannelEditorSort.NAME -> filtered.sortedBy { it.displayName.lowercase() }
        }
    }
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId }
    val xmlTvOptionsFlow = remember(selectedChannel?.sourceId) {
        selectedChannel?.sourceId?.let(guideRepository::observeXmlTvChannelOptions)
            ?: flowOf(emptyList())
    }
    val xmlTvOptions by xmlTvOptionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(filteredChannels.map(EditableChannel::id)) {
        if (selectedChannelId == null || filteredChannels.none { it.id == selectedChannelId }) {
            selectedChannelId = filteredChannels.firstOrNull()?.id
        }
    }
    LaunchedEffect(groups, selectedSourceId) {
        if (selectedGroup != null && selectedGroup !in groups) selectedGroup = null
    }
    LaunchedEffect(filteredChannels.firstOrNull()?.id) {
        if (filteredChannels.isNotEmpty()) firstFocus.requestFocusWhenAttached()
    }

    StreamMateScreenBackground { modifier ->
        Column(modifier = modifier) {
            SohvaTvBrand()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = stringResource(R.string.channels_title), fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = stringResource(R.string.channels_subtitle),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                    )
                }
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    onClick = onBack,
                    testTag = "channel-editor-back",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvActionButton(
                    label = stringResource(
                        R.string.channels_source,
                        selectedSourceId?.let { id ->
                            sources.firstOrNull { it.sourceId == id }?.sourceName
                        } ?: allLabel,
                    ),
                    onClick = {
                        selectedSourceId = nextValue(sources.map(EditableChannel::sourceId), selectedSourceId)
                        selectedGroup = null
                    },
                    testTag = "channel-editor-source",
                )
                TvActionButton(
                    label = stringResource(
                        R.string.channels_sort,
                        stringResource(
                            if (sortMode == ChannelEditorSort.PLAYLIST) R.string.guide_sort_playlist
                            else R.string.guide_sort_name,
                        ),
                    ),
                    onClick = {
                        sortMode = if (sortMode == ChannelEditorSort.PLAYLIST) {
                            ChannelEditorSort.NAME
                        } else {
                            ChannelEditorSort.PLAYLIST
                        }
                    },
                    testTag = "channel-editor-sort",
                )
                TvUrlField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(MAX_TEXT_LENGTH) },
                    label = stringResource(R.string.channels_search_hint),
                    modifier = Modifier.weight(1f),
                testTag = "channel-editor-search",
                editOnClickOnly = true,
                    leadingIcon = "⌕",
                    keyboardType = KeyboardType.Text,
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TvActionButton(
                    label = if (selectedGroup == null) "● $allLabel" else allLabel,
                    onClick = { selectedGroup = null },
                    compact = true,
                    testTag = "channel-editor-group-all",
                )
                groups.forEach { group ->
                    TvActionButton(
                        label = if (selectedGroup == group) "● $group" else group,
                        onClick = { selectedGroup = group },
                        compact = true,
                        testTag = "channel-editor-group-${group.hashCode()}",
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TvUrlField(
                    value = newListName,
                    onValueChange = { newListName = it.take(MAX_TEXT_LENGTH) },
                    label = stringResource(R.string.channels_new_list_hint),
                    modifier = Modifier.weight(1f),
                testTag = "channel-editor-new-list-name",
                editOnClickOnly = true,
                    leadingIcon = "+",
                    keyboardType = KeyboardType.Text,
                )
                TvActionButton(
                    label = stringResource(R.string.channels_create_list),
                    onClick = {
                        if (newListName.isNotBlank()) {
                            coroutineScope.launch {
                                guideRepository.createCustomChannelList(newListName, customLists.size)
                                newListName = ""
                                status = listCreatedMessage
                            }
                        }
                    },
                    testTag = "channel-editor-create-list",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelList(
                    channels = filteredChannels,
                    selectedChannelId = selectedChannelId,
                    onSelect = { selectedChannelId = it.id; status = null },
                    firstFocus = firstFocus,
                    modifier = Modifier.width(350.dp).fillMaxHeight(),
                )
                ChannelEditor(
                    channel = selectedChannel,
                    xmlTvOptions = xmlTvOptions,
                    customLists = customLists,
                    listMemberships = listMemberships,
                    locked = selectedChannel?.id?.let { it in appPreferences.lockedChannelIds } == true,
                    parentalPinConfigured = appPreferences.parentalPinConfigured,
                    status = status,
                    onSave = { channel, name, group, manualEpg ->
                        coroutineScope.launch {
                            guideRepository.updateChannel(
                                channel = channel,
                                customName = name,
                                customGroupTitle = group,
                                manualXmltvChannelId = manualEpg,
                            )
                            status = savedMessage
                        }
                    },
                    onToggleHidden = { channel ->
                        coroutineScope.launch {
                            guideRepository.updateChannel(channel, hidden = !channel.hidden)
                            status = if (channel.hidden) shownMessage else hiddenMessage
                        }
                    },
                    onToggleLocked = { channel, locked ->
                        coroutineScope.launch {
                            preferencesRepository.setChannelLocked(channel.id, locked)
                            status = if (locked) lockedMessage else unlockedMessage
                        }
                    },
                    onMove = { channel, delta ->
                        val index = channels.indexOfFirst { it.id == channel.id }
                        val destination = (index + delta).coerceIn(channels.indices)
                        if (index >= 0 && destination != index) {
                            val reordered = channels.toMutableList().apply {
                                add(destination, removeAt(index))
                            }
                            coroutineScope.launch {
                                guideRepository.reorderChannels(reordered)
                                status = orderUpdatedMessage
                            }
                        }
                    },
                    onReset = { channel ->
                        coroutineScope.launch {
                            guideRepository.resetChannel(channel.id)
                            status = resetDoneMessage
                        }
                    },
                    onToggleListMembership = { channel, list, member ->
                        coroutineScope.launch {
                            val memberCount = listMemberships.count { it.listId == list.id }
                            guideRepository.setCustomListMembership(list.id, channel.id, member, memberCount)
                            status = if (member) addedToListMessage else removedFromListMessage
                        }
                    },
                    onDeleteList = { list ->
                        coroutineScope.launch {
                            guideRepository.deleteCustomChannelList(list.id)
                            status = listDeletedMessage
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<EditableChannel>,
    selectedChannelId: String?,
    onSelect: (EditableChannel) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.outline.copy(alpha = 0.56f), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.channels_count, channels.size, channels.size),
            color = palette.textMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                ChannelListItem(
                    channel = channel,
                    selected = channel.id == selectedChannelId,
                    onSelect = { onSelect(channel) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ChannelListItem(
    channel: EditableChannel,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    var focused by remember(channel.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(
                when {
                    selected -> palette.surfaceRaised
                    else -> palette.surfaceSubtle
                },
            )
            .then(if (focused) Modifier.border(3.dp, palette.textPrimary, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable(onClick = onSelect)
            .focusable()
            .testTag("channel-editor-item-${channel.id}")
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelEditorLogo(channel)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (channel.hidden) palette.textMuted else palette.textPrimary,
            )
            Text(
                text = buildString {
                    append(channel.displayGroupTitle ?: channel.sourceName)
                    if (channel.hidden) {
                        append("  ·  ")
                        append(stringResource(R.string.channels_hidden_suffix))
                    }
                },
                color = if (channel.hidden) palette.accent else palette.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChannelEditor(
    channel: EditableChannel?,
    xmlTvOptions: List<XmlTvChannelOption>,
    customLists: List<CustomChannelList>,
    listMemberships: List<ChannelListMembership>,
    locked: Boolean,
    parentalPinConfigured: Boolean,
    status: String?,
    onSave: (EditableChannel, String?, String?, String?) -> Unit,
    onToggleHidden: (EditableChannel) -> Unit,
    onToggleLocked: (EditableChannel, Boolean) -> Unit,
    onMove: (EditableChannel, Int) -> Unit,
    onReset: (EditableChannel) -> Unit,
    onToggleListMembership: (EditableChannel, CustomChannelList, Boolean) -> Unit,
    onDeleteList: (CustomChannelList) -> Unit,
    modifier: Modifier,
) {
    val palette = StreamMateThemeTokens.palette
    if (channel == null) {
        Box(
            modifier = modifier.background(palette.surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.channels_select), color = palette.textMuted)
        }
        return
    }
    var customName by remember(channel.id, channel.customName) { mutableStateOf(channel.customName.orEmpty()) }
    var customGroup by remember(channel.id, channel.customGroupTitle) { mutableStateOf(channel.customGroupTitle.orEmpty()) }
    var manualEpg by remember(channel.id, channel.manualXmltvChannelId) {
        mutableStateOf(channel.manualXmltvChannelId)
    }
    var selectedListId by remember(channel.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(customLists.map(CustomChannelList::id)) {
        if (selectedListId == null || customLists.none { it.id == selectedListId }) {
            selectedListId = customLists.firstOrNull()?.id
        }
    }
    val selectedList = customLists.firstOrNull { it.id == selectedListId }
    val channelInSelectedList = selectedListId?.let { listId ->
        listMemberships.any { it.listId == listId && it.channelId == channel.id }
    } == true
    val manualEpgLabel = manualEpg?.let { id -> xmlTvOptions.firstOrNull { it.id == id }?.label ?: id }
        ?: stringResource(
            R.string.channels_automatic_epg,
            channel.tvgId ?: stringResource(R.string.channels_no_tvg_id),
        )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.outline.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelEditorLogo(channel, 64)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = channel.displayName, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(text = channel.sourceName, color = palette.focus, fontSize = 12.sp)
                Text(
                    text = stringResource(R.string.channels_original_name, channel.originalName),
                    color = palette.textMuted,
                    fontSize = 12.sp,
                )
            }
            status?.let { Text(text = it, color = palette.focus, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(12.dp))
        TvUrlField(
            value = customName,
            onValueChange = { customName = it.take(MAX_TEXT_LENGTH) },
            label = stringResource(R.string.channels_custom_name),
            modifier = Modifier.fillMaxWidth(),
            testTag = "channel-editor-name",
            editOnClickOnly = true,
            keyboardType = KeyboardType.Text,
        )
        Spacer(Modifier.height(8.dp))
        TvUrlField(
            value = customGroup,
            onValueChange = { customGroup = it.take(MAX_TEXT_LENGTH) },
            label = stringResource(R.string.channels_custom_group),
            modifier = Modifier.fillMaxWidth(),
            testTag = "channel-editor-group",
            editOnClickOnly = true,
            keyboardType = KeyboardType.Text,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = stringResource(R.string.channels_epg_mapping), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = stringResource(R.string.channels_current, manualEpgLabel),
            color = palette.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TvActionButton(
                label = stringResource(R.string.channels_change_epg),
                onClick = { manualEpg = nextValue(xmlTvOptions.map(XmlTvChannelOption::id), manualEpg) },
                testTag = "channel-editor-cycle-epg",
            )
            TvActionButton(
                label = stringResource(R.string.channels_automatic),
                onClick = { manualEpg = null },
                testTag = "channel-editor-auto-epg",
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(text = stringResource(R.string.channels_custom_lists), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (selectedList == null) {
            Text(text = stringResource(R.string.channels_create_list_help), color = palette.textMuted, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TvActionButton(
                    label = stringResource(R.string.channels_list, selectedList.name),
                    onClick = { selectedListId = nextValue(customLists.map(CustomChannelList::id), selectedListId) },
                    testTag = "channel-editor-cycle-list",
                )
                TvActionButton(
                    label = if (channelInSelectedList) {
                        stringResource(R.string.channels_remove_from_list)
                    } else {
                        stringResource(R.string.channels_add_to_list)
                    },
                    onClick = { onToggleListMembership(channel, selectedList, !channelInSelectedList) },
                    testTag = "channel-editor-toggle-list",
                )
                TvActionButton(
                    label = stringResource(R.string.channels_delete_list),
                    onClick = { onDeleteList(selectedList) },
                    testTag = "channel-editor-delete-list",
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TvActionButton(
                label = stringResource(R.string.action_save),
                icon = TvIcons.Check,
                onClick = { onSave(channel, customName, customGroup, manualEpg) },
                testTag = "channel-editor-save",
            )
            TvActionButton(
                label = if (channel.hidden) {
                    stringResource(R.string.channels_show_in_guide)
                } else {
                    stringResource(R.string.channels_hide_from_guide)
                },
                onClick = { onToggleHidden(channel) },
                testTag = "channel-editor-hidden",
            )
            TvActionButton(
                label = when {
                    !parentalPinConfigured -> stringResource(R.string.channels_configure_pin)
                    locked -> stringResource(R.string.channels_remove_pin_lock)
                    else -> stringResource(R.string.channels_lock_with_pin)
                },
                onClick = { onToggleLocked(channel, !locked) },
                enabled = parentalPinConfigured,
                testTag = "channel-editor-locked",
            )
            TvActionButton(label = "↑", onClick = { onMove(channel, -1) }, testTag = "channel-editor-up")
            TvActionButton(label = "↓", onClick = { onMove(channel, 1) }, testTag = "channel-editor-down")
            TvActionButton(
                label = stringResource(R.string.action_reset),
                onClick = { onReset(channel) },
                testTag = "channel-editor-reset",
            )
        }
    }
}

@Composable
private fun ChannelEditorLogo(channel: EditableChannel, size: Int = 38) {
    val palette = StreamMateThemeTokens.palette
    Box(
        modifier = Modifier.size(size.dp).background(palette.surfaceRaised, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (channel.logoUrl.isNullOrBlank()) {
            Text(text = channel.displayName.take(2).uppercase(), color = palette.focus, fontWeight = FontWeight.Black)
        } else {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

private fun <T> nextValue(values: List<T>, current: T?): T? {
    if (values.isEmpty()) return null
    if (current == null) return values.first()
    return values.getOrNull(values.indexOf(current) + 1)
}

private const val MAX_TEXT_LENGTH = 100
