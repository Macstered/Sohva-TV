package com.sohva.tv.ui.design

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Spec 74 §11 "Unit": every language has every English text of the same kind with the same
 * placeholders, and every plural has `one` and `other`. English stays only where beta 23 kept it:
 * the Trakt texts in the five drafts and the addon phone page everywhere (reference/README.md).
 */
class TranslationParityTest {
    private val res = File("src/main/res")
    private val languages = listOf("fi", "es", "pt", "de", "sv", "it")
    private val drafts = setOf("es", "pt", "de", "sv", "it")

    private data class Entry(val kind: String, val placeholders: List<String>, val quantities: Set<String>)

    private fun read(file: File): Map<String, Entry> {
        if (!file.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = HashMap<String, Entry>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val e = children.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val items = e.getElementsByTagName("item")
            val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.filter { it.isNotEmpty() }.toSet()
            // A plural's placeholders are those of its "other" form.
            val text = if (e.tagName == "plurals") {
                (0 until items.length).map { items.item(it) as Element }.firstOrNull { it.getAttribute("quantity") == "other" }?.textContent.orEmpty()
            } else {
                e.textContent
            }
            out[e.getAttribute("name")] = Entry(e.tagName, placeholders(text), quantities)
        }
        return out
    }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    private fun englishOnly(file: String, language: String): Boolean =
        file == "strings_discover_entry.xml" || (file == "strings_trakt.xml" && language in drafts)

    @Test
    fun everyLanguageHasEveryTextWithTheSamePlaceholders() {
        val problems = ArrayList<String>()
        val files = res.resolve("values").listFiles { f -> f.name.endsWith(".xml") }!!.sortedBy { it.name }
        assertTrue("no string files found", files.isNotEmpty())
        for (file in files) {
            val english = read(file)
            for ((key, entry) in english) {
                if (entry.kind == "plurals" && !entry.quantities.containsAll(setOf("one", "other"))) problems += "values/${file.name} $key: plural without one/other"
            }
            for (language in languages) {
                val translated = read(res.resolve("values-$language/${file.name}"))
                for ((key, entry) in english) {
                    val other = translated[key]
                    if (other == null) {
                        if (!englishOnly(file.name, language)) problems += "$language/${file.name} $key: missing"
                        continue
                    }
                    if (other.kind != entry.kind) problems += "$language $key: ${other.kind} instead of ${entry.kind}"
                    if (other.placeholders != entry.placeholders) problems += "$language $key: placeholders ${other.placeholders} instead of ${entry.placeholders}"
                    if (other.kind == "plurals" && !other.quantities.containsAll(setOf("one", "other"))) problems += "$language $key: plural without one/other"
                }
                for (key in translated.keys - english.keys) problems += "$language/${file.name} $key: not in English"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theLocaleConfigListsExactlyTheSevenLanguages() {
        val config = File("../../app/src/main/res/xml/locales_config.xml").readText()
        val tags = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toList()
        assertTrue(tags.toString(), tags == listOf("en", "fi", "es", "pt", "de", "sv", "it"))
        for (language in languages) assertTrue(language, res.resolve("values-$language").isDirectory)
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]""")
    }
}
