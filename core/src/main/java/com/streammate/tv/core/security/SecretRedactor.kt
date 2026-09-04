package com.streammate.tv.core.security

import java.net.URI

object SecretRedactor {
    private val urlPattern = Regex("""https?://[^\s\"'<>]+""", RegexOption.IGNORE_CASE)
    private val namedSecretPattern = Regex(
        """(?i)(api[-_]?key|token|password|username|user|pass)=([^&\s]+)""",
    )

    /**
     * Returns null when there is nothing left worth showing, so the caller
     * can fall back to its own localised text - this runs without a Context
     * and cannot resolve a string itself.
     */
    fun redact(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val urlsRemoved = urlPattern.replace(message) { match -> sanitizeUrl(match.value) }
        return namedSecretPattern.replace(urlsRemoved) { match -> "${match.groupValues[1]}=<redacted>" }
    }

    private fun sanitizeUrl(value: String): String = runCatching {
        val uri = URI(value.trimEnd('.', ',', ')', ']', '}'))
        val host = uri.host ?: return@runCatching "<redacted-url>"
        buildString {
            append(uri.scheme.lowercase())
            append("://")
            append(host)
            if (uri.port != -1) append(":${uri.port}")
            append("/<redacted>")
        }
    }.getOrDefault("<redacted-url>")
}
