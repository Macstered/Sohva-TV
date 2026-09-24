package com.sohva.tv.core.net.xmltv

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.text.StableIds
import com.sohva.tv.core.model.time.XmlTvTime
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

sealed interface XmlTvRecord

/** A `<channel>`: the list is kept whole, for the manual EPG mapping picker. */
data class XmlTvChannel(val id: String, val displayName: String?, val iconUrl: String?) : XmlTvRecord

/** A `<programme>` that passed the filter; times are the feed's, without the source's offset. */
data class XmlTvProgramme(
    val key: String,
    val channelId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val subTitle: String?,
    val description: String?,
    /** Non-empty `<category>` texts joined with U+001F, or null. */
    val categories: String?,
) : XmlTvRecord

/** Decides from the attributes alone whether a programme is read (spec 10 SRC-FR-83). */
fun interface ProgrammeFilter {
    fun keep(channelId: String, startMillis: Long, stopMillis: Long): Boolean
}

/**
 * Reads an XMLTV guide one record at a time (spec 10 §4.11) with a pull parser over the bytes, so
 * the parser itself detects UTF-16 and declared encodings.
 *
 * A programme the [filter] rejects is skipped before its children are read, so it costs no
 * strings and no digest ([hashedProgrammes] counts digests for the test that proves it). Nested
 * markup inside a text element is skipped and the text around it kept; beta 23 failed the whole
 * guide on it (spec 10 §8). Texts are cut to [MAX_TITLE_CHARS] and [MAX_DESCRIPTION_CHARS].
 */
class XmlTvReader(
    source: BufferedSource,
    private val filter: ProgrammeFilter = ProgrammeFilter { _, _, _ -> true },
    private val parser: XmlPullParser = newParser(),
    private val ids: StableIds = StableIds(),
) {
    var hashedProgrammes: Int = 0
        private set

    /** Every `<programme>` element met, kept or not: the guide's "came back empty" guard. */
    var seenProgrammes: Int = 0
        private set

    init {
        // A UTF-8 byte-order mark is dropped here; a UTF-16 one is the parser's to read.
        if (source.rangeEquals(0, UTF8_BOM)) source.skip(UTF8_BOM.size.toLong())
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(source.inputStream(), null)
    }

    /** The next channel or kept programme, or null at the end of the document. */
    fun next(): XmlTvRecord? = try {
        nextRecord()
    } catch (e: XmlPullParserException) {
        throw AppException(AppError.TransportFailed("invalid XML at line ${e.lineNumber}"), e)
    }

    private fun nextRecord(): XmlTvRecord? {
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> return null
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> channel()?.let { return it }
                    "programme" -> programme()?.let { return it }
                }
            }
        }
    }

    private fun channel(): XmlTvChannel? {
        val id = parser.getAttributeValue(null, "id")?.trim()
        if (id.isNullOrEmpty()) {
            skipElement()
            return null
        }
        var name: String? = null
        var icon: String? = null
        forEachChild { child ->
            when (child) {
                "display-name" -> {
                    val text = readText()
                    if (name == null) name = text.trim().take(MAX_TITLE_CHARS).ifEmpty { null }
                }
                "icon" -> {
                    parser.getAttributeValue(null, "src")?.trim()?.ifEmpty { null }?.let { icon = it }
                    skipElement()
                }
                else -> skipElement()
            }
        }
        return XmlTvChannel(id, name, icon)
    }

    private fun programme(): XmlTvProgramme? {
        seenProgrammes++
        val channel = parser.getAttributeValue(null, "channel")?.trim()
        val start = parser.getAttributeValue(null, "start")?.let(XmlTvTime::parseMillis)
        val stop = parser.getAttributeValue(null, "stop")?.let(XmlTvTime::parseMillis)
        if (channel.isNullOrEmpty() || start == null || stop == null || !filter.keep(channel, start, stop)) {
            skipElement()
            return null
        }
        var title: String? = null
        var subTitle: String? = null
        var description: String? = null
        var categories: StringBuilder? = null
        forEachChild { child ->
            when (child) {
                "title" -> readText().trim().take(MAX_TITLE_CHARS).let { if (title == null && it.isNotEmpty()) title = it }
                "sub-title" -> readText().trim().take(MAX_TITLE_CHARS).let { if (subTitle == null && it.isNotEmpty()) subTitle = it }
                "desc" -> readText().trim().take(MAX_DESCRIPTION_CHARS).let { if (description == null && it.isNotEmpty()) description = it }
                "category" -> readText().trim().take(MAX_TITLE_CHARS).let {
                    if (it.isNotEmpty()) {
                        val joined = categories ?: StringBuilder().also { b -> categories = b }
                        if (joined.isNotEmpty()) joined.append(CATEGORY_SEPARATOR)
                        joined.append(it)
                    }
                }
                else -> skipElement()
            }
        }
        val kept = title ?: return null
        hashedProgrammes++
        return XmlTvProgramme(ids.programmeKey(channel, start, stop, kept), channel, start, stop, kept, subTitle, description, categories?.toString())
    }

    /** Calls [onChild] at each child's start tag; [onChild] must consume the child to its end tag. */
    private inline fun forEachChild(onChild: (String) -> Unit) {
        val depth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> onChild(parser.name)
                XmlPullParser.END_TAG -> if (parser.depth == depth) return
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("document ended inside an element", parser, null)
            }
        }
    }

    /** The text of the current element; child elements inside it are skipped. */
    private fun readText(): String {
        val depth = parser.depth
        var text: StringBuilder? = null
        var first: String? = null
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT -> {
                    val part = parser.text
                    when {
                        first == null -> first = part
                        text == null -> text = StringBuilder(first).append(part)
                        else -> text.append(part)
                    }
                }
                XmlPullParser.START_TAG -> skipElement()
                XmlPullParser.END_TAG -> if (parser.depth == depth) return text?.toString() ?: first.orEmpty()
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("document ended inside an element", parser, null)
            }
        }
    }

    /** Skips from the current start tag past its end tag. */
    private fun skipElement() {
        var level = 1
        while (level > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> level++
                XmlPullParser.END_TAG -> level--
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("document ended inside an element", parser, null)
            }
        }
    }

    companion object {
        const val MAX_TITLE_CHARS: Int = 512
        const val MAX_DESCRIPTION_CHARS: Int = 4_096
        const val CATEGORY_SEPARATOR: Char = '\u001F'
        private val UTF8_BOM = "efbbbf".decodeHex()

        /** The platform parser on Android (kXML2 in JVM tests); DOCTYPE processing is off by default. */
        fun newParser(): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
    }
}
