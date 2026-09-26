package com.sohva.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.sport.SportFeedStatus
import com.sohva.tv.core.model.sport.SportServiceState
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.SportsProblem
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.FieldInput
import com.sohva.tv.ui.design.components.PickerRow
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvUrlField
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

private val KEY_FIELD = FieldInput(keyboard = KeyboardType.Password, transformation = PasswordVisualTransformation(), compact = true)
private val CODES_FIELD = FieldInput(keyboard = KeyboardType.Text, compact = true)

/**
 * Settings › Sohva Sport (spec 60 §4.1, design/screens/settings.md §6): the channel order first
 * (Save order takes the section's focus, S§8), then the key and the followed sports and
 * competitions, with the section's own status and messages (SPORT-FR-13).
 */
@Composable
internal fun SportPane(sport: SportSettings, start: FocusRequester) {
    val state by sport.state.collectAsStateWithLifecycle()
    PriorityGroup(sport, state, start)
    SettingsGroup {
        SettingsOverline(stringResource(R.string.sports_settings_title))
        Text(stringResource(R.string.sports_settings_description), Modifier.padding(horizontal = 14.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        val status by sport.status.collectAsStateWithLifecycle()
        status?.let { Text(statusLine(it), Modifier.padding(horizontal = 14.dp).testTag("settings-sport-status"), style = Sohva.typography.caption, color = Sohva.palette.textMuted) }
        KeyRow(sport, state)
        message(state.message)?.let { (text, bad) ->
            Text(text, Modifier.padding(horizontal = 14.dp).testTag("settings-sport-message"), style = Sohva.typography.caption, color = if (bad) Sohva.palette.danger else Sohva.palette.focus)
        }
        FollowArea(sport, state)
    }
}

@Composable
private fun PriorityGroup(sport: SportSettings, state: SportSettingsState, start: FocusRequester) {
    SettingsGroup {
        SettingsOverline(stringResource(R.string.sports_channel_priority_title))
        Text(stringResource(R.string.sports_channel_priority_help), Modifier.padding(horizontal = 14.dp), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvUrlField(
                state.codes, sport::typeCodes, stringResource(R.string.sports_channel_priority_codes),
                Modifier.weight(1f).testTag("settings-sport-codes"), TvIcons.Guide, CODES_FIELD,
            )
            TvActionButton(
                stringResource(R.string.sports_channel_priority_save), sport::saveCodes,
                Modifier.focusRequester(start).testTag("settings-sport-codes-save"), TvIcons.Save, compact = true,
            )
        }
    }
}

@Composable
private fun KeyRow(sport: SportSettings, state: SportSettingsState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TvUrlField(state.typedKey, sport::typeKey, stringResource(R.string.sports_api_key), Modifier.weight(1f).testTag("settings-sport-key"), TvIcons.Key, KEY_FIELD)
        TvActionButton(
            stringResource(if (state.typedKey.isBlank()) R.string.sports_remove_key else R.string.sports_save_key), sport::saveKey,
            Modifier.testTag("settings-sport-key-save"), TvIcons.Save, compact = true,
        )
    }
}

/**
 * No key: why the menu is closed. With a key the opener stays in place and the menu opens below it,
 * so the menu's Back can put focus on the opener before the menu hides (AGENTS.md §5 rule 2, S§8).
 */
@Composable
private fun FollowArea(sport: SportSettings, state: SportSettingsState) {
    val hasKey by sport.hasKey.collectAsStateWithLifecycle()
    val opener = remember { FocusRequester() }
    if (!hasKey) {
        Text(stringResource(R.string.sports_follow_requires_key), Modifier.padding(horizontal = 14.dp).testTag("settings-sport-needs-key"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        return
    }
    TvActionButton(
        stringResource(R.string.sports_follow_open), { sport.openMenu(!state.menuOpen) },
        Modifier.padding(horizontal = 14.dp).focusRequester(opener).testTag("settings-sport-follow-open"), TvIcons.Target,
        SurfaceState(selected = state.menuOpen), compact = true,
    )
    if (state.menuOpen) {
        FollowMenu(sport, state) {
            opener.requestFocus()
            sport.openMenu(false)
        }
    }
}

@Composable
private fun FollowMenu(sport: SportSettings, state: SportSettingsState, close: () -> Unit) {
    val follows by sport.follows.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.sports_follow_title), Modifier.weight(1f), style = Sohva.typography.bodyLarge, color = Sohva.palette.textPrimary)
        TvActionButton(stringResource(R.string.action_back), close, Modifier.testTag("settings-sport-follow-back"), TvIcons.Back, compact = true)
    }
    LazyRow(Modifier.fillMaxWidth().testTag("settings-sport-sports"), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
        items(SportType.entries, key = { it.name }) { type ->
            // Followed sports are drawn selected; the one being edited carries a mark (SPORT-FR-04 rebuild).
            TvActionButton(
                stringResource(sportLabel(type)), { sport.edit(type) },
                (if (type == SportType.FOOTBALL) Modifier.focusRequester(first) else Modifier).testTag("settings-sport-${type.name.lowercase()}"),
                if (type == state.editing) TvIcons.ChevronDown else null, SurfaceState(selected = follows.follows(type)), compact = true,
            )
        }
    }
    val editing = state.editing
    TvActionButton(
        stringResource(if (follows.follows(editing)) R.string.sports_follow_disable_sport else R.string.sports_follow_enable_sport), sport::toggleSport,
        Modifier.padding(horizontal = 14.dp).testTag("settings-sport-toggle"), compact = true,
    )
    CompetitionPanel(sport, state, follows.competitions)
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}

