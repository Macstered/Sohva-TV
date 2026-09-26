package com.sohva.tv.core.model.update

/**
 * A release body as plain lines for About (spec 72 ABOUT-FR-20, -21). The body is Markdown written
 * for GitHub; About shows words only. Regular expressions: run off the main thread, once per body.
 */
object ReleaseNotes {
    const val MAX_LENGTH: Int = 4_000

    private val CHANGED = Regex("""^#{1,3}\s+changed\b.*""", RegexOption.IGNORE_CASE)
    private val SECTION_END = Regex("""^#{1,2}\s+\S.*""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val LANGUAGE = Regex("""^##\s+(English|Suomi)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * The notes of [body] for a viewer reading [language] (a BCP 47 tag): its `## Suomi` section for
     * Finnish, else its `## English` section when the body has language sections (ABOUT-FR-21, the
     * spec's proposal), else the whole body; then ABOUT-FR-20. Null when nothing is left.
     */
    fun of(body: String, language: String): String? {
        val lines = languageSection(body.lines(), language) ?: body.lines()
        val text = plain(changedSection(lines)).take(MAX_LENGTH).trim()
        return text.ifEmpty { null }
    }

    private fun languageSection(lines: List<String>, language: String): List<String>? {
        val headings = lines.withIndex().mapNotNull { (i, line) -> LANGUAGE.find(line.trim())?.let { i to it.groupValues[1].lowercase() } }
        if (headings.isEmpty()) return null
        val wanted = if (language.lowercase().startsWith("fi")) "suomi" else "english"
        val start = headings.firstOrNull { it.second == wanted } ?: headings.firstOrNull { it.second == "english" } ?: return null
        val rest = lines.subList(start.first + 1, lines.size)
        val end = rest.indexOfFirst { SECTION_END.matches(it.trim()) }
        return if (end < 0) rest else rest.subList(0, end)
    }

    private fun changedSection(lines: List<String>): List<String> {
        val start = lines.indexOfFirst { CHANGED.matches(it.trim()) }
        if (start < 0) return lines
        val rest = lines.subList(start + 1, lines.size)
        val end = rest.indexOfFirst { SECTION_END.matches(it.trim()) }
        return if (end < 0) rest else rest.subList(0, end)
    }

    /** ABOUT-FR-20 step 3–5: bullets, headings and paragraphs as blocks, wrapped lines joined. */
    private fun plain(lines: List<String>): String {
        val blocks = ArrayList<StringBuilder>()
        var open = false
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> open = false
                line.startsWith("- ") || line.startsWith("* ") -> {
                    blocks += StringBuilder("• ").append(line.substring(2).trim())
                    open = true
                }
                line.startsWith("#") -> {
                    blocks += StringBuilder(line.trimStart('#').trim())
                    open = false
                }
                open -> blocks.last().append(' ').append(line)
                else -> {
                    blocks += StringBuilder(line)
                    open = true
                }
            }
        }
        return blocks.joinToString("\n") { clean(it.toString()) }
    }

    private fun clean(block: String): String = LINK.replace(block) { it.groupValues[1] }.replace("**", "").replace("`", "")
}
