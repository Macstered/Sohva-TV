package com.sohva.tv.core.model.source

/** M3U playlist (with an optional XMLTV guide) or an Xtream Codes account (spec 10 SRC-FR-01). */
enum class SourceType { M3U, XTREAM }

/** What a source imports (SRC-FR-04). Stored by name. */
enum class ImportScope {
    LIVE_TV,
    VOD,
    BOTH,
    ;

    val includesLive: Boolean get() = this != VOD
    val includesVod: Boolean get() = this != LIVE_TV
}

/** The three kinds of import; [id] is the stored value. */
enum class RefreshKind(val id: String) {
    PLAYLIST("playlist"),
    EPG("epg"),
    CATALOGUE("catalogue"),
    ;

    companion object {
        fun fromId(id: String): RefreshKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A source's non-secret fields: the only copy lives in the database table `source` (plan/04
 * §15.2). Addresses and credentials are in [SourceSecrets], in the secret store.
 */
data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val connectionLimit: Int = 1,
    val priority: Int = 0,
    val importScope: ImportScope = ImportScope.BOTH,
    val epgOffsetMinutes: Int = 0,
)

/**
 * A source's addresses and credentials. Never logged: [toString] says only which fields are set
 * (spec 73 SEC-FR-20).
 */
data class SourceSecrets(
    val m3uUrl: String? = null,
    val xmlTvUrl: String? = null,
    val xtreamBaseUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
) {
    override fun toString(): String = "SourceSecrets(credentials=<redacted>)"
}

/** A source with its secrets, as the source page and the importers need it. */
data class SourceConfig(val source: Source, val secrets: SourceSecrets) {
    override fun toString(): String = "SourceConfig(${source.id}, ${source.type}, credentials=<redacted>)"
}
