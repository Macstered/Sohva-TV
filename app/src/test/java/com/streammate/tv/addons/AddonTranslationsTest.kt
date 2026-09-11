package com.streammate.tv.addons

import com.streammate.tv.app.AppLocale
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class AddonTranslationsTest {
    private val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private fun entries(directory: String): Map<String, Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "$directory/strings_addons.xml"))
        val children = document.documentElement.childNodes
        val elements = (0 until children.length).mapNotNull { children.item(it) as? Element }
        assertEquals("Duplicate resource names in $directory", elements.size, elements.map { it.getAttribute("name") }.toSet().size)
        return elements.associateBy { it.getAttribute("name") }
    }

    @Test fun everyOfferedLanguageHasAllAddonStringsAndPluralForms() {
        val base = entries("values")
        assertTrue(base.size >= 298)
        for (language in AppLocale.SUPPORTED_TAGS.filter { it != "en" }) {
            val localized = entries("values-$language")
            assertEquals(language, base.keys, localized.keys)
            base.forEach { (name, original) ->
                val translated = localized.getValue(name)
                assertEquals(name, original.tagName, translated.tagName)
                assertNotEquals("Untranslated resource: $language/$name", "false", translated.getAttribute("translatable"))
                assertTrue("Empty translation: $language/$name", translated.textContent.isNotBlank())
                if (original.tagName == "plurals") {
                    val items = translated.getElementsByTagName("item")
                    assertEquals(setOf("one", "other"), (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet())
                }
            }
        }
        assertTrue(base.values.none { it.getAttribute("translatable") == "false" })
    }

    @Test fun translationsPreserveNumberedFormatArguments() {
        val base = entries("values")
        val format = Regex("%[0-9]+\\$[ds]")
        fun signatures(element: Element): List<String> {
            val values = if (element.tagName == "string") listOf(element.textContent) else {
                val items = element.getElementsByTagName("item")
                (0 until items.length).map { items.item(it).textContent }
            }
            return values.map { text -> format.findAll(text).map { it.value }.sorted().joinToString() }
        }
        for (language in AppLocale.SUPPORTED_TAGS.filter { it != "en" }) {
            entries("values-$language").forEach { (name, value) ->
                val expected = signatures(base.getValue(name)).first()
                signatures(value).forEach { actual -> assertEquals("$language/$name", expected, actual) }
            }
        }
    }
}
