package com.sohva.tv.core.model

import com.sohva.tv.core.model.player.VodLanguages
import com.sohva.tv.core.model.settings.ArtworkCacheLimit
import com.sohva.tv.core.model.settings.TimeZones
import com.sohva.tv.core.model.settings.VodLanguageSlot
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 70 §11 "Unit": language pairing, artwork sizes and limits, time-zone labels and filtering. */
class SettingsChoicesTest {
    @Test
    fun choosingThePartnersLanguageClearsThePartnerAndAutomaticNeverDoes() {
        val start = VodLanguages(audio = "fi", audioSecond = "en")
        assertEquals(VodLanguages(audio = "en", audioSecond = null), VodLanguageSlot.choose(start, VodLanguageSlot.AUDIO, "EN "))
        assertEquals(VodLanguages(audio = null, audioSecond = "en"), VodLanguageSlot.choose(start, VodLanguageSlot.AUDIO, null))
        assertEquals(VodLanguages(audio = "fi", audioSecond = "en", subtitles = "fi"), VodLanguageSlot.choose(start, VodLanguageSlot.SUBTITLES, "fi"))
    }

    @Test
    fun artworkLimitsAndSizes() {
        assertEquals(ArtworkCacheLimit.MEDIUM, ArtworkCacheLimit.fromStored(null))
        assertEquals(ArtworkCacheLimit.MEDIUM, ArtworkCacheLimit.fromStored("HUGE"))
        assertEquals(104_857_600L, ArtworkCacheLimit.SMALL.bytes)
        assertEquals(524_288_000L, ArtworkCacheLimit.LARGE.bytes)
        assertEquals("0 B", ArtworkCacheLimit.size(0))
        assertEquals("1023 B", ArtworkCacheLimit.size(1023))
        assertEquals("2 kB", ArtworkCacheLimit.size(2048))
        assertEquals("250 MB", ArtworkCacheLimit.size(262_144_000))
    }

    @Test
    fun zonesAreLabelledByCityAndOffsetAndFiltered() {
        val at = Instant.parse("2026-01-15T12:00:00Z")
        val ids = listOf("Europe/Helsinki", "America/Argentina/Buenos_Aires", "Etc/GMT+3", "SystemV/EST5", "UTC", "Asia/Kolkata", "GMT")
        val rows = TimeZones.rows(ids, at)
        assertEquals(listOf("America/Argentina/Buenos_Aires", "Asia/Kolkata", "Europe/Helsinki", "UTC"), rows.map { it.id })
        assertEquals("Buenos Aires, Argentina · UTC−3", rows[0].label)
        assertEquals("Kolkata · UTC+5:30", rows[1].label)
        assertEquals("Helsinki · UTC+2", rows[2].label)
        assertEquals("UTC · UTC", rows[3].label)
        assertEquals(listOf("Europe/Helsinki"), TimeZones.filter(rows, " HELSINKI ").map { it.id })
        assertEquals(listOf("Europe/Helsinki"), TimeZones.filter(rows, "europe").map { it.id })
        assertEquals("UTC−5:30", TimeZones.offset(ZoneId.of("-05:30"), at))
    }
}
