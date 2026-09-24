package com.sohva.tv.ui.design

import androidx.compose.ui.graphics.toArgb
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.theme.Palettes
import com.sohva.tv.ui.design.theme.SohvaPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every colour of every theme equals the resolved table in design/01 §5.1, read from the kit
 * itself, so the code and the specification cannot drift apart.
 */
class PaletteParityTest {
    private val themesInTableOrder = listOf(
        ColorThemeId.ORIGINAL, ColorThemeId.NORDIC_SLATE, ColorThemeId.COZY_HEARTH, ColorThemeId.CYBER_PLUM,
        ColorThemeId.NORD, ColorThemeId.EVERFOREST, ColorThemeId.KANAGAWA,
    )

    private val tokens: Map<String, (SohvaPalette) -> Int> = mapOf(
        "background" to { p -> p.background.toArgb() },
        "backgroundTop" to { p -> p.backgroundTop.toArgb() },
        "backgroundBottom" to { p -> p.backgroundBottom.toArgb() },
        "panel" to { p -> p.panel.toArgb() },
        "surfaceSubtle" to { p -> p.surfaceSubtle.toArgb() },
        "surface" to { p -> p.surface.toArgb() },
        "surfaceRaised" to { p -> p.surfaceRaised.toArgb() },
        "surfaceFocused" to { p -> p.surfaceFocused.toArgb() },
        "divider" to { p -> p.divider.toArgb() },
        "outline" to { p -> p.outline.toArgb() },
        "focus" to { p -> p.focus.toArgb() },
        "secondaryGlow" to { p -> p.secondaryGlow.toArgb() },
        "textPrimary" to { p -> p.textPrimary.toArgb() },
        "textMuted" to { p -> p.textMuted.toArgb() },
        "textDim" to { p -> p.textDim.toArgb() },
        "textDisabled" to { p -> p.textDisabled.toArgb() },
        "playerInfoSurface" to { p -> p.playerInfoSurface.toArgb() },
        "accent" to { p -> p.accent.toArgb() },
        "danger" to { p -> p.danger.toArgb() },
        "rating" to { p -> p.rating.toArgb() },
    )

    @Test
    fun everyTokenMatchesTheResolvedTable() {
        val table = resolvedTable()
        assertTrue("table rows found: ${table.keys}", table.keys.containsAll(tokens.keys))
        for ((token, read) in tokens) {
            val row = table.getValue(token)
            themesInTableOrder.forEachIndexed { i, theme ->
                val cell = row[i].takeUnless { it == "same" } ?: row[0]
                assertEquals("$token in $theme", parse(cell), read(Palettes.of(theme)))
            }
        }
    }

    /** Token → the seven cells of design/01 §5.1, as written (`#RRGGBB`, `#AARRGGBB` or "same"). */
    private fun resolvedTable(): Map<String, List<String>> {
        val doc = File("../../docs/rebuild/design/01-design-system.md").readText()
        val section = doc.substringAfter("### 5.1 Resolved palettes").substringBefore("\n## ")
        return section.lines()
            .filter { it.startsWith("| ") && it.contains('`') || it.contains("| same") }
            .mapNotNull { line ->
                val cells = line.trim('|').split('|').map { it.trim().trim('`') }
                val name = cells.first()
                if (name in tokens) name to cells.drop(1).map { it.substringBefore(' ') } else null
            }
            .toMap()
    }

    private fun parse(cell: String): Int {
        val hex = cell.removePrefix("#")
        val argb = if (hex.length == 6) "FF$hex" else hex
        return argb.toLong(16).toInt()
    }
}
