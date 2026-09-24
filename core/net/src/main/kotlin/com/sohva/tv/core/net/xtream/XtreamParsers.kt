package com.sohva.tv.core.net.xtream

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.net.xtream.LenientJson.bool
import com.sohva.tv.core.net.xtream.LenientJson.extension
import com.sohva.tv.core.net.xtream.LenientJson.id
import com.sohva.tv.core.net.xtream.LenientJson.int
import com.sohva.tv.core.net.xtream.LenientJson.string
import com.sohva.tv.core.net.xtream.LenientJson.text
import com.squareup.moshi.JsonReader

/**
 * One reader per Xtream record (spec 10 SRC-FR-68..71). Each consumes exactly one JSON value and
 * returns null for a record to skip (not an object, no id, blank name), so one bad element never
 * costs the rest of the list (SRC-FR-69).
 */
internal object XtreamParsers {
    private const val MAX_NAME = 512
    private const val MAX_PLOT = 4_096
    private const val MAX_URL = 2_048
    private const val MAX_CATCHUP_DAYS = 365

    private val categoryFields = JsonReader.Options.of("category_id", "category_name")
    private val liveFields = JsonReader.Options.of(
        "stream_id", "name", "category_id", "container_extension", "epg_channel_id", "stream_icon", "tv_archive",
        "tv_archive_duration", "num",
    )
    private val filmFields = JsonReader.Options.of(
        "stream_id", "name", "category_id", "container_extension", "stream_icon", "year", "release_date", "rating", "plot",
    )
    private val seriesFields = JsonReader.Options.of(
        "series_id", "name", "category_id", "cover", "backdrop_path", "year", "releaseDate", "rating", "plot",
    )
    private val episodeFields = JsonReader.Options.of("id", "episode_num", "container_extension", "title", "info")
    private val episodeInfoFields = JsonReader.Options.of("plot", "duration_secs", "movie_image", "cover_big", "cover")
    private val accountFields = JsonReader.Options.of("user_info", "server_info")
    private val userFields = JsonReader.Options.of("auth", "username", "status", "max_connections", "active_cons")
    private val serverFields = JsonReader.Options.of("timezone")
    private val seriesInfoFields = JsonReader.Options.of("episodes")

    fun category(r: JsonReader): XtreamCategory? {
        var id: String? = null
        var name: String? = null
        if (!r.readObject { when (r.selectName(categoryFields)) {
                0 -> id = id(r)
                1 -> name = text(r, MAX_NAME)
                else -> r.skipField()
            } }) return null
        return XtreamCategory(id ?: return null, name ?: return null)
    }

    fun live(r: JsonReader): XtreamLiveStream? {
        var id: String? = null
        var name: String? = null
        var category: String? = null
        var ext: String? = null
        var epg: String? = null
        var icon: String? = null
        var archive = false
        var archiveDays: Int? = null
        var num: Int? = null
        if (!r.readObject { when (r.selectName(liveFields)) {
                0 -> id = id(r)
                1 -> name = text(r, MAX_NAME)
                2 -> category = id(r)
                3 -> ext = string(r)
                4 -> epg = text(r, MAX_NAME)
                5 -> icon = text(r, MAX_URL)
                6 -> archive = bool(r)
                7 -> archiveDays = int(r)
                8 -> num = int(r)
                else -> r.skipField()
            } }) return null
        return XtreamLiveStream(
            streamId = id ?: return null,
            name = name ?: return null,
            categoryId = category,
            extension = extension(ext, "ts"),
            epgChannelId = epg,
            iconUrl = icon,
            catchupDays = if (archive) archiveDays?.coerceIn(1, MAX_CATCHUP_DAYS) else null,
            num = num,
        )
    }

    fun film(r: JsonReader): XtreamFilm? {
        var id: String? = null
        var name: String? = null
        var category: String? = null
        var ext: String? = null
        var poster: String? = null
        var year: String? = null
        var released: String? = null
        var rating: String? = null
        var plot: String? = null
        if (!r.readObject { when (r.selectName(filmFields)) {
                0 -> id = id(r)
                1 -> name = text(r, MAX_NAME)
                2 -> category = id(r)
                3 -> ext = string(r)
                4 -> poster = text(r, MAX_URL)
                5 -> year = string(r)
                6 -> released = string(r)
                7 -> rating = text(r, 16)
                8 -> plot = text(r, MAX_PLOT)
                else -> r.skipField()
            } }) return null
        return XtreamFilm(
            streamId = id ?: return null,
            name = name ?: return null,
            categoryId = category,
            extension = extension(ext, "mp4"),
            posterUrl = poster,
            year = LenientJson.yearOf(year, released),
            rating = rating,
            plot = plot,
        )
    }

