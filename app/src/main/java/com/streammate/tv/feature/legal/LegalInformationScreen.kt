package com.streammate.tv.feature.legal

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.R
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.feature.common.SohvaTvBrand
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons

@Composable
fun LegalInformationScreen(onBack: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val backFocus = remember { FocusRequester() }
    val versionName = remember(context) { context.applicationVersionName() }
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    LaunchedEffect(Unit) { backFocus.requestFocus() }

    StreamMateScreenBackground { modifier ->
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SohvaTvBrand()
                    Column(Modifier.padding(start = 24.dp)) {
                        Text(
                            text = stringResource(R.string.about_title),
                            color = palette.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = stringResource(R.string.about_subtitle),
                            color = palette.textMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    onClick = onBack,
                    focusRequester = backFocus,
                    testTag = "legal-back",
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 18.dp).testTag("legal-list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    LegalSection(
                        title = stringResource(R.string.app_name),
                        body = listOf(
                            stringResource(R.string.about_version, versionName),
                            stringResource(R.string.about_noncommercial),
                        ).joinToString("\n\n"),
                    )
                }
                item {
                    LegalSection(
                        title = stringResource(R.string.about_privacy_title),
                        body = listOf(
                            stringResource(R.string.about_privacy_intro),
                            stringResource(R.string.about_privacy_local),
                            stringResource(R.string.about_privacy_network),
                            stringResource(R.string.about_privacy_cleartext),
                            stringResource(R.string.about_privacy_retention),
                        ).joinToString("\n\n"),
                    )
                }
                item {
                    LegalSection(
                        title = stringResource(R.string.about_contact_title),
                        body = stringResource(R.string.about_contact_body),
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.about_contact_email),
                            onClick = { openUrl(SUPPORT_EMAIL_URL) },
                            compact = true,
                            testTag = "legal-contact-email",
                        )
                    }
                }
                item {
                    LegalSection(
                        title = stringResource(R.string.about_providers_title),
                        body = stringResource(R.string.about_tmdb_notice),
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/tmdb_attribution.svg",
                            contentDescription = "TMDB",
                            modifier = Modifier.width(205.dp).height(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        TvActionButton(
                            label = stringResource(R.string.about_open_tmdb),
                            onClick = { openUrl(TMDB_URL) },
                            compact = true,
                            testTag = "legal-open-tmdb",
                        )
                    }
                }
                item {
                    LegalSection(
                        title = "TVmaze · CC BY-SA",
                        body = stringResource(R.string.about_tvmaze_notice),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvActionButton(
                                label = stringResource(R.string.about_open_tvmaze),
                                onClick = { openUrl(TVMAZE_URL) },
                                compact = true,
                                testTag = "legal-open-tvmaze",
                            )
                            TvActionButton(
                                label = stringResource(R.string.about_open_tvmaze_license),
                                onClick = { openUrl(TVMAZE_LICENSE_URL) },
                                compact = true,
                                testTag = "legal-open-tvmaze-license",
                            )
                        }
                    }
                }
                item {
                    LegalSection(
                        title = "API-Sports",
                        body = listOf(
                            stringResource(R.string.about_api_sports_notice),
                            stringResource(R.string.about_provider_rights),
                        ).joinToString("\n\n"),
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.about_open_api_sports_terms),
                            onClick = { openUrl(API_SPORTS_TERMS_URL) },
                            compact = true,
                            testTag = "legal-open-api-sports",
                        )
                    }
                }
                item {
                    LegalSection(
                        title = stringResource(R.string.about_open_source_title),
                        body = stringResource(R.string.about_open_source_notice),
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.about_open_apache_license),
                            onClick = { openUrl(APACHE_LICENSE_URL) },
                            compact = true,
                            testTag = "legal-open-apache-license",
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.about_no_affiliation),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalSection(
    title: String,
    body: String,
    actions: @Composable () -> Unit = {},
) {
    val palette = StreamMateThemeTokens.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, StreamMateThemeTokens.shapes.medium)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            color = palette.focus,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = body,
            color = palette.textPrimary.copy(alpha = 0.88f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 7.dp).widthIn(max = 1_120.dp),
        )
        Column(Modifier.padding(top = 12.dp), content = { actions() })
    }
}

@Suppress("DEPRECATION")
private fun Context.applicationVersionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().ifBlank { "—" }

private const val TMDB_URL = "https://www.themoviedb.org"
private const val TVMAZE_URL = "https://www.tvmaze.com"
private const val TVMAZE_LICENSE_URL = "https://www.tvmaze.com/api#licensing"
private const val API_SPORTS_TERMS_URL = "https://api-sports.io/terms"
private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val SUPPORT_EMAIL_URL = "mailto:hello@luontra.fi"
