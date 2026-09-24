package com.sohva.tv.core.net.m3u

/** What an M3U entry is, by beta 23's substring rule (spec 10 SRC-FR-57). */
enum class M3uKind { LIVE, MOVIE, SERIES }

/**
 * One playlist entry. [name] is null when the entry names itself nowhere; the importer then shows
 * the translated "Channel n" while [id] keeps the compatibility rule's "Kanava n" (plan/04 §6).
 */
data class M3uEntry(
    /** 0-based position in the playlist: the playlist order. */
    val index: Int,
    val id: String,
    val name: String?,
    val normalizedName: String,
    val tvgId: String?,
    val logoUrl: String?,
    val group: String?,
    val channelNumber: Int?,
    val catchupType: String?,
    val catchupDays: Int?,
    val catchupSource: String?,
    val durationSeconds: Int?,
    val kind: M3uKind,
    val streamUrl: String,
    val userAgent: String?,
    val referrer: String?,
) {
    /** Never the stream address (SRC-FR-62): it often carries the viewer's credentials. */
    override fun toString(): String = "M3uEntry(index=$index, id=$id, kind=$kind, streamUrl=<redacted>)"
}
