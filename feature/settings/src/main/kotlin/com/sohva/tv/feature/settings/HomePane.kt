package com.sohva.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.SettingsGroup
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.SettingsSwitchRow
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.moveKeys
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.launch

/**
 * Settings › Home (spec 02 HOME-FR-90): the active profile's Home rows. Reorder picks a row up
 * with OK, moves it with Up/Down (Page Up/Down, Home/End) and places it with OK, Back cancels, as
 * Discover's catalogue order does; Show / hide has one switch per row; Reset to default. Focus stays
 * on the moving row and comes back to it after Back.
 */
@Composable
internal fun HomePane(home: HomeLayoutSettings, start: FocusRequester) {
    val s by home.state.collectAsStateWithLifecycle()
    val view = s.view ?: return
    val scope = rememberCoroutineScope()
    val rows = remember { HashMap<String, FocusRequester>() }
    fun row(id: String) = rows.getOrPut(id) { FocusRequester() }
    BackHandler(enabled = s.moving != null) {
        val moved = s.moving ?: return@BackHandler
        home.cancelMove()
        scope.launch { row(moved).requestFocusWhenAttached() }
    }
    SettingsGroup {
        SettingsOverline(stringResource(R.string.home_nav_home))
        Text(
            view.profileName?.let { stringResource(R.string.home_layout_for_profile, it) } ?: stringResource(R.string.home_layout_intro),
            Modifier.padding(horizontal = 14.dp).testTag("settings-home-intro"),
            style = Sohva.typography.body,
            color = Sohva.palette.textMuted,
        )
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                stringResource(R.string.addon_ui_reorder), { home.mode(HomeLayoutMode.ORDER) }, Modifier.focusRequester(start).testTag("settings-home-order"),
                TvIcons.Guide, SurfaceState(selected = s.mode == HomeLayoutMode.ORDER), compact = true,
            )
            TvActionButton(
                stringResource(R.string.addon_ui_show_hide), { home.mode(HomeLayoutMode.VISIBILITY) }, Modifier.testTag("settings-home-visibility"),
                TvIcons.Channels, SurfaceState(selected = s.mode == HomeLayoutMode.VISIBILITY), compact = true,
            )
            TvActionButton(stringResource(R.string.home_layout_reset), home::reset, Modifier.testTag("settings-home-reset"), TvIcons.Refresh, compact = true)
        }
        Text(
            stringResource(
                when {
                    s.moving != null -> R.string.addon_ui_move_controls
                    s.mode == HomeLayoutMode.ORDER -> R.string.home_layout_order_help
                    else -> R.string.home_layout_visibility_help
                },
            ),
            Modifier.padding(horizontal = 14.dp),
            style = Sohva.typography.label,
            color = Sohva.palette.textDim,
        )
        s.order.forEachIndexed { index, id ->
            val shown = view.layout.isShown(id)
            if (s.mode == HomeLayoutMode.VISIBILITY) {
                SettingsSwitchRow(stringResource(rowTitle(id)), shown, { home.toggle(id) }, Modifier.focusRequester(row(id)).testTag("settings-home-switch-$id"))
            } else {
                val moving = s.moving == id
                val keys = if (!moving) Modifier else Modifier.moveKeys({ ROWS_PER_PAGE }, { delta, to -> home.move(delta, to) }, home::place)
                TvListRow(
                    stringResource(rowTitle(id)), { home.pickUp(id) },
                    keys.focusRequester(row(id)).testTag("settings-home-row-$id"),
                    supporting = when {
                        moving -> stringResource(R.string.addon_ui_moving_position, index + 1)
                        !shown -> stringResource(R.string.addon_ui_hidden)
                        else -> null
                    },
                    trailing = "${index + 1}",
                    state = SurfaceState(selected = moving),
                    layout = ListRowLayout(divider = true),
                )
            }
        }
    }
    // Focus follows the moving row (HOME-FR-90); the pane scrolls it into view by itself.
    LaunchedEffect(s.moving, s.order) {
        val id = s.moving ?: return@LaunchedEffect
        row(id).requestFocusWhenAttached()
    }
}

/** Home's own row titles (spec 02 HOME-FR-02). */
@StringRes
private fun rowTitle(id: String): Int = when (id) {
    HomeLayout.CONTINUE -> R.string.home_continue_watching
    HomeLayout.WATCH_NEXT -> R.string.home_watch_next
    HomeLayout.SPORT -> R.string.home_sports_today
    HomeLayout.RECOMMENDED -> R.string.home_recommended
    else -> R.string.home_recent_channels
}

/** Page Up / Page Down in a list of a handful of rows: to the other end. */
private const val ROWS_PER_PAGE = 4
