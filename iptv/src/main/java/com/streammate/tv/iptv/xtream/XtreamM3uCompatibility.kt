package com.streammate.tv.iptv.xtream

import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import java.net.URI
import java.net.URLDecoder

fun IptvSourceConfiguration.derivedXtreamSourceOrNull(): IptvSourceConfiguration? {
    if (type != IptvSourceType.M3U) return null
    val playlistUrl = m3uUrl?.takeIf(String::isNotBlank) ?: return null
    val uri = runCatching { URI(playlistUrl) }.getOrNull() ?: return null
    if (!uri.path.orEmpty().substringAfterLast('/').equals("get.php", ignoreCase = true)) return null
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        decode(pair.substring(0, separator)).lowercase() to decode(pair.substring(separator + 1))
    }.toMap()
    val username = query["username"]?.takeIf(String::isNotBlank) ?: return null
    val password = query["password"]?.takeIf(String::isNotBlank) ?: return null
    val host = uri.host ?: return null
    val parentPath = uri.path.orEmpty().substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
    val baseUrl = runCatching {
        URI(uri.scheme, null, host, uri.port, parentPath, null, null).toASCIIString()
    }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
    return copy(
        type = IptvSourceType.XTREAM,
        m3uUrl = null,
        xmlTvUrl = null,
        xtreamBaseUrl = baseUrl,
        xtreamUsername = username,
        xtreamPassword = password,
    )
}

private fun decode(value: String): String = runCatching {
    URLDecoder.decode(value, Charsets.UTF_8.name())
}.getOrElse { value }
