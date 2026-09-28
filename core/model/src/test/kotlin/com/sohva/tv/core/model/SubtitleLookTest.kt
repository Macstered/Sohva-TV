package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.SubtitleBackground
import com.sohva.tv.core.model.player.SubtitleColor
import com.sohva.tv.core.model.player.SubtitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PLAY-27, spec 30 §4.14: subtitle size, colour and background, each following the TV unless chosen. */
class SubtitleLookTest {
    @Test
    fun nothingStoredFollowsTheTv() {
        assertEquals(SubtitleSize.FOLLOW_TV, SubtitleSize.fromStored(null))
        assertEquals(SubtitleColor.FOLLOW_TV, SubtitleColor.fromStored(null))
        assertEquals(SubtitleBackground.FOLLOW_TV, SubtitleBackground.fromStored(null))
        assertEquals(SubtitleBackground.FOLLOW_TV, SubtitleBackground.fromStored("RAINBOW"))
        assertNull(SubtitleSize.FOLLOW_TV.scale)
        assertNull(SubtitleColor.FOLLOW_TV.argb)
    }

    @Test
    fun beta23sValuesKeepTheirMeaning() {
        assertEquals(listOf(0.8f, 1.0f, 1.3f, 1.6f), listOf(SubtitleSize.SMALL, SubtitleSize.NORMAL, SubtitleSize.LARGE, SubtitleSize.VERY_LARGE).map { it.scale })
        assertEquals(0xFFFFFFFF, SubtitleColor.fromStored("WHITE").argb)
        assertEquals(0xFFFFE14D, SubtitleColor.fromStored("YELLOW").argb)
        assertEquals(SubtitleBackground.BOX, SubtitleBackground.fromStored("BOX"))
    }
}
