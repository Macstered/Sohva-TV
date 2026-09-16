package com.streammate.tv.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorThemeTest {
    @Test
    fun storedIdsRoundTripAndMissingOrUnknownThemesUseTheOriginal() {
        ColorTheme.entries.forEach { assertEquals(it, ColorTheme.fromStored(it.storedValue)) }
        assertEquals(ColorTheme.ORIGINAL, ColorTheme.fromStored(null))
        assertEquals(ColorTheme.ORIGINAL, ColorTheme.fromStored("future_theme"))
        assertSame(StreamMateDefaultPalette, ColorTheme.DEFAULT.palette)
    }

    @Test
    fun newThemesKeepSmallTextReadableAcrossTheirSurfaceLadder() {
        ColorTheme.entries.filter { it != ColorTheme.ORIGINAL }.forEach { theme ->
            val palette = theme.palette
            val surfaces = listOf(palette.background, palette.panel, palette.surfaceRaised, palette.surfaceFocused)
            val text = listOf(palette.textPrimary, palette.textMuted, palette.textDim, palette.focus)
            surfaces.forEach { surface ->
                text.forEach { foreground ->
                    val ratio = contrast(foreground, surface)
                    assertTrue("$theme: $foreground on $surface has contrast $ratio", ratio >= 4.5f)
                }
            }
            assertTrue("$theme focused control", contrast(palette.background, palette.textPrimary) >= 7f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }
}
