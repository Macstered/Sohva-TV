package com.sohva.tv.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Channel management (spec 21 §5): header, toolbar, group chips, the new-list row, then the
 * channel list and the editor side by side. Back goes through the window (the app pops it).
 */
@Composable
fun ChannelsScreen(model: ChannelsModel, onBack: () -> Unit) = trace("Channels:Screen") {
    ScreenBackground(Modifier.fillMaxSize().testTag("screen-channels")) {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            SohvaTvBrand()
            Header(onBack)
            Spacer(Modifier.height(10.dp))
            Toolbar(model)
            Spacer(Modifier.height(7.dp))
            GroupChips(model)
            Spacer(Modifier.height(7.dp))
            NewListRow(model)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelListPane(model, Modifier.width(350.dp).fillMaxHeight())
                ChannelEditor(model, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                stringResource(R.string.channels_title),
                style = Sohva.typography.display.copy(fontSize = 32.sp, fontWeight = FontWeight.Black),
                color = Sohva.palette.textPrimary,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.channels_subtitle),
                style = Sohva.typography.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
                color = Sohva.palette.textMuted,
                maxLines = 1,
            )
        }
        TvActionButton(stringResource(R.string.action_back), onBack, Modifier.testTag("channels-back"), icon = TvIcons.Back)
    }
}

/** Source, Sort, Show hidden and search (CHAN-FR-03…06). */
@Composable
private fun Toolbar(model: ChannelsModel) {
    val filter by model.filter.collectAsStateWithLifecycle()
    val sources by model.sources.collectAsStateWithLifecycle()
    val search by model.searchText.collectAsStateWithLifecycle()
    val sourceName = sources.firstOrNull { it.id == filter.sourceId }?.name ?: stringResource(R.string.guide_filter_all)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TvActionButton(stringResource(R.string.channels_source, sourceName), model::cycleSource, Modifier.testTag("channels-source"))
        val sort = stringResource(if (filter.sort == ChannelSort.PLAYLIST) R.string.guide_sort_playlist else R.string.guide_sort_name)
        TvActionButton(stringResource(R.string.channels_sort, sort), model::toggleSort, Modifier.testTag("channels-sort"))
        TvActionButton(
            stringResource(R.string.channels_show_hidden),
            model::toggleShowHidden,
            Modifier.testTag("channels-show-hidden"),
            state = SurfaceState(selected = filter.showHidden),
        )
        TvUrlField(
            search,
            model::setSearch,
            stringResource(R.string.channels_search_hint),
            Modifier.weight(1f).testTag("channels-search"),
            icon = TvIcons.Search,
            input = FieldInput(keyboard = KeyboardType.Text),
        )
    }
}

/** All, then the source's groups; the chosen chip is marked "●" (CHAN-FR-07). */
@Composable
private fun GroupChips(model: ChannelsModel) {
    val groups by model.groups.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val all = stringResource(R.string.guide_filter_all)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.testTag("channels-groups")) {
        item(key = "@all") {
            TvActionButton(if (filter.groupName == null) "● $all" else all, { model.chooseGroup(null) }, compact = true)
        }
        items(groups, key = { it }) { name ->
            TvActionButton(if (filter.groupName == name) "● $name" else name, { model.chooseGroup(name) }, compact = true)
        }
    }
}

/** A name field and Create list (CHAN-FR-50). */
@Composable
private fun NewListRow(model: ChannelsModel) {
    var name by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TvUrlField(
            name,
            { name = it.take(NAME_MAX) },
            stringResource(R.string.channels_new_list_hint),
            Modifier.weight(1f).testTag("channels-new-list"),
            input = FieldInput(keyboard = KeyboardType.Text),
        )
        TvActionButton(
            stringResource(R.string.channels_create_list),
            {
                if (name.isNotBlank()) {
                    model.createList(name)
                    name = ""
                }
            },
            Modifier.testTag("channels-create-list"),
        )
    }
}

private const val NAME_MAX = 100
