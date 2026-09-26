package com.sohva.tv.core.model

import com.sohva.tv.core.model.guide.ChannelDial
import com.sohva.tv.core.model.guide.DialBuffer
import com.sohva.tv.core.model.guide.Genre
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.core.model.guide.GuideRules
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.guide.Schedules
import com.sohva.tv.core.model.time.TimeLabels
import com.sohva.tv.core.model.time.TimeStyle
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 20 §11 unit tests (old `GuideTimeWindowTest`, `GuideWindowedReadTest`, `ChannelDialTest` and friends). */
class GuideModelTest {
    private val min = GuideWindow.MINUTE_MS

    // 2026-09-24 19:47 UTC, anchored in tests to a fixed epoch so no fixture expires.
    private val now = 1_790_279_220_000L

    @Test
    fun anchorIsHalfAnHourBeforeTheCurrentHalfHour() {
        val anchor = GuideWindow.anchor(now)
        assertEquals(0L, anchor % GuideWindow.HALF_HOUR_MS)
        assertTrue(anchor <= now - GuideWindow.HALF_HOUR_MS && now - anchor < 2 * GuideWindow.HALF_HOUR_MS)
        assertEquals(anchor, GuideWindow.AT_NOW.startAt(now))
    }

    @Test
    fun pagingRoundTripsAndPinnedWindowsDoNotDrift() {
        val forward = GuideWindow.AT_NOW.moved(GuideWindow.PAGE_MS, now)!!
        assertFalse(forward.isAtNow(now))
        val start = forward.startAt(now)
        // The clock crosses a half hour: the paged window stays; the window at now follows.
        val later = now + 45 * min
        assertEquals(start, forward.startAt(later))
        assertEquals(GuideWindow.anchor(later), GuideWindow.AT_NOW.startAt(later))
        val back = forward.moved(-GuideWindow.PAGE_MS, now)!!
        assertTrue(back.isAtNow(now))
        assertEquals(GuideWindow.AT_NOW, back)
    }

    @Test
    fun limitsAreOneDayBackAndSevenDaysAhead() {
        var w = GuideWindow.AT_NOW
        repeat(20) { w = w.moved(-GuideWindow.DAY_MS, now) ?: w }
        assertEquals(GuideWindow.anchor(now) - GuideWindow.DAY_MS, w.startAt(now))
        assertNull(w.moved(-GuideWindow.PAGE_MS, now))
        w = GuideWindow.AT_NOW
        repeat(20) { w = w.moved(GuideWindow.DAY_MS, now) ?: w }
        assertEquals(GuideWindow.anchor(now) + GuideWindow.WEEK_MS, w.startAt(now))
        assertNull(w.moved(GuideWindow.PAGE_MS, now))
        // A jump that would pass the limit clamps to it.
        assertEquals(GuideWindow.anchor(now) - GuideWindow.DAY_MS, GuideWindow.AT_NOW.moved(-5 * GuideWindow.DAY_MS, now)!!.startAt(now))
    }

    @Test
    fun programmeRowsAreVisibleThirtyEitherSideSnappedToTen() {
        assertEquals(0 until 40, GuideRules.programmeRows(0, 7, 1000))
        assertEquals(0 until 50, GuideRules.programmeRows(3, 7, 1000))
        assertEquals(10 until 90, GuideRules.programmeRows(45, 7, 1000))
        assertEquals(GuideRules.programmeRows(40, 7, 1000), GuideRules.programmeRows(41, 7, 1000))
        assertEquals(960 until 1000, GuideRules.programmeRows(995, 7, 1000))
        assertEquals(0 until 5, GuideRules.programmeRows(0, 5, 5))
        assertTrue(GuideRules.programmeRows(0, 0, 100).isEmpty())
        assertTrue(GuideRules.programmeRows(3, 7, 1000).count() <= 80)
    }

    @Test
    fun scheduleKeepsTheRichestOfEqualStartsAndDropsEmptyOnes() {
        val a = programme(1, 0, 30, subtitle = null)
        val richer = programme(2, 0, 30, subtitle = "Part 2")
        val longer = programme(3, 30, 90)
        val shorter = programme(4, 30, 60)
        val empty = programme(5, 100, 100)
        val cleaned = Schedules.clean(listOf(longer, a, empty, richer, shorter))
        assertEquals(listOf(2L, 3L), cleaned.map { it.id })
        // Ties go to the smaller id.
        assertEquals(listOf(6L), Schedules.clean(listOf(programme(7, 0, 30), programme(6, 0, 30))).map { it.id })
    }

    @Test
    fun genreAccentFollowsTheFirstMatchingCategoryAndTheTableOrder() {
        assertEquals(Genre.SPORT, Genre.of(listOf("Urheilu")))
        assertEquals(Genre.NEWS, Genre.of(listOf("Talk", "Weather")))
        // "Sports news" contains both; the table's order puts sport first.
        assertEquals(Genre.SPORT, Genre.of(listOf("Sports news")))
        assertEquals(Genre.FILM, Genre.of(listOf("Draama", "Kids")))
        assertNull(Genre.of(listOf("Music")))
        assertEquals(listOf("A", "B"), Schedules.categories("A\u001F \u001FB"))
    }

