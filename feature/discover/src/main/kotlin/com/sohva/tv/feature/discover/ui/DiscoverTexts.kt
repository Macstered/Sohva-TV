package com.sohva.tv.feature.discover.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.ui.design.R

/** The one sentence a viewer reads for a failure (spec 50 §4.20); never the provider's own text. */
@Composable
fun failureText(failure: AddonFailure): String = stringResource(
    when (failure) {
        AddonFailure.INVALID_URL, AddonFailure.INSECURE_URL -> R.string.addon_error_url
        AddonFailure.CONFIGURATION_REQUIRED -> R.string.addon_error_configuration
        AddonFailure.INVALID_MANIFEST, AddonFailure.INVALID_RESPONSE, AddonFailure.RESPONSE_TOO_LARGE -> R.string.addon_error_manifest
        AddonFailure.NETWORK, AddonFailure.TIMEOUT, AddonFailure.HTTP_ERROR -> R.string.addon_error_network
        AddonFailure.REDIRECT -> R.string.addon_error_redirect
        AddonFailure.ACCESS_DENIED -> R.string.addon_access_denied
        else -> R.string.addon_error_operation
    },
)

/** "Movie", "Series", or a provider's own type as supplied (FR-56, -127). */
@Composable
fun typeLabel(type: String): String = when (type) {
    "movie" -> stringResource(R.string.addon_ui_movie)
    "series" -> stringResource(R.string.home_series)
    else -> type
}

/** §5.1 facts: "Movie  ·  2024  ·  1h 58m  ·  Drama / Crime  ·  IMDb 7.4", only the parts supplied. */
@Composable
fun factsLine(preview: MetaPreview): String = listOfNotNull(
    typeLabel(preview.type),
    preview.releaseInfo,
    preview.runtime,
    preview.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" / "),
    preview.imdbRating?.let { "IMDb $it" },
).joinToString("  ·  ")
