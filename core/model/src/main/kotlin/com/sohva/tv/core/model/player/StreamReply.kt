package com.sohva.tv.core.model.player

import java.util.Locale

/**
 * What a stream address sent back when the player could not read it as a stream (spec 30
 * PLAY-FR-94 detail): the kind of reply from its first bytes, the media type of its header, and
 * whether a redirect took it to another host. Never the content and never the address, so the line
 * is safe in the diagnostics file.
 */
data class StreamReply(val kind: Kind, val contentType: String?, val redirected: Boolean) {
    enum class Kind(val label: String) {
        EMPTY("empty"),
        PLAYLIST("playlist"),
        WEB_PAGE("web page"),
        JSON("json"),
        TEXT("text"),
        DATA("unrecognised data"),
    }

    /** An HLS playlist at an address that does not say so (no `.m3u8`). */
    val isPlaylist: Boolean get() = kind == Kind.PLAYLIST

    fun describe(): String =
        "reply: ${kind.label}, type ${contentType ?: "none"}, ${if (redirected) "redirected to another host" else "same host"}"

    companion object {
        /** Bytes looked at: enough for a byte-order mark, blank lines and a tag. */
        const val PEEK: Int = 256

        private val PLAYLIST_TAG = "#EXTM3U".toByteArray(Charsets.US_ASCII)
        private val MEDIA_TYPE = Regex("[a-z0-9][a-z0-9.+-]{0,40}/[a-z0-9][a-z0-9.+-]{0,60}")

        /** The kind of the first [length] bytes of [head]. */
        fun kindOf(head: ByteArray, length: Int): Kind {
            val end = length.coerceIn(0, head.size)
            var i = 0
            // A UTF-8 byte-order mark, then blank space.
            if (end >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) i = 3
            while (i < end && head[i].toInt().toChar().isWhitespace()) i++
            if (i >= end) return Kind.EMPTY
            if (startsWith(head, i, end, PLAYLIST_TAG)) return Kind.PLAYLIST
            return when (head[i].toInt().toChar()) {
                '<' -> Kind.WEB_PAGE
                '{', '[' -> Kind.JSON
                else -> if (isText(head, i, end)) Kind.TEXT else Kind.DATA
            }
        }

        /** The `type/subtype` of a Content-Type header, lower-cased; null when absent or unusual. */
        fun mediaType(header: String?): String? {
            val type = header?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return null
            return type.takeIf { MEDIA_TYPE.matches(it) }
        }

        private fun startsWith(head: ByteArray, from: Int, end: Int, tag: ByteArray): Boolean {
            if (end - from < tag.size) return false
            for (k in tag.indices) if (head[from + k] != tag[k]) return false
            return true
        }

        /** Printable ASCII, tabs and line breaks, or UTF-8 continuation bytes; no control bytes. */
        private fun isText(head: ByteArray, from: Int, end: Int): Boolean {
            for (k in from until end) {
                val b = head[k].toInt() and 0xFF
                val printable = b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D || b >= 0x80
                if (!printable) return false
            }
            return true
        }
    }
}
