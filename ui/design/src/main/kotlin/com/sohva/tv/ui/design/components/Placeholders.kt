package com.sohva.tv.ui.design.components

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * Up to two initials on a surface tile where artwork is missing (design/04 §3). The initials sit
 * under any logo or crest drawn on top, so a missing image costs nothing extra.
 */
@Composable
fun InitialsTile(name: String, modifier: Modifier = Modifier, corner: Dp = 8.dp, fontSize: TextUnit = 18.sp) {
    val initials = remember(name) { Initials.of(name) }
    Box(modifier.roundFill(Sohva.palette.surfaceRaised, corner), contentAlignment = Alignment.Center) {
        Text(
            initials,
            style = Sohva.typography.label.copy(fontSize = fontSize, fontWeight = FontWeight.Black),
            color = Sohva.palette.focus,
            maxLines = 1,
        )
    }
}

/** A section's message: loading, empty or failed, in plain language (plan/03 §4.11). */
@Composable
fun SectionMessage(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = Sohva.typography.bodyLarge.copy(textAlign = TextAlign.Center), color = Sohva.palette.textMuted)
        if (detail != null) {
            Text(detail, Modifier.padding(top = 6.dp), style = Sohva.typography.label, color = Sohva.palette.textDim)
        }
    }
}

/** The one sentence the viewer reads for an [AppError]. Features add their codes with their strings. */
@Composable
fun errorMessage(error: AppError): String = errorText(LocalResources.current, error)

/** [errorMessage] outside composition, for texts written to a file (spec 72 §7.6). */
fun errorText(res: Resources, error: AppError): String = when (error) {
    is AppError.TransportFailed ->
        if (error.detail.isNullOrBlank()) {
            res.getString(R.string.error_transport_failed)
        } else {
            res.getString(R.string.error_transport_failed_detail, error.detail.orEmpty())
        }
    is AppError.HttpStatus -> res.getString(R.string.error_source_http, error.status)
    AppError.Unknown -> res.getString(R.string.error_unknown)
    AppError.SourceResponseTooLarge -> res.getString(R.string.error_source_response_too_large)
    AppError.SecretsUnreadable -> res.getString(R.string.error_secrets_unreadable)
    AppError.PinFormat -> res.getString(R.string.error_pin_format)
    AppError.Interrupted -> res.getString(R.string.error_import_interrupted)
    AppError.SourceNameRequired -> res.getString(R.string.settings_source_name_required)
    is AppError.SourceNameTooLong -> res.getQuantityString(R.plurals.error_source_name_too_long, error.max, error.max)
    is AppError.SourceLimitReached -> res.getString(R.string.error_source_limit_reached, error.max)
    is AppError.SourceUrlInvalid -> res.getString(R.string.error_source_url_invalid, fieldLabel(res, error.label))
    AppError.SourceUrlMalformed -> res.getString(R.string.error_source_url_malformed)
    AppError.XtreamUsernameMissing -> res.getString(R.string.error_xtream_username_missing)
    AppError.XtreamPasswordMissing -> res.getString(R.string.error_xtream_password_missing)
    AppError.SourceNoLiveTv -> res.getString(R.string.error_source_no_live_tv)
    AppError.SourceNoVod -> res.getString(R.string.error_source_no_vod)
    AppError.PlaylistEmpty -> res.getString(R.string.error_playlist_empty)
    AppError.PlaylistNotM3u -> res.getString(R.string.error_playlist_not_m3u)
    AppError.EpgEmpty -> res.getString(R.string.error_epg_empty)
    AppError.EpgUnmatched -> res.getString(R.string.error_epg_unmatched)
    AppError.CatalogueEmpty -> res.getString(R.string.error_catalogue_empty)
    AppError.XtreamAuthFailed -> res.getString(R.string.error_xtream_auth_failed)
    AppError.XtreamNoUserInfo -> res.getString(R.string.error_xtream_no_user_info)
    is AppError.XtreamHttp -> res.getString(R.string.error_xtream_http, error.status)
    AppError.XtreamResponseTooLarge -> res.getString(R.string.error_xtream_response_too_large)
    AppError.XtreamResponseInvalid -> res.getString(R.string.error_xtream_response_invalid)
    AppError.SeriesIdInvalid -> res.getString(R.string.error_series_id_invalid)
    AppError.SourceDisabled -> res.getString(R.string.catalogue_source_disabled)
    is AppError.MetadataHttp -> res.getString(R.string.error_metadata_http, error.provider, error.status)
    is AppError.MetadataTooLarge -> res.getString(R.string.error_metadata_response_too_large, error.provider)
    AppError.TmdbKeyInvalid -> res.getString(R.string.error_tmdb_key_invalid)
    AppError.TmdbKeyRequired -> res.getString(R.string.error_tmdb_key_required)
    AppError.MetadataSaveFailed -> res.getString(R.string.error_metadata_settings_save)
    is AppError.ConnectionLimit -> res.getString(R.string.error_source_connection_limit, error.sourceName, error.limit)
}

/** "M3U" and "XMLTV" are format names and stay untranslated, as in beta 23. */
private fun fieldLabel(res: Resources, label: AppError.FieldLabel): String = when (label) {
    AppError.FieldLabel.M3U -> "M3U"
    AppError.FieldLabel.XMLTV -> "XMLTV"
    AppError.FieldLabel.XTREAM_SERVER -> res.getString(R.string.error_label_xtream_server)
    AppError.FieldLabel.SOURCE -> res.getString(R.string.error_source_label)
}

/** Fills its parent with a centred [SectionMessage]; for screens that have nothing else yet. */
@Composable
fun FullScreenMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SectionMessage(text) }
}