    fun series(r: JsonReader): XtreamSeries? {
        var id: String? = null
        var name: String? = null
        var category: String? = null
        var cover: String? = null
        var backdrop: String? = null
        var year: String? = null
        var released: String? = null
        var rating: String? = null
        var plot: String? = null
        if (!r.readObject { when (r.selectName(seriesFields)) {
                0 -> id = id(r)
                1 -> name = text(r, MAX_NAME)
                2 -> category = id(r)
                3 -> cover = text(r, MAX_URL)
                4 -> backdrop = LenientJson.firstString(r, MAX_URL)
                5 -> year = string(r)
                6 -> released = string(r)
                7 -> rating = text(r, 16)
                8 -> plot = text(r, MAX_PLOT)
                else -> r.skipField()
            } }) return null
        return XtreamSeries(
            seriesId = id ?: return null,
            name = name ?: return null,
            categoryId = category,
            coverUrl = cover,
            backdropUrl = backdrop,
            year = LenientJson.yearOf(year, released),
            rating = rating,
            plot = plot,
        )
    }

    /** The account call: signed in, or [AppException] with the viewer's reason. */
    fun account(r: JsonReader): XtreamAccountInfo {
        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) throw AppException(AppError.XtreamResponseInvalid)
        var user: UserInfo? = null
        var zone: String? = null
        r.readObject {
            when (r.selectName(accountFields)) {
                0 -> user = userInfo(r)
                1 -> r.readObject { if (r.selectName(serverFields) == 0) zone = text(r, 64) else r.skipField() }
                else -> r.skipField()
            }
        }
        val info = user ?: throw AppException(AppError.XtreamNoUserInfo)
        if (!info.auth) throw AppException(AppError.XtreamAuthFailed)
        return XtreamAccountInfo(info.username, info.status, info.maxConnections, info.activeConnections, zone)
    }

    private class UserInfo(val auth: Boolean, val username: String?, val status: String?, val maxConnections: Int?, val activeConnections: Int?)

    private fun userInfo(r: JsonReader): UserInfo? {
        var auth = false
        var username: String? = null
        var status: String? = null
        var max: Int? = null
        var active: Int? = null
        if (!r.readObject { when (r.selectName(userFields)) {
                0 -> auth = bool(r)
                1 -> username = text(r, MAX_NAME)
                2 -> status = text(r, 64)
                3 -> max = int(r)
                4 -> active = int(r)
                else -> r.skipField()
            } }) return null
        return UserInfo(auth, username, status, max, active)
    }

    /** `get_series_info`: the `episodes` object of season → array; any other shape has none. */
    fun seriesInfo(r: JsonReader): List<XtreamEpisode> {
        if (r.peek() != JsonReader.Token.BEGIN_OBJECT) throw AppException(AppError.XtreamResponseInvalid)
        val out = ArrayList<XtreamEpisode>()
        r.readObject {
            if (r.selectName(seriesInfoFields) != 0) {
                r.skipField()
            } else if (r.peek() != JsonReader.Token.BEGIN_OBJECT) {
                r.skipValue()
            } else {
                r.readObject {
                    val season = r.nextName().trim().toIntOrNull()
                    if (season == null || r.peek() != JsonReader.Token.BEGIN_ARRAY) {
                        r.skipValue()
                    } else {
                        r.beginArray()
                        while (r.hasNext()) episode(r, season)?.let(out::add)
                        r.endArray()
                    }
                }
            }
        }
        return out
    }

    private fun episode(r: JsonReader, season: Int): XtreamEpisode? {
        var id: String? = null
        var number: Int? = null
        var ext: String? = null
        var title: String? = null
        var plot: String? = null
        var duration: Int? = null
        val images = arrayOfNulls<String>(3)
        if (!r.readObject { when (r.selectName(episodeFields)) {
                0 -> id = id(r)
                1 -> number = int(r)
                2 -> ext = string(r)
                3 -> title = text(r, MAX_NAME)
                4 -> r.readObject {
                    when (val field = r.selectName(episodeInfoFields)) {
                        0 -> plot = text(r, MAX_PLOT)
                        1 -> duration = int(r)
                        2, 3, 4 -> images[field - 2] = text(r, MAX_URL)
                        else -> r.skipField()
                    }
                }
                else -> r.skipField()
            } }) return null
        val episode = number ?: return null
        return XtreamEpisode(
            id = id ?: return null,
            season = season,
            episode = episode,
            extension = extension(ext, "mp4"),
            title = EpisodeTitles.clean(title, season, episode),
            plot = plot,
            durationSeconds = duration,
            imageUrl = images.firstOrNull { it != null },
        )
    }

    /** Reads an object with [field] per member; false (value skipped) when the value is not an object. */
    private inline fun JsonReader.readObject(field: () -> Unit): Boolean {
        if (peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            return false
        }
        beginObject()
        while (hasNext()) field()
        endObject()
        return true
    }

    private fun JsonReader.skipField() {
        skipName()
        skipValue()
    }
}

/** Episode titles of SRC-FR-71. */
internal object EpisodeTitles {
    private const val TRIM = " -–—:."

    /**
     * The provider's title; when it holds this episode's `S01E02` marker, only the text after it,
     * trimmed of spaces and `- – — : .`. Null when nothing is left.
     */
    fun clean(title: String?, season: Int, episode: Int): String? {
        val text = title?.trim() ?: return null
        val marker = Regex("(?i)S0*$season\\s*E0*$episode(?!\\d)").find(text) ?: return text.ifEmpty { null }
        return text.substring(marker.range.last + 1).trim { it in TRIM }.ifEmpty { null }
    }
}
