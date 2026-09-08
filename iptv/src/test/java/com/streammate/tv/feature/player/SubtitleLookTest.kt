package com.streammate.tv.feature.player

import com.streammate.tv.app.SubtitleBackground
import com.streammate.tv.app.SubtitleTextColor
import com.streammate.tv.app.SubtitleTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleLookTest {
    private val tv = SubtitleLook(foreground = 0xFFEEEEEE.toInt(), background = 0x80000000.toInt(), windowColor = 0, edgeType = 1, edgeColor = 0xFF111111.toInt())

    @Test
    fun `following the TV in every way leaves the TV's style alone`() {
        assertNull(subtitleLook(SubtitleAppearance(), tv))
        assertNull(subtitleLook(SubtitleAppearance(size = SubtitleTextSize.LARGE), tv))
    }

    @Test
    fun `a colour alone keeps the TV's background and edge`() {
        val look = subtitleLook(SubtitleAppearance(color = SubtitleTextColor.YELLOW), tv)
        assertEquals(tv.copy(foreground = SubtitleTextColor.YELLOW.argb!!), look)
    }

    @Test
    fun `a background alone keeps the TV's text colour`() {
        val box = subtitleLook(SubtitleAppearance(background = SubtitleBackground.BOX), tv)!!
        assertEquals(tv.foreground, box.foreground)
        assertEquals(SUBTITLE_BOX_COLOR, box.background)
        assertEquals(SUBTITLE_EDGE_NONE, box.edgeType)

        val shadow = subtitleLook(SubtitleAppearance(background = SubtitleBackground.SHADOW), tv)!!
        assertEquals(SUBTITLE_TRANSPARENT, shadow.background)
        assertEquals(SUBTITLE_EDGE_DROP_SHADOW, shadow.edgeType)

        val none = subtitleLook(SubtitleAppearance(background = SubtitleBackground.NONE), tv)!!
        assertEquals(SUBTITLE_TRANSPARENT, none.background)
        assertEquals(SUBTITLE_EDGE_NONE, none.edgeType)
    }

    @Test
    fun `sizes grow from small to very large around the default`() {
        assertEquals(listOf(null, 0.8f, 1f, 1.3f, 1.6f), SubtitleTextSize.entries.map { it.factor })
    }
}
