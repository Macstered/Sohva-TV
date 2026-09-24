package com.sohva.tv.feature.settings

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import java.util.Locale

/**
 * The source page's edits (spec 10 SRC-FR-18): they live only here until an action saves them.
 * Fields hold what the viewer typed; [toConfig] hands them to the store, which validates and
 * normalises (SRC-FR-20…23).
 */
@Immutable
data class SourceDraft(
    val id: String,
    val type: SourceType,
    /** Never saved yet: no Remove button, and saving starts the first sync. */
    val isNew: Boolean,
    val name: String,
    val m3uUrl: String = "",
    val xmlTvUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
    val connectionLimit: Int = 1,
    val scope: ImportScope = ImportScope.BOTH,
    val epgOffsetMinutes: Int = 0,
    val priority: Int = 0,
) {
    /** The XMLTV field shows only with live TV; its value is kept and still checked (SRC-FR-14). */
    val showsGuideAddress: Boolean get() = type == SourceType.M3U && scope.includesLive

    /** SRC-FR-17: the guide can refresh with an XMLTV address, an Xtream account or a `get.php` address. */
    val canRefreshGuide: Boolean
        get() = scope.includesLive &&
            (type == SourceType.XTREAM || xmlTvUrl.isNotBlank() || m3uUrl.lowercase(Locale.ROOT).contains("get.php"))

    fun toConfig(): SourceConfig = SourceConfig(
        Source(id, name, type, enabled, connectionLimit, priority, scope, epgOffsetMinutes),
        when (type) {
            SourceType.M3U -> SourceSecrets(m3uUrl = m3uUrl, xmlTvUrl = xmlTvUrl.ifBlank { null })
            SourceType.XTREAM -> SourceSecrets(xtreamBaseUrl = server, xtreamUsername = username, xtreamPassword = password)
        },
    )

    /** What an import depends on; changing any of these makes Save securely start a sync. */
    fun importInputs(): List<Any> = listOf(m3uUrl, xmlTvUrl, server, username, password, scope, enabled)

    /** Never the addresses or credentials (spec 73). */
    override fun toString(): String = "SourceDraft($id, $type, new=$isNew, credentials=<redacted>)"

    companion object {
        /** A new page's default name: `IPTV n` or `Xtream n`, untranslated as in beta 23 (SRC-FR-11). */
        fun new(type: SourceType, savedOfType: Int): SourceDraft = SourceDraft(
            id = SourceRules.newId(type),
            type = type,
            isNew = true,
            name = if (type == SourceType.M3U) "IPTV ${savedOfType + 1}" else "Xtream ${savedOfType + 1}",
        )

        fun of(config: SourceConfig): SourceDraft {
            val s = config.source
            val secrets = config.secrets
            return SourceDraft(
                id = s.id, type = s.type, isNew = false, name = s.name,
                m3uUrl = secrets.m3uUrl.orEmpty(), xmlTvUrl = secrets.xmlTvUrl.orEmpty(), server = secrets.xtreamBaseUrl.orEmpty(),
                username = secrets.xtreamUsername.orEmpty(), password = secrets.xtreamPassword.orEmpty(), enabled = s.enabled,
                connectionLimit = s.connectionLimit, scope = s.importScope, epgOffsetMinutes = s.epgOffsetMinutes, priority = s.priority,
            )
        }
    }
}
