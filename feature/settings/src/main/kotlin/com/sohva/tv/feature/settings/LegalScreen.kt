package com.sohva.tv.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The legal screen's model: the links and texts it needs, for the life of its back-stack entry. */
class LegalModel(services: AboutSettingsServices) : ViewModel() {
    internal val about: AboutSettings = AboutSettings(services, viewModelScope)
    internal val palettes: suspend () -> String? = services::paletteLicences
}

/**
 * About, privacy and licences (spec 72 ABOUT-FR-22, §5.2): brand, title and Back, then one card
 * per section. Every card's text is a focusable reading block (focus ring only), so the D-pad
 * reaches every card, not only those with buttons (§5.2 rebuild).
 */
@Composable
fun LegalScreen(model: LegalModel, onBack: () -> Unit) {
    val back = remember { FocusRequester() }
    val links = rememberLinkOpener(model.about)
    Column(Modifier.fillMaxSize().background(Sohva.palette.background).padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SohvaTvBrand()
            Column(Modifier.weight(1f).padding(start = 24.dp)) {
                Text(stringResource(R.string.about_title), style = Sohva.typography.title.copy(fontWeight = FontWeight.Black), color = Sohva.palette.textPrimary)
                Text(stringResource(R.string.about_subtitle), style = Sohva.typography.label, color = Sohva.palette.textMuted)
            }
            TvActionButton(stringResource(R.string.action_back), onBack, Modifier.focusRequester(back).testTag("legal-back"), TvIcons.Back)
        }
        LazyColumn(
            Modifier.fillMaxWidth().padding(top = 18.dp).testTag("legal-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item { AppCard(model.about.installedVersion) }
            item { Card(stringResource(R.string.about_privacy_title), privacyText()) }
            item {
                Card(stringResource(R.string.about_contact_title), stringResource(R.string.about_contact_body)) { first ->
                    LinkButton(stringResource(R.string.about_contact_email), "mailto:hello@luontra.fi", "legal-contact-email", links, first)
                }
            }
            item { TmdbCard(links) }
            item {
                Card("TVmaze · CC BY-SA", stringResource(R.string.about_tvmaze_notice)) { first ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinkButton(stringResource(R.string.about_open_tvmaze), "https://www.tvmaze.com", "legal-open-tvmaze", links, first)
                        LinkButton(stringResource(R.string.about_open_tvmaze_license), "https://www.tvmaze.com/api#licensing", "legal-open-tvmaze-license", links)
                    }
                }
            }
            item {
                Card("API-Sports", stringResource(R.string.about_api_sports_notice) + "\n\n" + stringResource(R.string.about_provider_rights)) { first ->
                    LinkButton(stringResource(R.string.about_open_api_sports_terms), "https://api-sports.io/terms", "legal-open-api-sports", links, first)
                }
            }
            item {
                val notes = listOf(R.string.settings_http_disclosure, R.string.metadata_disclosure, R.string.sports_disclosure).map { stringResource(it) }
                Card(stringResource(R.string.about_service_notes_title), notes.joinToString("\n\n"))
            }
            item { Card(stringResource(R.string.about_addons_title), stringResource(R.string.about_addons_body)) }
            item {
                Card(stringResource(R.string.about_open_source_title), stringResource(R.string.about_open_source_notice)) { first ->
                    LinkButton(stringResource(R.string.about_open_apache_license), "https://www.apache.org/licenses/LICENSE-2.0", "legal-open-apache-license", links, first)
                }
            }
            item { PalettesCard(model) }
            item {
                Text(
                    stringResource(R.string.about_no_affiliation),
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    style = Sohva.typography.caption,
                    color = Sohva.palette.textMuted,
                )
            }
        }
    }
    LinkDialog(model.about, links)
    LaunchedEffect(Unit) { back.requestFocusWhenAttached() }
}

@Composable
private fun privacyText(): String = listOf(
    R.string.about_privacy_intro, R.string.about_privacy_local, R.string.about_privacy_network, R.string.about_privacy_cleartext, R.string.about_privacy_retention,
).map { stringResource(it) }.joinToString("\n\n")

@Composable
private fun AppCard(version: String) {
    val shown = version.ifBlank { "—" }
    Card(stringResource(R.string.app_name), stringResource(R.string.about_version, shown) + "\n\n" + stringResource(R.string.about_noncommercial))
}

@Composable
private fun TmdbCard(links: LinkOpener) {
    Card(stringResource(R.string.about_providers_title), stringResource(R.string.about_tmdb_notice)) { first ->
        // TMDB's own mark, unmodified (assets/README.md), then its button.
        Image(painterResource(R.drawable.tmdb_attribution), "TMDB", Modifier.padding(bottom = 10.dp).size(205.dp, 28.dp))
        LinkButton(stringResource(R.string.about_open_tmdb), "https://www.themoviedb.org", "legal-open-tmdb", links, first)
    }
}

/** The MIT notices of the adapted palettes, shipped in the APK (ABOUT-24; spec 72 ABOUT-FR-24's proposal). */
@Composable
private fun PalettesCard(model: LegalModel) {
    val text by produceState<String?>(null) { value = model.palettes() }
    Card(stringResource(R.string.about_palettes_title), text ?: return)
}

/**
 * One section card (§5.2): surface fill, medium shape, the text as a reading block, then its buttons.
 * Down from the full-width text goes to the card's first button: left alone, the focus search
 * prefers the next card's text, centred like it, over a small button on the left.
 */
@Composable
private fun Card(title: String, body: String, actions: (@Composable ColumnScope.(FocusRequester) -> Unit)? = null) {
    val first = remember { FocusRequester() }
    Column(
        Modifier.fillMaxWidth().background(Sohva.palette.surface, RoundedCornerShape(Sohva.shapes.medium)).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val block = if (actions == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().focusProperties { down = first }
        TvSurface({}, block, style = READING) {
            Column(Modifier.padding(4.dp)) {
                Text(title, style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Black), color = Sohva.palette.focus)
                Text(
                    body,
                    Modifier.padding(top = 7.dp).widthIn(max = 1_120.dp),
                    style = Sohva.typography.label.copy(fontWeight = FontWeight.Normal),
                    color = Sohva.palette.textPrimary.copy(alpha = 0.88f),
                )
            }
        }
        if (actions != null) Column(Modifier.padding(top = 12.dp)) { actions(first) }
    }
}

private val READING = SurfaceStyle(focusRing = true, focusScale = 1f, animateFill = false)
