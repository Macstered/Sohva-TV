package com.sohva.tv.core.net.xtream

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.XtreamAccount
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Addresses of one Xtream account (spec 10 SRC-FR-68, SRC-FR-70). A server with a path keeps it
 * (`https://provider.example/panel` → `…/panel/player_api.php`); the builder percent-encodes path
 * segments and query values, so credentials with `/`, `?` or spaces stay intact.
 */
class XtreamUrls(private val account: XtreamAccount) {
    private val base: HttpUrl = account.baseUrl.trimEnd('/').toHttpUrlOrNull()
        ?: throw AppException(AppError.SourceUrlMalformed)

    fun api(action: String? = null, extra: Pair<String, String>? = null): HttpUrl = base.newBuilder()
        .addPathSegment("player_api.php")
        .credentials()
        .apply { if (action != null) addQueryParameter("action", action) }
        .apply { if (extra != null) addQueryParameter(extra.first, extra.second) }
        .build()

    fun guide(): HttpUrl = base.newBuilder().addPathSegment("xmltv.php").credentials().build()

    fun live(streamId: String, extension: String): String = stream("live", streamId, extension)

    fun film(streamId: String, extension: String): String = stream("movie", streamId, extension)

    fun episode(episodeId: String, extension: String): String = stream("series", episodeId, extension)

    private fun stream(kind: String, id: String, extension: String): String = base.newBuilder()
        .addPathSegment(kind)
        .addPathSegment(account.username)
        .addPathSegment(account.password)
        .addPathSegment("$id.$extension")
        .build()
        .toString()

    private fun HttpUrl.Builder.credentials(): HttpUrl.Builder =
        addQueryParameter("username", account.username).addQueryParameter("password", account.password)
}
