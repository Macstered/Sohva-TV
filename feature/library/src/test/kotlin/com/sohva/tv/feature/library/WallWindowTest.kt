package com.sohva.tv.feature.library

import com.sohva.tv.core.data.database.WallRow
import com.sohva.tv.core.data.vod.WallItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 40 §11 "Pager": window bound 600, absolute indices stable across drops and prepends. */
class WallWindowTest {
    private fun item(n: Int) = WallItem(WallRow(n.toLong(), "vod:movie:s:$n", "s", "T$n", "t$n", null, null, null, 0, null))

    private fun page(from: Int, size: Int = WallWindow.PAGE) = (from until from + size).map(::item)

    @Test
    fun theWindowHoldsFivePagesAndKeepsAbsoluteIndices() {
        var w = WallWindow.of(page(0))
        for (p in 1 until 7) w = w.appended(page(p * WallWindow.PAGE))
        assertEquals(WallWindow.MAX_ITEMS, w.items.size)
        assertEquals(2 * WallWindow.PAGE, w.first)
        // The card at absolute index 500 is the 500th entry, whatever was dropped.
        assertEquals(500L, w.itemAt(500)!!.row.id)
        assertNull(w.itemAt(10))
        assertFalse(w.atEnd)
    }

    @Test
    fun aShortPageEndsTheWall() {
        val w = WallWindow.of(page(0)).appended(page(120, 7))
        assertTrue(w.atEnd)
        assertEquals(127, w.slots(columns = 6))
        assertEquals(126, WallWindow.of(page(0)).slots(columns = 6))
    }

    @Test
    fun prependingRestoresDroppedPagesAtTheirIndices() {
        var w = WallWindow.of(page(0))
        for (p in 1 until 7) w = w.appended(page(p * WallWindow.PAGE))
        w = w.prepended(page(120))
        assertEquals(WallWindow.PAGE, w.first)
        assertEquals(130L, w.itemAt(130)!!.row.id)
        assertEquals(WallWindow.MAX_ITEMS, w.items.size)
        assertFalse(w.atEnd)
        // The front page comes back at index 0.
        w = w.prepended(page(0))
        assertEquals(0, w.first)
        assertEquals(0L, w.itemAt(0)!!.row.id)
    }

    @Test
    fun columnsComeFromTheAbsoluteIndex() {
        var w = WallWindow.of(page(0))
        for (p in 1 until 7) w = w.appended(page(p * WallWindow.PAGE))
        for (columns in 6..9) {
            val index = w.first + 13
            assertEquals(index % columns, (w.itemAt(index)!!.row.id % columns).toInt())
        }
    }

    @Test
    fun prefetchStartsThreeRowsFromEitherEnd() {
        var w = WallWindow.of(page(0))
        assertFalse(w.wantsNext(focused = 101, columns = 6))
        assertTrue(w.wantsNext(focused = 102, columns = 6))
        for (p in 1 until 7) w = w.appended(page(p * WallWindow.PAGE))
        assertTrue(w.wantsPrevious(focused = w.first + 17, columns = 6))
        assertFalse(w.wantsPrevious(focused = w.first + 18, columns = 6))
        assertFalse(WallWindow.of(page(0)).wantsPrevious(focused = 0, columns = 6))
    }
}