@Composable
private fun CompetitionPanel(sport: SportSettings, state: SportSettingsState, followed: Set<String>) {
    val editing = state.editing
    val pad = Modifier.padding(horizontal = 14.dp)
    if (!editing.hasCompetitions) {
        Text(stringResource(R.string.sports_competitions_none), pad.testTag("settings-sport-no-competitions"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        return
    }
    when (val list = state.lists[editing]) {
        null, CompetitionList.Loading ->
            Text(stringResource(R.string.sports_competitions_loading), pad.testTag("settings-sport-competitions-loading"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
        CompetitionList.Failed -> Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sports_competitions_error), Modifier.weight(1f), style = Sohva.typography.label, color = Sohva.palette.danger)
            TvActionButton(stringResource(R.string.action_retry), sport::retry, Modifier.testTag("settings-sport-competitions-retry"), TvIcons.Refresh, compact = true)
        }
        is CompetitionList.Loaded -> {
            val count = list.all.count { it.key in followed }
            Text(stringResource(R.string.sports_competitions_count, count, list.all.size), pad.testTag("settings-sport-competitions-count"), style = Sohva.typography.label, color = Sohva.palette.textMuted)
            val rows = remember(list, followed, state.search) { SportSettings.visible(list.all, followed, state.search) }
            Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvUrlField(
                    state.search, sport::search, stringResource(R.string.sports_competitions_search),
                    Modifier.fillMaxWidth(0.62f).testTag("settings-sport-competitions-search"), TvIcons.Search, FieldInput(keyboard = KeyboardType.Text, compact = true),
                )
                if (state.search.isNotBlank()) Text(stringResource(R.string.sports_competitions_results, rows.size), style = Sohva.typography.caption, color = Sohva.palette.textMuted)
            }
            LazyColumn(Modifier.fillMaxWidth().height(245.dp).testTag("settings-sport-competitions")) {
                items(rows, key = { it.key }) { c ->
                    PickerRow(
                        listOfNotNull(c.name, c.country).joinToString(" · "), { sport.toggleCompetition(c) },
                        Modifier.testTag("settings-sport-competition-${c.key}"), state = SurfaceState(selected = c.key in followed),
                    )
                }
            }
        }
    }
}

