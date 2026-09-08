package com.streammate.tv.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every translation carries the same strings as the English, with the same
 * plural forms and the same placeholders. A drafted translation with a
 * missing `%2$s` crashes the screen that formats it, so this fails the build
 * before a device ever sees it.
 */
class TranslationParityTest {
    private val modules = listOf("core", "app", "iptv", "sportmate")
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `every translation matches the English keys, plural forms and placeholders`() {
        val problems = mutableListOf<String>()
        for (module in modules) {
            val res = File(root, "$module/src/main/res")
            val english = parse(File(res, "values/strings.xml"))
            val translations = res.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }.orEmpty()
                .map { File(it, "strings.xml") }.filter { it.exists() }
            assertTrue("$module has no translations", translations.isNotEmpty())
            for (file in translations) {
                val tag = file.parentFile.name.removePrefix("values-")
                val translated = parse(file)
                val translatable = english.filterValues { !it.untranslatable }
                (translatable.keys - translated.keys).forEach { problems += "$module/$tag: missing $it" }
                (translated.keys - english.keys).forEach { problems += "$module/$tag: unknown $it" }
                for ((name, entry) in translatable) {
                    val other = translated[name] ?: continue
                    if (entry.plural != other.plural) problems += "$module/$tag: $name is ${if (entry.plural) "a plural" else "a string"} in English"
                    if (entry.plural && other.quantities != entry.quantities) {
                        problems += "$module/$tag: $name has forms ${other.quantities} against ${entry.quantities}"
                    }
                    if (placeholders(entry.text) != placeholders(other.text)) {
                        problems += "$module/$tag: $name placeholders ${placeholders(other.text)} against ${placeholders(entry.text)}"
                    }
                }
            }
        }
        assertEquals(problems.joinToString("\n"), 0, problems.size)
    }

    @Test
    fun `every supported tag has a translation folder in every module`() {
        for (tag in AppLocale.SUPPORTED_TAGS - "en") {
            for (module in modules) {
                assertTrue("$module has no values-$tag", File(root, "$module/src/main/res/values-$tag/strings.xml").exists())
            }
        }
    }

    private data class Entry(val text: String, val plural: Boolean, val quantities: Set<String>, val untranslatable: Boolean)

    private fun parse(file: File): Map<String, Entry> {
        val xml = file.readText()
        val entries = mutableMapOf<String, Entry>()
        Regex("<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            entries[m.groupValues[1]] = Entry(m.groupValues[3], false, emptySet(), "translatable=\"false\"" in m.groupValues[2])
        }
        Regex("<plurals name=\"([^\"]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val items = Regex("<item quantity=\"([^\"]+)\">(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[2]).toList()
            entries[m.groupValues[1]] = Entry(items.joinToString(" ") { it.groupValues[2] }, true, items.map { it.groupValues[1] }.toSet(), false)
        }
        return entries
    }

    /** The positional placeholders, as a multiset: `%1$s`, `%2$d`, `%1$.1f`, and a bare `%d` counts as one. */
    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[.\\d]*[sdf]").findAll(text.replace("%%", "")).map { it.value }.sorted().toList()
}
