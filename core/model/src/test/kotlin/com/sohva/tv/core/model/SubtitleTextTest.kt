package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.AddonLanguages
import com.sohva.tv.core.model.player.SubtitleFormat
import com.sohva.tv.core.model.player.SubtitleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 50 ADDON-FR-96, -101, -102, -103. */
class SubtitleTextTest {
    private val srt = "1\n00:00:01,000 --> 00:00:02,500\nHello\n\n2\n00:01:00,000 --> 00:01:02,000\nAgain\n"
    private val vtt = "WEBVTT\n\n00:01.000 --> 00:02.500 align:start\nHello\n"
    private val ass = "[Script Info]\nTitle: t\n\n[Events]\nFormat: Layer, Start, End, Style, Text\nDialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,Hi, there\n"

    @Test
    fun formatsAreRecognisedByContentOnly() {
        assertEquals(SubtitleFormat.SUBRIP, SubtitleText.detect(srt))
        assertEquals(SubtitleFormat.WEBVTT, SubtitleText.detect(vtt))
        assertEquals(SubtitleFormat.SSA, SubtitleText.detect(ass))
        assertNull(SubtitleText.detect("PK zip archive"))
        assertNull(SubtitleText.detect("[Script Info]\nno events"))
    }

    @Test
    fun theByteOrderMarkGoesAndOversizedFilesAreRefused() {
        assertEquals("WEBVTT", SubtitleText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "WEBVTT".toByteArray()))
        assertNull(SubtitleText.decode(ByteArray(SubtitleText.MAX_BYTES + 1)))
    }

    @Test
    fun timingShiftsOnlyTheTimesAndStopsAtZero() {
        val later = SubtitleText.shift(srt, SubtitleFormat.SUBRIP, 1_500)
        assertTrue(later, later.contains("00:00:02,500 --> 00:00:04,000"))
        assertTrue(later, later.contains("00:01:01,500 --> 00:01:03,500"))
        assertTrue(later.contains("Hello"))
        val earlier = SubtitleText.shift(srt, SubtitleFormat.SUBRIP, -2_000)
        assertTrue(earlier, earlier.contains("00:00:00,000 --> 00:00:00,500"))
        val v = SubtitleText.shift(vtt, SubtitleFormat.WEBVTT, 250)
        assertTrue(v, v.contains("00:00:01.250 --> 00:00:02.750 align:start"))
        val a = SubtitleText.shift(ass, SubtitleFormat.SSA, 1_000)
        assertTrue(a, a.contains("Dialogue: 0,0:00:02.00,0:00:03.50,Default,,0,0,0,,Hi, there"))
        assertEquals("+0.100 s", SubtitleText.label(100))
        assertEquals("-1.500 s", SubtitleText.label(-1_500))
    }

    @Test
    fun languagesNormalise() {
        assertEquals("fi", AddonLanguages.normalise("fin"))
        assertEquals("no", AddonLanguages.normalise("nob"))
        assertEquals("pt", AddonLanguages.normalise("pt-BR"))
        assertEquals("he", AddonLanguages.normalise("heb"))
        assertNull(AddonLanguages.normalise("und"))
        assertNull(AddonLanguages.normalise(" "))
        assertEquals("xx", AddonLanguages.normalise("XX"))
    }
}
