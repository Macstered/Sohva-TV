package com.sohva.tv.feature.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.NavIcons
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.DrawColorText
import com.sohva.tv.ui.design.theme.Sohva

/** The Home rail's destinations, in rail order (spec 01 SHELL-FR-60). */
enum class RailItem(@StringRes val label: Int, @DrawableRes val icon: Int, val tag: String) {
    LIVE_TV(R.string.home_live_tv, NavIcons.LiveTv, "home-live"),
    SPORT(R.string.home_sportmate, NavIcons.Sport, "home-sportmate"),
    MOVIES(R.string.home_movies, NavIcons.Movies, "home-movies"),
    SERIES(R.string.home_series, NavIcons.Series, "home-series"),
    SEARCH(R.string.home_search, NavIcons.Search, "home-search"),
    DISCOVER(R.string.home_discover, NavIcons.Discover, "home-discover"),
    PROFILES(R.string.profile_active_title, TvIcons.Star, "home-profiles"),
    SETTINGS(R.string.home_settings, NavIcons.Settings, "home-settings"),
}

private val COLLAPSED = 80.dp
private val EXPANDED = 244.dp
private val ITEM_HEIGHT = 48.dp
private const val EXPAND_MS = 150

/**
 * The Home rail (spec 01 §5.4): icons at rest, widening over the content with labels while it
 * holds focus. Laid out once at full width in its own region; collapsing and expanding change
 * only drawn values (the scrim's width and density, label alpha, the marker's width), 150 ms,
 * snapped in reduced motion (plan/07 §4.7.8). Beta 23 re-laid out the rail every frame.
 */
@Composable
fun HomeRail(
    items: List<RailItem>,
    requesters: Map<RailItem, FocusRequester>,
    onOpen: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val t by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (Sohva.reducedMotion) snap() else tween(EXPAND_MS),
        label = "rail",
    )
    val scrim = Sohva.palette.background
    val navigation = stringResource(R.string.home_navigation)
    Box(
        modifier
            .width(EXPANDED)
            .fillMaxHeight()
            .semantics { contentDescription = navigation }
            .onFocusChanged { expanded = it.hasFocus }
            .drawBehind {
                // The wider, denser rail is its own scrim over the rows.
                val width = lerp(COLLAPSED.toPx(), EXPANDED.toPx(), t)
                val s = lerp(0.70f, 0.97f, t)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to scrim.copy(alpha = s),
                        0.72f to scrim.copy(alpha = 0.9f * s),
                        1f to Color.Transparent,
                        endX = width,
                    ),
                    size = Size(width, size.height),
                )
            },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState(), enabled = RailItem.DISCOVER in items)
                .padding(start = 24.dp, end = 8.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HomeMarker { t }
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                RailEntry(item, requesters.getValue(item), { t }) { onOpen(item) }
            }
        }
    }
}

/** Always drawn as the current page: a `textPrimary` block, 48 dp square collapsed. Not focusable. */
@Composable
private fun HomeMarker(t: () -> Float) {
    val fill = Sohva.palette.textPrimary
    val ink = Sohva.palette.background
    val corner = Sohva.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .testTag("home-nav-home")
            .drawBehind {
                val width = lerp(ITEM_HEIGHT.toPx(), size.width, t())
                drawRoundRect(fill, size = Size(width, size.height), cornerRadius = CornerRadius(corner.toPx()))
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.FrontPage, size = 24.dp, tint = ink)
        Spacer(Modifier.width(14.dp))
        val style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold)
        DrawColorText(stringResource(R.string.home_nav_home), { ink.copy(alpha = t()) }, style = style)
    }
}

@Composable
private fun RailEntry(item: RailItem, requester: FocusRequester, t: () -> Float, onClick: () -> Unit) {
    val style = SurfaceStyle(
        corner = Sohva.shapes.medium,
        focusScale = 1f,
        padding = PaddingValues(horizontal = 12.dp),
    )
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(ITEM_HEIGHT).focusRequester(requester).testTag(item.tag),
        style = style,
    ) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, size = 24.dp)
            Spacer(Modifier.width(14.dp))
            // The label is always there for accessibility; it is drawn only as the rail widens.
            DrawColorText(stringResource(item.label), { colors.content.copy(alpha = colors.content.alpha * t()) }, style = Sohva.typography.label)
        }
    }
}
