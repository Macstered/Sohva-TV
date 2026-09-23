package com.streammate.tv.iptv.xmltv

import android.util.Xml
import java.io.InputStream
import java.security.MessageDigest
import org.xmlpull.v1.XmlPullParser

sealed interface XmlTvRecord {
    data class Channel(
        val id: String,
        val displayName: String?,
        val iconUrl: String?,
    ) : XmlTvRecord

    data class Programme(
        val id: String,
        val channelId: String,
        val startEpochMillis: Long,
        val stopEpochMillis: Long,
        val title: String,
        val subtitle: String?,
        val description: String?,
        val categories: List<String>,
    ) : XmlTvRecord
}

/**
 * [newParser] makes the pull parser. On a device it is Android's own KXml,
 * which is also what the bundled kxml2 jar this used to name directly always
 * resolved to there: the system's copy of the same class is loaded first. The
 * bundled copy could not be shrunk away, since its XmlPullParser clashes with
 * the platform's, so the JVM tests now bring kxml2 themselves.
 */
class XmlTvParser(private val newParser: () -> XmlPullParser = ::platformPullParser) {
    fun records(input: InputStream): Sequence<XmlTvRecord> = sequence {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Let the XML parser consume the byte-order mark and detect the
            // declared encoding. A UTF-8 Reader exposes the BOM as text before
            // <?xml, which KXml rejects as "PI must not start with xml".
            setInput(input, null)
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> parseChannel(parser)?.let { yield(it) }
                    "programme" -> parseProgramme(parser)?.let { yield(it) }
                }
            }
            parser.next()
        }
    }

    private fun parseChannel(parser: XmlPullParser): XmlTvRecord.Channel? {
        val id = parser.getAttributeValue(null, "id")?.trim()?.takeIf(String::isNotEmpty)
            ?: return null.also { skipElement(parser) }
        val startDepth = parser.depth
        var displayName: String? = null
        var iconUrl: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "display-name" -> if (displayName == null) displayName = parser.nextText().trim()
                "icon" -> iconUrl = parser.getAttributeValue(null, "src")?.trim()
            }
        }
        return XmlTvRecord.Channel(id, displayName, iconUrl)
    }

    private fun parseProgramme(parser: XmlPullParser): XmlTvRecord.Programme? {
        val channelId = parser.getAttributeValue(null, "channel")?.trim()?.takeIf(String::isNotEmpty)
        val startValue = parser.getAttributeValue(null, "start")
        val stopValue = parser.getAttributeValue(null, "stop")
        if (channelId == null || startValue == null || stopValue == null) {
            skipElement(parser)
            return null
        }
        // A single unparseable timestamp used to propagate out of the whole
        // sequence, so GuideImportService discarded an entire EPG import over
        // one bad programme. Skip the record instead, matching how a programme
        // with missing attributes is already handled above.
        val start = runCatching { XmlTvTimestampParser.parse(startValue) }.getOrNull()
        val stop = runCatching { XmlTvTimestampParser.parse(stopValue) }.getOrNull()
        if (start == null || stop == null) {
            skipElement(parser)
            return null
        }
        val startDepth = parser.depth
        var title: String? = null
        var subtitle: String? = null
        var description: String? = null
        val categories = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> if (title == null) title = parser.nextText().trim()
                "sub-title" -> if (subtitle == null) subtitle = parser.nextText().trim()
                "desc" -> if (description == null) description = parser.nextText().trim()
                "category" -> parser.nextText().trim().takeIf(String::isNotEmpty)?.let(categories::add)
            }
        }
        val safeTitle = title?.takeIf(String::isNotEmpty) ?: return null
        return XmlTvRecord.Programme(
            // Sixty-four bits of the digest: a quarter of the primary-key index
            // of a full digest, and a collision inside one snapshot is a
            // once-in-the-lifetime-of-the-universe event that drops a listing.
            id = sha256("$channelId|${start.toEpochMilli()}|${stop.toEpochMilli()}|$safeTitle").take(PROGRAMME_ID_LENGTH),
            channelId = channelId,
            startEpochMillis = start.toEpochMilli(),
            stopEpochMillis = stop.toEpochMilli(),
            title = safeTitle,
            subtitle = subtitle?.takeIf(String::isNotEmpty),
            description = description?.takeIf(String::isNotEmpty),
            categories = categories,
        )
    }

    private fun skipElement(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        val startDepth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == startDepth) return
        }
    }

    // One digest per parse and a lookup table for the hex: a million
    // programmes used to mean a million digest instances and thirty-two
    // Formatter round trips each, which was most of the parser's time.
    private val digest: MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun sha256(value: String): String {
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        val encoded = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val unsigned = byte.toInt() and 0xff
            encoded[index * 2] = HEX_DIGITS[unsigned ushr 4]
            encoded[index * 2 + 1] = HEX_DIGITS[unsigned and 0x0f]
        }
        return encoded.concatToString()
    }

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"
        const val PROGRAMME_ID_LENGTH = 16
    }
}

/** As a bare KXmlParser starts: Xml.newPullParser also reads document type declarations. */
private fun platformPullParser(): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
}
