package com.streammate.tv.feature.guide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.streammate.tv.app.StreamMateGenreColors
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
internal val CHANNEL_COLUMN_WIDTH = 232.dp

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
    val zone = timeZoneId?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() } ?: ZoneId.systemDefault()
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
internal fun genreAccent(categories: List<String>, colors: StreamMateGenreColors): Color? = categories
    .asSequence()
    .map { it.lowercase() }
    .firstNotNullOfOrNull { category ->
        GENRE_ACCENTS.entries.firstOrNull { (keyword, _) -> category.contains(keyword) }?.value?.invoke(colors)
    }

/**
 * Keyword to accent, in match order.
 *
 * English and Finnish stems both appear because XMLTV feeds carry whichever
 * the provider writes, and a Finnish feed labelling a match "urheilu" should
 * light the same colour as an English one labelling it "sport".
 */
private val GENRE_ACCENTS: Map<String, (StreamMateGenreColors) -> Color> = linkedMapOf(
    "sport" to StreamMateGenreColors::sport,
    "urheilu" to StreamMateGenreColors::sport,
    "football" to StreamMateGenreColors::sport,
    "jalkapallo" to StreamMateGenreColors::sport,
    "hockey" to StreamMateGenreColors::sport,
    "news" to StreamMateGenreColors::news,
    "uutis" to StreamMateGenreColors::news,
    "current affairs" to StreamMateGenreColors::news,
    "ajankohtais" to StreamMateGenreColors::news,
    "weather" to StreamMateGenreColors::news,
    "children" to StreamMateGenreColors::children,
    "kids" to StreamMateGenreColors::children,
    "lapset" to StreamMateGenreColors::children,
    "lasten" to StreamMateGenreColors::children,
    "animation" to StreamMateGenreColors::children,
    "movie" to StreamMateGenreColors::film,
    "film" to StreamMateGenreColors::film,
    "elokuva" to StreamMateGenreColors::film,
    "cinema" to StreamMateGenreColors::film,
    "drama" to StreamMateGenreColors::film,
    "draama" to StreamMateGenreColors::film,
)


internal val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")
internal val WINDOW_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.M.")

/** Four hours: the visible three-hour grid and half an hour of padding on each side. */
internal fun programmeReadWindow(windowStart: Long): LongRange =
    (windowStart - 30 * MINUTE_MILLIS) until (windowStart + TIMELINE_WINDOW_MILLIS + 30 * MINUTE_MILLIS)

/** Rows above and below the visible ones whose programmes are read along with them. */
internal const val GUIDE_PROGRAMME_WINDOW_MARGIN = 30

/** The window moves in steps of this many rows, so a row-by-row scroll does not re-read per row. */
internal const val GUIDE_PROGRAMME_WINDOW_STEP = 10

/**
 * The channel ids whose programmes the grid wants: the visible rows plus a
 * margin either side, snapped to a step so scrolling one row at a time does
 * not re-read on every row.
 */
internal fun programmeWindowIds(
    ids: List<String>,
    firstVisible: Int,
    visibleCount: Int,
    margin: Int = GUIDE_PROGRAMME_WINDOW_MARGIN,
    step: Int = GUIDE_PROGRAMME_WINDOW_STEP,
): List<String> {
    if (ids.isEmpty() || visibleCount <= 0) return emptyList()
    val start = ((firstVisible - margin).coerceAtLeast(0) / step) * step
    val end = (((firstVisible + visibleCount + margin) / step) * step + step).coerceAtMost(ids.size)
    return if (start >= end) emptyList() else ids.subList(start, end)
}

/** Stable ordering and its IDs are built together on a worker, independent of programme arrivals. */
internal class GuideChannelRows(
    val channels: List<GuideTimelineChannel>,
) {
    private val indicesById = HashMap<String, Int>()
    private val indicesByNumber = HashMap<Int, Int>()
    val ids = channels.mapIndexed { index, channel ->
        indicesById[channel.id] = index
        channel.channelNumber?.let { number -> if (number !in indicesByNumber) indicesByNumber[number] = index }
        channel.id
    }

    fun indexOf(id: String?): Int? = id?.let(indicesById::get)

    /** Match provider numbers first, then the displayed position, as ChannelDial does. */
    fun indexForNumber(number: Int): Int? = indicesByNumber[number]
        ?: (number - 1).takeIf { it in channels.indices && channels[it].channelNumber == null }
}

/**
 * At most three prefetched scroll windows, refreshed in recency order.
 *
 * A window that moves ten rows reads again the sixty it shares with the last
 * one, and they arrive as new objects that are equal to the old. The schedule
 * already held is the one kept: a row and its cells hold lists, which Compose
 * can only compare by identity, so equal-but-new schedules recomposed, measured
 * and drew every row on screen each time a window arrived, 50 to 108 ms a
 * frame on the Shield, for rows on which nothing had changed.
 */
internal fun retainProgrammeWindow(
    previous: Map<String, List<GuideTimelineProgramme>>,
    incoming: List<GuideTimelineChannel>,
    limit: Int = 240,
): Map<String, List<GuideTimelineProgramme>> = LinkedHashMap(previous).apply {
    incoming.forEach { row ->
        val held = remove(row.id)
        put(row.id, if (held != null && held == row.programmes) held else row.programmes)
    }
    while (size > limit) remove(keys.first())
}

/**
 * Copy only the rows requested by the lazy grid, never every channel on each
 * programme emission. Given the list the grid holds now as [previous], a row
 * whose schedule is the same object as before is the same row object too, so
 * the grid skips it; see [retainProgrammeWindow].
 */
internal fun mergeProgrammes(
    rows: List<GuideTimelineChannel>,
    programmes: Map<String, List<GuideTimelineProgramme>>,
    previous: List<GuideTimelineChannel>? = null,
): List<GuideTimelineChannel> = ProgrammeOverlay(
    rows,
    programmes,
    // By index, so only from an overlay of these very rows. Its map is taken
    // rather than the overlay, or each would keep alive every one before it.
    held = (previous as? ProgrammeOverlay)?.takeIf { it.rows === rows }?.mergedRows.orEmpty(),
)

private class ProgrammeOverlay(
    val rows: List<GuideTimelineChannel>,
    private val programmes: Map<String, List<GuideTimelineProgramme>>,
    private val held: Map<Int, GuideTimelineChannel>,
) : AbstractList<GuideTimelineChannel>() {
    val mergedRows = HashMap<Int, GuideTimelineChannel>()
    override val size: Int get() = rows.size
    override fun get(index: Int): GuideTimelineChannel {
        mergedRows[index]?.let { return it }
        val row = rows[index]
        val schedule = programmes[row.id] ?: return row
        val merged = held[index]?.takeIf { it.programmes === schedule } ?: row.copy(programmes = schedule)
        return merged.also { mergedRows[index] = it }
    }
    // Compose keys must not compare all 50k rows merely because a window changed.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** The grid's rows with their programmes, remembering the last so [mergeProgrammes] can keep what did not change. */
internal class ProgrammeMerger {
    private var last: List<GuideTimelineChannel>? = null

    fun merge(
        rows: List<GuideTimelineChannel>,
        programmes: Map<String, List<GuideTimelineProgramme>>,
    ): List<GuideTimelineChannel> = mergeProgrammes(rows, programmes, last).also { last = it }
}
