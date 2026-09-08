package com.streammate.tv.iptv.m3u

import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.security.SecretRedactor

enum class M3uContentKind {
    LIVE,
    MOVIE,
    SERIES,
}

class ParsedIptvChannel(
    val id: String,
    val tvgId: String?,
    val name: String,
    val normalizedName: String,
    val groupTitle: String?,
    val logoUrl: String?,
    val streamUrl: String,
    val userAgent: String?,
    val referrer: String?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val playlistOrder: Int,
    val contentKind: M3uContentKind,
) {
    override fun toString(): String =
        "ParsedIptvChannel(id=$id, name=$name, streamUrl=<redacted>)"
}

class M3uParser {
    fun parse(input: InputStream): List<ParsedIptvChannel> = records(input).toList()

    fun records(input: InputStream): Sequence<ParsedIptvChannel> = sequence {
        var metadata: PendingMetadata? = null
        var channelIndex = 0

        var opened = false
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.removePrefix("\uFEFF").trim()
                if (line.isBlank()) continue
                if (!opened) {
                    if (!opensPlaylist(line)) throw M3uFormatException(line)
                    opened = true
                }
                when {
                    line == "#EXTM3U" -> Unit
                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        metadata = parseMetadata(line)
                    }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        metadata = (metadata ?: PendingMetadata()).withVlcOption(line)
                    }
                    line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                        metadata = (metadata ?: PendingMetadata()).withKodiProperty(line)
                    }
                    line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                        metadata = (metadata ?: PendingMetadata()).withJsonHeaders(line)
                    }
                    line.startsWith('#') -> Unit
                    else -> {
                        channelIndex += 1
                        yield(createChannel(line, metadata, channelIndex))
                        metadata = null
                    }
                }
            }
        }
    }

    /**
     * Whether [line], the first with anything on it, can open a playlist: a
     * directive, or a stream address. A sign-in page, a JSON error and a
     * sentence from a panel all fail this, and every one of them used to
     * import as channels.
     */
    private fun opensPlaylist(line: String): Boolean =
        line.startsWith('#') ||
            (!line.startsWith('<') && !line.startsWith('{') && !line.startsWith('[') && line.contains("://"))

    private fun createChannel(
        rawUrl: String,
        metadata: PendingMetadata?,
        channelIndex: Int,
    ): ParsedIptvChannel {
        val (streamUrl, urlHeaders) = splitUrlHeaders(rawUrl)
        val name = metadata?.displayName
            ?.takeIf(String::isNotBlank)
            ?: metadata?.attributes?.get("tvg-name")?.takeIf(String::isNotBlank)
            ?: "Kanava $channelIndex"
        val tvgId = metadata?.attributes?.get("tvg-id")?.takeIf(String::isNotBlank)
        val normalizedName = ChannelNameNormalizer.normalize(name)
        val groupTitle = metadata?.attributes?.get("group-title")?.takeIf(String::isNotBlank)
        val identity = tvgId?.let { "$it|$normalizedName" } ?: "$normalizedName|$streamUrl"
        val catchupType = (metadata?.attributes?.get("catchup")
            ?: metadata?.attributes?.get("catchup-type")
            ?: metadata?.attributes?.get("timeshift")?.let { "timeshift" })
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)
        val catchupDays = (metadata?.attributes?.get("catchup-days")
            ?: metadata?.attributes?.get("timeshift"))
            ?.toIntOrNull()
            ?.coerceIn(1, MAX_CATCHUP_DAYS)
        return ParsedIptvChannel(
            id = sha256(identity),
            tvgId = tvgId,
            name = name,
            normalizedName = normalizedName,
            groupTitle = groupTitle,
            logoUrl = metadata?.attributes?.get("tvg-logo")?.takeIf(String::isNotBlank),
            streamUrl = streamUrl,
            userAgent = urlHeaders["user-agent"] ?: metadata?.userAgent,
            referrer = urlHeaders["referer"] ?: urlHeaders["referrer"] ?: metadata?.referrer,
            catchupType = catchupType,
            catchupSource = metadata?.attributes?.get("catchup-source")?.takeIf(String::isNotBlank),
            catchupDays = catchupDays,
            playlistOrder = channelIndex - 1,
            contentKind = detectContentKind(metadata, groupTitle, streamUrl),
        )
    }

    private fun detectContentKind(
        metadata: PendingMetadata?,
        groupTitle: String?,
        streamUrl: String,
    ): M3uContentKind {
        val attributes = metadata?.attributes.orEmpty()
        val declaredType = listOfNotNull(
            attributes["type"],
            attributes["media-type"],
            attributes["content-type"],
            attributes["tvg-type"],
        ).joinToString(" ").lowercase(Locale.ROOT)
        val normalizedGroup = ChannelNameNormalizer.normalize(groupTitle.orEmpty())
        val path = streamUrl.substringBefore('?').lowercase(Locale.ROOT)
        val series = SERIES_HINTS.any { it in declaredType || it in normalizedGroup || "/$it/" in path }
        if (series) return M3uContentKind.SERIES
        val declaredVod = MOVIE_HINTS.any { it in declaredType || it in normalizedGroup || "/$it/" in path }
        val fileVod = VOD_FILE_EXTENSIONS.any(path::endsWith)
        val finiteDuration = metadata?.durationSeconds?.let { it > 0 } == true
        return if (declaredVod || fileVod || finiteDuration) M3uContentKind.MOVIE else M3uContentKind.LIVE
    }

    private fun parseMetadata(line: String): PendingMetadata {
        val content = line.substringAfter(':')
        val separator = findUnquotedComma(content)
        val attributeSection = if (separator == -1) content else content.substring(0, separator)
        val displayName = if (separator == -1) null else content.substring(separator + 1).trim()
        val attributes = attributePattern.findAll(attributeSection).associate { match ->
            val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
            match.groupValues[1].lowercase(Locale.ROOT) to value
        }
        val durationSeconds = content.substringBefore(' ').substringBefore(',').trim().toIntOrNull()
        return PendingMetadata(
            attributes = attributes,
            displayName = displayName,
            durationSeconds = durationSeconds,
        )
    }

    private fun PendingMetadata.withVlcOption(line: String): PendingMetadata {
        val option = line.substringAfter(':').trim()
        val key = option.substringBefore('=').lowercase(Locale.ROOT)
        val value = option.substringAfter('=', missingDelimiterValue = "").trim()
        return when (key) {
            "http-user-agent" -> copy(userAgent = value)
            "http-referrer", "http-referer" -> copy(referrer = value)
            else -> this
        }
    }

    private fun PendingMetadata.withKodiProperty(line: String): PendingMetadata {
        val property = line.substringAfter(':')
        if (!property.substringBefore('=').endsWith("stream_headers", ignoreCase = true)) return this
        val headers = parseHeaderPairs(property.substringAfter('='))
        return copy(
            userAgent = headers["user-agent"] ?: userAgent,
            referrer = headers["referer"] ?: headers["referrer"] ?: referrer,
        )
    }

    private fun PendingMetadata.withJsonHeaders(line: String): PendingMetadata {
        val json = line.substringAfter(':')
        val headers = jsonHeaderPattern.findAll(json).associate { match ->
            match.groupValues[1].lowercase(Locale.ROOT) to match.groupValues[2]
        }
        return copy(
            userAgent = headers["user-agent"] ?: userAgent,
            referrer = headers["referer"] ?: headers["referrer"] ?: referrer,
        )
    }

    private fun splitUrlHeaders(value: String): Pair<String, Map<String, String>> {
        val separator = value.indexOf('|')
        if (separator == -1) return value to emptyMap()
        return value.substring(0, separator) to parseHeaderPairs(value.substring(separator + 1))
    }

    private fun parseHeaderPairs(value: String): Map<String, String> = value
        .split('&')
        .mapNotNull { pair ->
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = pair.substring(0, separator).trim().lowercase(Locale.ROOT)
            val decoded = runCatching {
                URLDecoder.decode(pair.substring(separator + 1), Charsets.UTF_8.name())
            }.getOrElse { pair.substring(separator + 1) }
            key to decoded
        }
        .toMap()

    private fun findUnquotedComma(value: String): Int {
        var quote: Char? = null
        value.forEachIndexed { index, character ->
            when {
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == character -> quote = null
                quote == null && character == ',' -> return index
            }
        }
        return -1
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class PendingMetadata(
        val attributes: Map<String, String> = emptyMap(),
        val displayName: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
        val durationSeconds: Int? = null,
    )

    private companion object {
        const val MAX_CATCHUP_DAYS = 365
        val MOVIE_HINTS = listOf("vod", "movie", "movies", "film", "films", "elokuva", "elokuvat")
        val SERIES_HINTS = listOf("series", "show", "shows", "sarja", "sarjat")
        val VOD_FILE_EXTENSIONS = listOf(".avi", ".m4v", ".mkv", ".mov", ".mp4", ".webm")
        val attributePattern = Regex("""([A-Za-z0-9_-]+)=(?:\"([^\"]*)\"|'([^']*)'|([^\s,]+))""")
        val jsonHeaderPattern = Regex(
            """\"(User-Agent|Referer|Referrer)\"\s*:\s*\"([^\"]*)\"""",
            RegexOption.IGNORE_CASE,
        )
    }
}

object ChannelNameNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKD)
        .replace(markPattern, "")
        .lowercase(Locale.ROOT)
        .replace(punctuationPattern, " ")
        .trim()
        .replace(whitespacePattern, " ")

    private val markPattern = Regex("\\p{M}+")
    private val punctuationPattern = Regex("[^a-z0-9]+")
    private val whitespacePattern = Regex("\\s+")
}

/**
 * The document is not an M3U at all. Every line that was not a comment used
 * to become a channel, so a login page arrived as hundreds of channels named
 * "Kanava 1, 2, 3" and the refresh reported success for it.
 */
class M3uFormatException(firstLine: String) : LocalizedException(
    CoreR.string.error_playlist_not_m3u,
    logMessage = "Not an M3U document: it opens with \"${SecretRedactor.redact(firstLine.take(60)).orEmpty()}\"",
)
