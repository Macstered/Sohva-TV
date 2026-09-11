package com.streammate.tv.addons

import com.streammate.tv.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.sohva.tv.addons.AddonMedia
import com.streammate.tv.app.StreamMateThemeTokens

/** Addon counterpart of VOD's backdrop treatment; leaves the IPTV VOD screen unchanged. */
@Composable
internal fun AddonBackdrop(image: String?, modifier: Modifier = Modifier) {
    val ground = StreamMateThemeTokens.palette.background
    Box(modifier.background(ground)) {
        AsyncImage(image, null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(ground.copy(alpha = .98f), ground.copy(alpha = .75f), ground.copy(alpha = .2f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ground.copy(alpha = .35f), ground))))
    }
}

@Composable
internal fun AddonFacts(media: AddonMedia, modifier: Modifier = Modifier) {
    val labels = addonStrings()
    val facts = listOfNotNull(when (media.key.type) { "movie" -> labels(R.string.addon_ui_movie); "series" -> labels(R.string.home_series); else -> null },
        media.releaseInfo, media.runtime, media.genres.take(2).joinToString(" / ").takeIf { it.isNotEmpty() },
        media.imdbRating?.let { "IMDb $it" })
    if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), modifier,
        color = StreamMateThemeTokens.palette.textMuted, fontSize = StreamMateThemeTokens.typography.body.fontSize,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun AddonHero(media: AddonMedia?, modifier: Modifier = Modifier) {
    val labels = addonStrings()
    val type = StreamMateThemeTokens.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val logo = media?.logo?.takeIf { it.isNotBlank() }
        key(media?.key, logo) {
            var logoFailed by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().heightIn(max = 88.dp)) {
                // Keep the logo's space empty while it loads, rather than flashing
                // a plain-text title. Missing/broken artwork still gets a title.
                if (logo == null || logoFailed) Text(media?.name ?: labels(R.string.addon_title), Modifier.testTag("addon-hero-title-text"),
                    fontSize = type.display.fontSize, lineHeight = type.display.lineHeight,
                    fontWeight = FontWeight.Black, color = StreamMateThemeTokens.palette.textPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                logo?.let { AsyncImage(it, media?.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    onLoading = { logoFailed = false }, onSuccess = { logoFailed = false }, onError = { logoFailed = true },
                    modifier = Modifier.width(320.dp).height(88.dp).testTag("addon-hero-logo")) }
            }
        }
        if (media != null) {
            AddonFacts(media)
            media.description?.let { Text(it, Modifier.weight(1f, fill = false), fontSize = type.label.fontSize, lineHeight = type.label.lineHeight,
                color = StreamMateThemeTokens.palette.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}