@Composable
private fun message(message: SportMessage?): Pair<String, Boolean>? = when (message) {
    null -> null
    SportMessage.KEY_SAVED -> stringResource(R.string.sports_key_saved) to false
    SportMessage.KEY_REMOVED -> stringResource(R.string.sports_key_removed) to false
    SportMessage.PRIORITY_SAVED -> stringResource(R.string.sports_channel_priority_saved) to false
    SportMessage.KEY_INVALID -> stringResource(R.string.error_api_sports_key_invalid) to true
    SportMessage.SAVE_FAILED -> stringResource(R.string.error_api_sports_settings_save) to true
}

/** SPORT-FR-12: zone · refreshing, the failure or the service state · quota per sport · polling. */
@Composable
private fun statusLine(status: SportFeedStatus): String {
    val parts = ArrayList<String>()
    parts += stringResource(R.string.today_subtitle_base, status.zoneId)
    val problem = status.problem
    val service = status.state
    val middle = when {
        status.refreshing -> stringResource(R.string.today_subtitle_refreshing)
        problem != null -> sportsProblemText(problem)
        service != null -> stringResource(
            R.string.today_subtitle_cache,
            stringResource(
                when (service) {
                    SportServiceState.CACHE -> R.string.cache_hit
                    SportServiceState.UPDATED -> R.string.cache_miss
                    SportServiceState.STALE -> R.string.cache_stale
                    SportServiceState.MIXED -> R.string.cache_mixed
                },
            ),
        )
        else -> null
    }
    middle?.let { parts += it }
    if (status.quotas.isNotEmpty()) {
        val pairs = status.quotas.entries.sortedBy { it.key.provider }.joinToString(" / ") { "${it.key.quotaLabel} ${it.value}" }
        parts += stringResource(R.string.today_subtitle_quota, pairs)
    }
    parts += stringResource(R.string.today_subtitle_polling, status.pollingMinutes)
    return parts.joinToString(" · ")
}

/** The provider's failures in the spec's words (§7), the quota named apart (rebuild). */
@Composable
internal fun sportsProblemText(problem: SportsProblem): String = when (problem) {
    SportsProblem.KEY_MISSING -> stringResource(R.string.error_api_sports_key_missing)
    SportsProblem.HTTP -> stringResource(R.string.error_api_sports_unavailable)
    SportsProblem.TOO_LARGE -> stringResource(R.string.error_api_sports_response_too_large)
    SportsProblem.UNAVAILABLE -> stringResource(R.string.error_api_sports_unavailable)
    SportsProblem.SERVICE_ERROR -> stringResource(R.string.error_api_sports_service_error)
    SportsProblem.INVALID_DATA -> stringResource(R.string.error_api_sports_invalid_data)
    SportsProblem.QUOTA_EXHAUSTED -> stringResource(R.string.error_api_sports_quota)
}

internal fun sportLabel(type: SportType): Int = when (type) {
    SportType.FOOTBALL -> R.string.sports_follow_football
    SportType.ICE_HOCKEY -> R.string.sports_follow_hockey
    SportType.AUSTRALIAN_FOOTBALL -> R.string.sports_follow_afl
    SportType.BASKETBALL -> R.string.sports_follow_basketball
    SportType.BASEBALL -> R.string.sports_follow_baseball
    SportType.HANDBALL -> R.string.sports_follow_handball
    SportType.RUGBY -> R.string.sports_follow_rugby
    SportType.VOLLEYBALL -> R.string.sports_follow_volleyball
    SportType.AMERICAN_FOOTBALL -> R.string.sports_follow_nfl
    SportType.MMA -> R.string.sports_follow_mma
    SportType.FORMULA_1 -> R.string.sports_follow_formula1
    SportType.NBA -> R.string.sports_follow_nba
}
