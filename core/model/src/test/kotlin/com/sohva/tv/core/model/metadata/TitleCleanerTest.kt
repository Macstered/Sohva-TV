package com.sohva.tv.core.model.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

/** Spec 41 §11: the table of META-FR-23, the work keys of META-FR-67, and equality with the regex reference (§9.1). */
class TitleCleanerTest {
    private data class Row(val input: String, val search: String, val normalized: String, val year: Int?)

    private val table = listOf(
        Row("The Bear (2022) S02E06", "The Bear", "the bear", null),
        Row("The Bear (2022)", "The Bear", "the bear", 2022),
        Row("The Shadow's Edge [Multi-Sub&Audio] [4K] [2025]", "The Shadow's Edge", "the shadow s edge", 2025),
        Row("[FIN] Foundation - 4K", "Foundation", "foundation", null),
        Row("ENG | Avatar UHD", "Avatar", "avatar", null),
        Row("21 Bridges [Multi-Subs] [2019] [4K]", "21 Bridges", "21 bridges", null),
        Row("The Shawshank Redemption [IMDB] [1994]", "The Shawshank Redemption", "the shawshank redemption", 1994),
        Row("Finding Nemo [KIDS] (Animated) {Multi Audio} [2003]", "Finding Nemo", "finding nemo", 2003),
        Row("[REC]", "REC", "rec", null),
        Row("4K - NC Avatar: The Last Airbender (2024)", "Avatar: The Last Airbender", "avatar the last airbender", 2024),
        Row("NC - DuckTales 2017", "DuckTales", "ducktales", 2017),
        Row("NORDIC | The Bear 2022", "The Bear", "the bear", 2022),
        Row("HDR10+ - Dune 2021", "Dune", "dune", 2021),
        Row("1917", "1917", "1917", null),
    )

    @Test
    fun theSpecTableHolds() {
        for (row in table) {
            assertEquals(row.input, row.search, TitleCleaner.searchTitle(row.input))
            assertEquals(row.input, row.normalized, TitleCleaner.normalizeTitle(row.input))
            assertEquals(row.input, row.year, TitleCleaner.yearFromTitle(row.input))
        }
    }

    @Test
    fun edgeCasesOfThePrefixAndMarkers() {
        // Step C keeps the last token of a name made only of tokens (spec 41 §4.4).
        assertEquals(" HD", TitleCleaner.decorationPrefix("4K HD"))
        assertEquals(" -", TitleCleaner.decorationPrefix("4K --"))
        assertEquals("Show", TitleCleaner.searchTitle("Show K2 J6"))
        assertEquals("Show", TitleCleaner.searchTitle("Show K2J6"))
        assertEquals("The Matrix", TitleCleaner.searchTitle("FIN The Matrix"))
    }

    @Test
    fun workKeysFoldCopiesAndKeepFilmsApart() {
        val matrix = listOf("FIN | The Matrix (1999) 4K", "The Matrix 1999 [MULTI-SUBS] 1080p", "NORDIC - The Matrix - HDR10", "The Matrix 1999 [MULTI-SUBS] HDR10 1080p", "[FI] The Matrix")
            .map { WorkKeys.of(it, 1999) }.toSet()
        assertEquals(1, matrix.size)
        assertEquals(WorkKeys.of("The Matrix", null), WorkKeys.of("FIN The Matrix", null))
        assertNotEquals(WorkKeys.of("The Matrix", 1999), WorkKeys.of("The Matrix Reloaded", 1999))
        assertNotEquals(WorkKeys.of("The Thing", 1982), WorkKeys.of("The Thing", 2011))
        assertEquals(WorkKeys.of("The Matrix", 1999), WorkKeys.of("The Matrix (1999)", null))
        assertNotEquals(WorkKeys.of("The Matrix", 1999), WorkKeys.of("The Matrix", 2000))
        assertEquals("tmdb:603", WorkKeys.of("Anything", 1999, "603"))
        assertEquals(WorkKeys.of("The Matrix", 1999), WorkKeys.of("The Matrix", 1999, " "))
        assertEquals(WorkKeys.of("Amelie", 2001), WorkKeys.of("AMÉLIE", 2001))
        assertNotEquals(WorkKeys.of("[MULTI-SUBS]", null), WorkKeys.of("[4K]", null))
    }