    @Test
    fun dialOwnNumberWinsAndPositionOnlyWithoutOwnNumber() = runTest {
        data class Row(val position: Int, val number: Int?)
        val rows = listOf(Row(0, 12), Row(1, null), Row(2, 3))
        suspend fun resolve(n: Int) = ChannelDial.resolve(
            n,
            byOwnNumber = { x -> rows.firstOrNull { it.number == x } },
            atPosition = { p -> rows.getOrNull(p) },
            ownNumber = { it.number },
        )
        assertEquals(rows[0], resolve(12))
        assertEquals(rows[1], resolve(2))
        assertEquals(rows[2], resolve(3))
        // Position 1 has its own number 12, so "1" finds nothing.
        assertNull(resolve(1))
        assertNull(resolve(99))
        val buffer = DialBuffer()
        listOf(0, 0, 7, 1, 5).forEach { buffer.append(it) }
        assertEquals("0071", buffer.digits)
        assertEquals(71, buffer.number())
    }

    @Test
    fun sourceChoiceFallsBackInOrder() {
        val sources = listOf("a", "b", "c")
        assertEquals("b", GuideRules.chooseSource(sources, "b", "c", "a"))
        assertEquals("c", GuideRules.chooseSource(sources, "gone", "c", "a"))
        assertEquals("a", GuideRules.chooseSource(sources, null, "gone", "a"))
        assertEquals("a", GuideRules.chooseSource(sources, null, null, null))
        assertNull(GuideRules.chooseSource(emptyList(), "a", null, null))
    }

    @Test
    fun firstGroupUsesManualPositionsThenRailOrder() {
        val groups = listOf("News" to null, "Sport" to 4, "Kids" to 2, "Film" to 2)
        assertEquals("Kids", GuideRules.firstGroup(groups) { it.second }?.first)
        assertEquals("News", GuideRules.firstGroup(groups) { null }?.first)
        assertNull(GuideRules.firstGroup(emptyList<Pair<String, Int?>>()) { it.second })
    }

    @Test
    fun verticalMoveTakesTheNearestOverlappingBlock() {
        val from = GuideRules.Span(40f, 80f)
        val blocks = listOf(GuideRules.Span(0f, 50f), GuideRules.Span(50f, 120f), GuideRules.Span(120f, 180f))
        assertEquals(1, GuideRules.verticalTarget(from, blocks))
        assertEquals(0, GuideRules.verticalTarget(GuideRules.Span(10f, 20f), blocks))
        // Nothing overlaps: nearest centre.
        assertEquals(1, GuideRules.verticalTarget(GuideRules.Span(200f, 210f), listOf(GuideRules.Span(0f, 10f), GuideRules.Span(150f, 160f))))
        assertEquals(-1, GuideRules.verticalTarget(from, emptyList()))
    }

    @Test
    fun selectionAfterAPageKeepsTheProgrammeOrFallsBack() {
        val start = GuideWindow.anchor(now)
        val end = start + GuideWindow.LENGTH_MS
        val live = programme(1, 0, 60, base = now - 20 * min)
        val later = programme(2, 60, 120, base = now - 20 * min)
        val schedule = listOf(live, later)
        assertEquals(later, GuideRules.keepSelection(schedule, 2, null, start, end, now))
        assertEquals(live, GuideRules.keepSelection(schedule, 99, null, start, end, now))
        // After a refresh the id is gone: the programme covering the old start wins.
        assertEquals(later, GuideRules.keepSelection(schedule, 99, later.start + 1, start, end, now))
        assertEquals(live, GuideRules.channelSelection(schedule, now))
        assertTrue(live.isLive(now))
        assertEquals(20f / 60f, live.progress(now), 0.001f)
    }

    @Test
    fun timeLabelsFollowTheStyleWithAnEnDash() {
        // Android's best patterns for Finnish 24-hour and English 12-hour time (spec 74 L10N-FR-41).
        val fi = TimeLabels(ZoneId.of("Europe/Helsinki"), TimeStyle(Locale.forLanguageTag("fi"), "HH.mm", "EEE d.M."))
        val en = TimeLabels(ZoneId.of("Europe/Helsinki"), TimeStyle(Locale.US, "h:mm a", "EEE, M/d"))
        val t = 1_790_279_220_000L // 22.47 in Helsinki
        assertEquals("22.47", fi.guideTime(t))
        assertEquals("22.47–23.17", fi.guideRange(t, t + 30 * min))
        assertEquals("10:47 PM", en.playerTime(t))
        assertEquals("Thu, 9/24", en.dayLabel(t))
        // The zone applies; an invalid id falls back to the TV's (L10N-FR-32).
        assertEquals("21.47", TimeLabels(ZoneId.of("Europe/Stockholm"), TimeStyle.fixed24(Locale.ROOT).copy(clockPattern = "HH.mm")).guideTime(t))
        assertEquals(ZoneId.systemDefault(), TimeLabels.zoneOf("Not/AZone"))
        val labels = fi
        assertEquals(TimeLabels.RelativeDay.TOMORROW, labels.relativeDay(t + 2 * 60 * min, t))
        assertEquals(TimeLabels.RelativeDay.TODAY, labels.relativeDay(t, t))
        assertNull(labels.relativeDay(t + 3 * GuideWindow.DAY_MS, t))
        assertEquals(ZoneId.systemDefault(), TimeLabels.zoneOf("Not/AZone"))
        assertEquals(ZoneId.systemDefault(), TimeLabels.zoneOf(null))
    }

    private fun programme(id: Long, fromMin: Int, toMin: Int, subtitle: String? = null, base: Long = 0): GuideProgramme =
        GuideProgramme(
            id = id, start = base + fromMin * min, stop = base + toMin * min, title = "P$id", subtitle = subtitle,
            firstCategory = null, genre = null, hasDescription = false, categoryCount = 0, key = "k$id",
        )
}
