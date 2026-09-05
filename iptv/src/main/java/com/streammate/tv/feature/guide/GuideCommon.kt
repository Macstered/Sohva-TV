package com.streammate.tv.feature.guide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.streammate.tv.iptv.repository.GuideTimelineChannel
import com.streammate.tv.iptv.repository.GuideTimelineProgramme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The pieces the guide's screen, hero, rail and grid all need.
 *
 * The grid is laid out from the constraints it is given rather than from a
 * fixed timeline width: three hours always fill whatever is left after the
 * channel column, so the same code draws a 1080p panel and a smaller one.
 */

/** How much time the grid shows at once. */
internal const val TIMELINE_WINDOW_MINUTES = 180

/** Where the half-hour labels and rules fall. */
internal const val TICK_MINUTES = 30

internal const val MINUTE_MILLIS = 60_000L
internal const val DAY_MILLIS = 86_400_000L
internal const val TIMELINE_WINDOW_MILLIS = TIMELINE_WINDOW_MINUTES * MINUTE_MILLIS

internal const val MAX_SEARCH_LENGTH = 80
internal const val MAX_CATCHUP_DAYS = 365
internal const val METADATA_SELECTION_DELAY_MILLIS = 350L

/** How long a timeline read may take before the stale rows give way to the reading notice. */
internal const val TIMELINE_READING_NOTICE_MILLIS = 400L

/** The revealable rail down the left, wide enough for longer provider groups. */
internal val GUIDE_RAIL_WIDTH = 200.dp

/** Between the rail and the grid. */
internal val GUIDE_CONTENT_GAP = 14.dp

/** The fixed identity column: number, logo, name, stream tags. */
internal val CHANNEL_COLUMN_WIDTH = 202.dp

/** Between the channel column and the timeline. */
internal val GRID_GAP = 6.dp

/**
 * One channel row.
 *
 * Sized so that one fewer, more legible row fits beneath the hero on a 1080p
 * panel than in the previous crowded layout.
 * A guide is judged by how many channels it shows, and every dimension here
 * looks reasonable on its own while together they decide whether eight fit or
 * four do.
 */
internal val GUIDE_ROW_HEIGHT = 44.dp

/** Between rows, as in the reference. */
internal val GRID_ROW_GAP = 4.dp

/** The hero above the grid. */
internal val GUIDE_HERO_HEIGHT = 136.dp

/** The red now-line and the head that sits on top of it. */
internal val NOW_LINE_WIDTH = 2.dp
internal val NOW_LINE_HEAD = 9.dp

internal fun GuideTimelineProgramme.isLive(now: Long): Boolean =
    now in startEpochMillis until stopEpochMillis

/** How far through a programme is, or null when it is not on. */
internal fun GuideTimelineProgramme.progressAt(now: Long): Float? {
    if (!isLive(now)) return null
    val span = (stopEpochMillis - startEpochMillis).coerceAtLeast(1L)
    return ((now - startEpochMillis).toFloat() / span).coerceIn(0f, 1f)
}

/** The programme covering the start of the window, or the first one in it. */
internal fun GuideTimelineChannel.programmeAt(
    windowStart: Long,
    windowEnd: Long,
): GuideTimelineProgramme? = programmes.firstOrNull { programme ->
    windowStart in programme.startEpochMillis until programme.stopEpochMillis
} ?: programmes.firstOrNull { programme ->
    programme.startEpochMillis in windowStart until windowEnd
}

internal fun GuideTimelineChannel.preferredProgramme(now: Long): GuideTimelineProgramme? =
    programmes.firstOrNull { it.isLive(now) } ?: programmes.firstOrNull()

internal fun GuideTimelineChannel.canCatchup(programme: GuideTimelineProgramme, now: Long): Boolean {
    val mode = catchupType?.lowercase()?.takeIf(String::isNotBlank) ?: return false
    val days = catchupDays?.takeIf { it > 0 }?.coerceAtMost(MAX_CATCHUP_DAYS) ?: return false
    if (programme.startEpochMillis > now || programme.startEpochMillis < now - days * DAY_MILLIS) return false
    return when (mode) {
        "default", "append", "vod" -> !catchupSource.isNullOrBlank()
        "shift", "timeshift", "xtream", "xc" -> true
        else -> false
    }
}

internal fun <T> nextValue(values: List<T>, current: T?): T? {
    if (values.isEmpty()) return null
    if (current == null) return values.first()
    val currentIndex = values.indexOf(current)
    return values[(currentIndex + 1).mod(values.size)]
}

internal fun formatRange(start: Long, stop: Long, timeZoneId: String?): String =
    formatTime(start, timeZoneId) + "–" + formatTime(stop, timeZoneId)

internal fun formatTime(epochMillis: Long, timeZoneId: String?): String {
    val zone = runCatching { ZoneId.of(timeZoneId ?: DEFAULT_ZONE_ID) }
        .getOrDefault(ZoneId.of(DEFAULT_ZONE_ID))
    return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/**
 * A slim accent down the left edge of a block, read off the categories the EPG
 * actually carries.
 *
 * Deterministic and closed: a category the mapping does not recognise gets no
 * accent rather than an arbitrary colour, so the bar means something wherever
 * it appears instead of turning the grid into confetti.
 */
internal fun genreAccent(categories: List<String>): Color? = categories
    .asSequence()
    .map { it.lowercase() }
    .firstNotNullOfOrNull { category ->
        GENRE_ACCENTS.entries.firstOrNull { (keyword, _) -> category.contains(keyword) }?.value
    }

private val GENRE_FILM = Color(0xFF8E7BFF)
private val GENRE_SPORT = Color(0xFFFF8A4C)
private val GENRE_NEWS = Color(0xFF4CC2FF)
private val GENRE_KIDS = Color(0xFF57D9A3)

/**
 * Keyword to accent, in match order.
 *
 * English and Finnish stems both appear because XMLTV feeds carry whichever
 * the provider writes, and a Finnish feed labelling a match "urheilu" should
 * light the same colour as an English one labelling it "sport".
 */
private val GENRE_ACCENTS: Map<String, Color> = linkedMapOf(
    "sport" to GENRE_SPORT,
    "urheilu" to GENRE_SPORT,
    "football" to GENRE_SPORT,
    "jalkapallo" to GENRE_SPORT,
    "hockey" to GENRE_SPORT,
    "news" to GENRE_NEWS,
    "uutis" to GENRE_NEWS,
    "current affairs" to GENRE_NEWS,
    "ajankohtais" to GENRE_NEWS,
    "weather" to GENRE_NEWS,
    "children" to GENRE_KIDS,
    "kids" to GENRE_KIDS,
    "lapset" to GENRE_KIDS,
    "lasten" to GENRE_KIDS,
    "animation" to GENRE_KIDS,
    "movie" to GENRE_FILM,
    "film" to GENRE_FILM,
    "elokuva" to GENRE_FILM,
    "cinema" to GENRE_FILM,
    "drama" to GENRE_FILM,
    "draama" to GENRE_FILM,
)

private const val DEFAULT_ZONE_ID = "Europe/Helsinki"

internal val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")
internal val WINDOW_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.M.")