    @Test
    fun theHandWrittenCleanerEqualsTheRegexesOnGeneratedTitles() {
        val random = Random(20260925)
        var checked = 0
        repeat(CORPUS) {
            val title = generate(random)
            assertEquals("searchTitle <$title>", TitleReference.searchTitle(title), TitleCleaner.searchTitle(title))
            assertEquals("normalizeTitle <$title>", TitleReference.normalizeTitle(title), TitleCleaner.normalizeTitle(title))
            assertEquals("yearFromTitle <$title>", TitleReference.yearFromTitle(title), TitleCleaner.yearFromTitle(title))
            val year = if (random.nextInt(3) == 0) 1990 + random.nextInt(30) else null
            assertEquals("workKey <$title>", TitleReference.workKey(title, year), WorkKeys.of(title, year))
            checked++
        }
        assertEquals(CORPUS, checked)
    }

    @Test
    fun theFixtureTitlesMatchTheReferenceToo() {
        for (row in table) {
            assertEquals(TitleReference.searchTitle(row.input), TitleCleaner.searchTitle(row.input))
            assertEquals(TitleReference.normalizeTitle(row.input), TitleCleaner.normalizeTitle(row.input))
        }
    }

    /** A random title from the pieces providers put in names: prefixes, words, markers, years, tags, brackets and delimiters. */
    private fun generate(random: Random): String {
        val b = StringBuilder()
        val parts = 1 + random.nextInt(7)
        repeat(parts) {
            if (b.isNotEmpty() || random.nextInt(4) == 0) b.append(SEPARATORS.random(random))
            val pool = POOLS.random(random)
            var piece = pool.random(random)
            if (random.nextInt(5) == 0) piece = piece.uppercase()
            if (random.nextInt(9) == 0) piece = piece.replaceFirstChar { it.titlecase() }
            b.append(piece)
        }
        if (random.nextInt(6) == 0) b.append(SEPARATORS.random(random))
        return b.toString()
    }

    private companion object {
        const val CORPUS = 100_000

        val WORDS = listOf(
            "The", "Matrix", "Bear", "Dune", "Amélie", "Café", "Fin", "del", "mundo", "Nordic", "Noir", "English", "Patient", "Deutschland",
            "21", "Bridges", "1917", "300", "Avatar", "Foundation", "Top", "Gear", "sub", "audio", "vision", "hdr", "Beach", "Ｆｉｎ", "ﬁlm",
            "Škoda", "Ærø", "hd", "HDfilm", "multi", "S01", "E02", "k2", "j6", "only", "on", "devices", "imdb", "x", "a_b", "é",
        )
        val PREFIXES = listOf(
            "FIN", "ENG", "SWE", "fi", "en", "NC", "NORDIC", "4K", "UHD", "FHD", "HD", "HDR", "HDR10", "HDR10+", "Dolby Vision", "DolbyVision",
            "DV", "x264", "x265", "HEVC", "MULTI", "MULTI-SUBS", "Multi Subs", "multisubtitles", "Multi-Audio", "VIP", "VOD", "spa", "ger",
        )
        val MARKERS = listOf("S01E02", "s1e1", "S123E1", "S01E0012", "K2 J6", "k2j6", "K12  J123", "s1e2x", "_s1e2", "és1e2", "S1E2é")
        val YEARS = listOf("1999", "2021", "(2020)", "[2019]", "(1899)", "2100", "(2019]", "20190", "[2021)", "１９９９")
        val BRACKETS = listOf(
            "[4K]", "[IMDB]", "[IMDB TOP 250]", "[imdb 7.5]", "[Only on Samsung devices]", "(Animated)", "{Multi Audio}", "[MULTI-SUBS]",
            "[Multi-Sub&Audio]", "[", "]", "(", ")", "{", "}", "[[4K]]", "[a[b]", "(x(y))", "[FIN]", "(fi]", "[eng)", "[\n]", "［4K］", "［IMDB］",
        )
        val SUFFIXES = listOf("1080p", "720p", "2160p", "4K HDR10", "| HD", "- UHD", "— x265 HEVC", "MULTI-SUBS & AUDIO", "multi audio", "Dolby  Vision")
        val POOLS = listOf(WORDS, WORDS, WORDS, PREFIXES, MARKERS, YEARS, BRACKETS, SUFFIXES)
        val SEPARATORS = listOf(" ", " ", " ", "  ", "\t", " | ", "|", " - ", "-", " • ", "·", ":", " : ", "–", " — ", " ", " ")
    }
}
