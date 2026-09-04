package com.streammate.tv.iptv.xmltv

import java.io.InputStream
import java.security.MessageDigest
import org.kxml2.io.KXmlParser
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

class XmlTvParser {
    fun records(input: InputStream): Sequence<XmlTvRecord> = sequence {
        val parser = KXmlParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input.reader(Charsets.UTF_8))
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
            id = sha256("$channelId|${start.toEpochMilli()}|${stop.toEpochMilli()}|$safeTitle"),
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
