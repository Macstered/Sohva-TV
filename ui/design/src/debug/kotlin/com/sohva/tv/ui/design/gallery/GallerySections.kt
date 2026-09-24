package com.sohva.tv.ui.design.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.components.Icon
import com.sohva.tv.ui.design.components.KeyHints
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.NavIcons
import com.sohva.tv.ui.design.components.SohvaSportBrand
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The gallery's pages. Each fits one 960 × 540 dp TV screen inside the safe area. */
enum class GallerySection { TOKENS, BUTTONS, ROWS, SETTINGS, STATUS, SHEET }

@Composable
fun GallerySectionContent(section: GallerySection) {
    when (section) {
        GallerySection.TOKENS -> TokensSection()
        GallerySection.BUTTONS -> ButtonsSection()
        GallerySection.ROWS -> RowsSection()
        GallerySection.SETTINGS -> SettingsSection()
        GallerySection.STATUS -> StatusSection()
        GallerySection.SHEET -> SheetSection()
    }
}

/** The states every focusable component is shown in (design/02 §6). */
internal val showcaseStates: List<Pair<String, SurfaceState>> = listOf(
    "Rest" to SurfaceState(),
    "Focused" to SurfaceState(showFocused = true),
    "Selected" to SurfaceState(selected = true),
    "Selected + focused" to SurfaceState(selected = true, showFocused = true),
    "Danger" to SurfaceState(danger = true),
    "Danger + focused" to SurfaceState(danger = true, showFocused = true),
    "Disabled" to SurfaceState(enabled = false),
)

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = Sohva.typography.overline, color = Sohva.palette.textDim)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TokensSection() {
    val p = Sohva.palette
    val swatches = listOf(
        "background" to p.background, "backgroundTop" to p.backgroundTop, "backgroundBottom" to p.backgroundBottom,
        "panel" to p.panel, "surfaceSubtle" to p.surfaceSubtle, "surface" to p.surface,
        "surfaceRaised" to p.surfaceRaised, "surfaceFocused" to p.surfaceFocused, "divider" to p.divider,
        "focus" to p.focus, "secondaryGlow" to p.secondaryGlow, "accent" to p.accent, "danger" to p.danger,
        "rating" to p.rating, "textPrimary" to p.textPrimary, "textMuted" to p.textMuted, "textDim" to p.textDim,
        "textDisabled" to p.textDisabled, "dangerSurface" to p.dangerSurface, "playerInfoSurface" to p.playerInfoSurface,
    )
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SectionTitle("PALETTE")
            swatches.forEach { (name, color) -> Swatch(name, color) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("TYPE")
            val t = Sohva.typography
            listOf("Display" to t.display, "Title" to t.title, "Headline" to t.headline, "Body large" to t.bodyLarge,
                "Body" to t.body, "Label" to t.label, "Caption" to t.caption, "OVERLINE" to t.overline)
                .forEach { (name, style) -> Text(name, style = style, color = p.textPrimary, maxLines = 1) }
            Spacer(Modifier.height(8.dp))
            SohvaTvBrand()
            SohvaSportBrand()
            Spacer(Modifier.height(8.dp))
            IconGrid()
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row {
        Box(Modifier.size(34.dp, 18.dp).roundFill(color, 4.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = Sohva.typography.caption, color = Sohva.palette.textMuted, maxLines = 1)
    }
}

@Composable
private fun IconGrid() {
    val icons = listOf(
        TvIcons.Home, TvIcons.Back, TvIcons.Aspect, TvIcons.Audio, TvIcons.Subtitles, TvIcons.Stats, TvIcons.ChevronRight,
        TvIcons.ChevronDown, TvIcons.Lock, TvIcons.Link, TvIcons.Key, TvIcons.Refresh, TvIcons.Check, TvIcons.Play, TvIcons.Pause,
        TvIcons.Save, TvIcons.Settings, TvIcons.Delete, TvIcons.Close, TvIcons.Channels, TvIcons.Target, TvIcons.Guide,
        TvIcons.Epg, TvIcons.Info, TvIcons.Search, TvIcons.Replay, TvIcons.Forward, TvIcons.Rewind, TvIcons.Star, TvIcons.StarOutline,
    )
    val nav = listOf(
        NavIcons.FrontPage, NavIcons.LiveTv, NavIcons.Sport, NavIcons.Movies, NavIcons.Series, NavIcons.Search, NavIcons.Discover,
        NavIcons.Settings, NavIcons.AddonHome, NavIcons.Library, NavIcons.Explore, NavIcons.Addons, NavIcons.BackToHome,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        icons.chunked(15).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { line.forEach { Icon(it, size = 24.dp, tint = Sohva.palette.textPrimary) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { nav.forEach { Icon(it, size = 24.dp, tint = Sohva.palette.textMuted) } }
    }
}

@Composable
private fun ButtonsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("ACTION BUTTONS — NORMAL, WITH ICON, COMPACT")
        showcaseStates.forEach { (name, state) ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(name, Modifier.width(140.dp).padding(top = 10.dp), style = Sohva.typography.caption, color = Sohva.palette.textDim)
                TvActionButton("Watch", {}, state = state)
                TvActionButton("Settings", {}, icon = TvIcons.Settings, state = state)
                TvActionButton("Refresh", {}, icon = TvIcons.Refresh, state = state, compact = true)
                TvActionButton("Close", {}, state = state, compact = true)
            }
        }
    }
}

@Composable
private fun RowsSection() {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(360.dp)) {
            SectionTitle("LIST ROWS")
            TvListRow("All channels", {}, icon = TvIcons.Channels, trailing = "56 164")
            TvListRow("Favourites", {}, icon = TvIcons.Star, state = SurfaceState(selected = true), layout = ListRowLayout(divider = true))
            TvListRow("News", {}, supporting = "412 channels", state = SurfaceState(showFocused = true), layout = ListRowLayout(divider = true))
            TvListRow("Sports", {}, state = SurfaceState(selected = true, showFocused = true), layout = ListRowLayout(divider = true))
            TvListRow("Films", {}, layout = ListRowLayout(dense = true, divider = true))
            TvListRow("Hidden group", {}, state = SurfaceState(enabled = false), layout = ListRowLayout(dense = true, divider = true))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("TAG CHIPS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvTagChip("4K UHD", tone = TagTone.PRIMARY)
                TvTagChip("×3", tone = TagTone.ACCENT)
                TvTagChip("50 FPS")
                TvTagChip("TMDB 7.8", tone = TagTone.RATING)
                TvTagChip("LIVE", tone = TagTone.LIVE)
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("KEY HINTS")
            KeyHints(listOf("◀ ▶" to "Later / earlier", "OK" to "Watch", "MENU" to "Options"))
        }
    }
}
