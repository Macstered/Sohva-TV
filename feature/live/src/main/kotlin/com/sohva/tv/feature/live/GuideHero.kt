package com.sohva.tv.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LiveDot
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.components.TagTone
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvTagChip
import com.sohva.tv.ui.design.components.roundFill
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

internal val HERO_HEIGHT = 136.dp

/**
 * The hero (guide.md §1): a 16:9 still and the detail column for the selection. The area is kept
 * even while nothing is selected (guide.md §12 item 7). Metadata (backdrops, TMDB) arrives in M4;
 * until then the still is the channel's tile, as beta 23 shows when no metadata service is on.
 */
@Composable
internal fun GuideHero(model: GuideModel, actions: RowActions, watch: FocusRequester, modifier: Modifier = Modifier) = trace("Guide:Hero") {
    val selection by model.selection.collectAsStateWithLifecycle()
    val current = selection
    Row(modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        if (current == null) return@Row
        HeroStill(model, current)
        Spacer(Modifier.width(16.dp))
        HeroDetail(model, current, actions, watch, Modifier.weight(1f))
    }
}

@Composable
private fun HeroStill(model: GuideModel, selection: GuideSelection) {
    val palette = Sohva.palette
    val numbers by model.showNumbers.collectAsStateWithLifecycle()
    val programme = selection.programme
    val now = model.nowState.longValue
    val live = programme?.isLive(now) == true
    Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).roundFill(palette.surfaceSubtle, Sohva.shapes.large)) {
        LogoTile(selection.row.name, selection.row.channel.logoUrl, 56.dp, Modifier.align(Alignment.Center), fontSize = Sohva.typography.caption.fontSize)
        val ground = palette.background
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0.46f to ground.copy(alpha = 0f), 1f to ground.copy(alpha = 0.92f)))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    LiveDot(size = 8.dp)
                    Spacer(Modifier.width(7.dp))
                }
                val number = selection.row.numberValue.takeIf { numbers }
                val caption = listOfNotNull(selection.row.name, number?.let { stringResource(R.string.guide_channel_number, it) })
                    .joinToString("  ·  ").uppercase(ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT)
                Text(caption, style = Sohva.typography.overline.copy(fontWeight = FontWeight.Bold), color = palette.textPrimary, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                programme?.title ?: selection.row.name,
                style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = palette.textPrimary,
                maxLines = 1,
            )
        }
        if (live) {
            val track = palette.textPrimary.copy(alpha = 0.18f)
            val fill = palette.focus
            Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart)) {
                drawRect(track)
                drawRect(fill, Offset.Zero, Size(size.width * programme.progress(model.nowState.longValue), size.height))
            }
        }
    }
}

@Composable
private fun HeroDetail(model: GuideModel, selection: GuideSelection, actions: RowActions, watch: FocusRequester, modifier: Modifier) {
    val palette = Sohva.palette
    val type = Sohva.typography
    val labels by model.labels.collectAsStateWithLifecycle()
    val programme = selection.programme
    val now = model.nowState.longValue
    Column(modifier.fillMaxHeight()) {
        Text(programme?.title ?: selection.row.name, style = type.headline.copy(fontWeight = FontWeight.Black), color = palette.textPrimary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (programme?.isLive(now) == true) TvTagChip(stringResource(R.string.guide_live), tone = TagTone.LIVE)
            val facts = listOfNotNull(programme?.let { labels.guideRange(it.start, it.stop) }, programme?.firstCategory)
            if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = type.label, color = palette.textMuted, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Synopsis(model, programme, Modifier.weight(1f))
        HeroButtons(model, selection, actions, watch)
    }
}

/** Description read by id off the main thread (GUIDE-NFR-13), else the subtitle, else "no details". */
@Composable
private fun Synopsis(model: GuideModel, programme: GuideProgramme?, modifier: Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(programme?.id) {
        text = null
        if (programme != null && programme.hasDescription) text = model.description(programme.id)
    }
    val shown = text?.takeIf { it.isNotBlank() } ?: programme?.subtitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.guide_programme_no_details)
    // Two lines fit at font scale 1 (guide.md §12 item 3).
    Text(shown, modifier, style = Sohva.typography.label, color = Sohva.palette.textMuted, maxLines = 2)
}

@Composable
private fun HeroButtons(model: GuideModel, selection: GuideSelection, actions: RowActions, watch: FocusRequester) {
    val favourites by model.favourites.collectAsStateWithLifecycle()
    val search by model.overlays.search.collectAsStateWithLifecycle()
    val favourite = selection.row.key in favourites
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            // Down returns to the selected row, not the nearest block below (GUIDE-FR-66).
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                    model.focusRow(selection.row.index, GuideModel.KEEP)
                    true
                } else {
                    false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvActionButton(
            stringResource(R.string.action_watch), { actions.play(selection.row) },
            Modifier.focusRequester(watch).testTag("guide-hero-watch"), icon = TvIcons.Play, compact = true,
        )
        TvActionButton(
            stringResource(if (favourite) R.string.guide_favourite else R.string.guide_add_favourite),
            { model.toggleFavourite(selection.row.key) },
            Modifier.testTag("guide-hero-favourite"),
            icon = if (favourite) TvIcons.Star else TvIcons.StarOutline,
            state = SurfaceState(selected = favourite),
            compact = true,
        )
        TvActionButton(
            stringResource(if (search.visible) R.string.guide_close_search else R.string.guide_find_programme),
            { model.overlays.toggleSearch() },
            Modifier.testTag("guide-hero-search"),
            icon = TvIcons.Search,
            state = SurfaceState(selected = search.visible),
            compact = true,
        )
    }
}
