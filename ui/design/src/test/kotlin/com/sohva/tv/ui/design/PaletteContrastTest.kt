package com.sohva.tv.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.theme.Palettes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The smallest text must be readable from a sofa: WCAG's 4.5:1 for dim text on the ground and on
 * raised surfaces, and for the LIVE chip's text on its red, in every theme (decision "Contrast").
 */
class PaletteContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun dimTextIsReadableOnTheGroundAndOnRaisedSurfaces() {
        val problems = ArrayList<String>()
        for (theme in ColorThemeId.entries) {
            val p = Palettes.of(theme)
            val raised = p.surfaceRaised.compositeOver(p.background)
            for ((name, under) in listOf("background" to p.background, "surfaceRaised" to raised)) {
                val r = ratio(p.textDim, under)
                if (r < 4.5) problems += "$theme textDim on $name: %.2f".format(r)
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theLiveChipsTextIsReadableOnItsRed() {
        val problems = ArrayList<String>()
        for (theme in ColorThemeId.entries) {
            val r = ratio(Palettes.liveChipInk, Palettes.of(theme).danger)
            if (r < 4.5) problems += "$theme LIVE chip: %.2f".format(r)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
