package com.sohva.tv.feature.discover.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.NavIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The landing rail's items, top to bottom (FR-68). */
enum class RailTarget(val tag: String) {
    HOME("discover-rail-home"),
    LIBRARY("discover-rail-library"),
    SEARCH("discover-rail-search"),
    DISCOVER("discover-rail-discover"),
    SETUP("discover-rail-setup"),
    LEAVE("discover-rail-leave"),
}

/**
 * The icon rail over the landing's left edge (FR-68, §5.1): it overlays the shelves instead of
 * moving them. Focus expands it (labels appear); focusing an item never opens it, OK does. Right,
 * Back, or OK on Home return focus to the shelves through [back].
 */
@Composable
fun DiscoverRail(requesters: Map<RailTarget, FocusRequester>, open: (RailTarget) -> Unit, back: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val p = Sohva.palette
    BackHandler(enabled = expanded) { back() }
    Column(
        modifier.fillMaxHeight().width(if (expanded) 218.dp else 64.dp)
            .background(p.background.copy(alpha = if (expanded) 0.98f else 0.65f))
            .padding(horizontal = 8.dp, vertical = 24.dp)
            .onFocusChanged { expanded = it.hasFocus }
            .onPreviewKeyEvent { e -> (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight).also { if (it) back() } }
            .focusGroup()
            .testTag("discover-rail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (target in RailTarget.entries) {
            if (target == RailTarget.LEAVE) Spacer(Modifier.weight(1f))
            val (icon, label) = when (target) {
                RailTarget.HOME -> NavIcons.AddonHome to stringResource(R.string.home_nav_home)
                RailTarget.LIBRARY -> NavIcons.Library to stringResource(R.string.settings_section_metadata)
                RailTarget.SEARCH -> NavIcons.Search to stringResource(R.string.home_search)
                RailTarget.DISCOVER -> NavIcons.Explore to stringResource(R.string.addon_title)
                RailTarget.SETUP -> NavIcons.Addons to stringResource(R.string.addon_ui_addons_setup)
                RailTarget.LEAVE -> NavIcons.BackToHome to stringResource(R.string.addon_back)
            }
            val style = SurfaceStyle(corner = Sohva.shapes.small, focusScale = 1f, padding = PaddingValues(horizontal = 12.dp))
            TvSurface(
                { if (target == RailTarget.HOME) back() else open(target) },
                Modifier.fillMaxWidth().height(48.dp).focusRequester(requesters.getValue(target)).testTag(target.tag),
                style = style,
            ) { colors ->
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, size = 24.dp, tint = colors.content)
                    if (expanded) {
                        Spacer(Modifier.width(14.dp))
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Sohva.typography.label, color = colors.content)
                    }
                }
            }
        }
    }
}
