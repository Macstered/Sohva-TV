package com.sohva.tv.core.model.diagnostics

import java.net.URI

/**
 * Removes addresses and secrets from text before it is logged, saved or shown (spec 73
 * SEC-FR-17..19). Runs on every diagnostics line, every non-localised error on screen and every
 * transport failure detail.
 *
 * Beyond beta 23 it also catches `key`, `auth`, `authorization` (also as a header),
 * `access_token`, `refresh_token`, `code`, and the user and password segments of Xtream paths
 * written without a scheme (SEC-FR-19).
 */
object Redactor {
    const val REDACTED: String = "<redacted>"
    const val REDACTED_URL: String = "<redacted-url>"

    private val url = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,)]}"

    // Xtream stream paths: /live|movie|series|timeshift/<user>/<password>/...
    private val xtreamPath = Regex("""(?i)(/(?:live|movie|series|timeshift)/)[^/\s]+/[^/\s]+/""")

    // "Authorization: Bearer abc" and "authorization=Basic abc".
    private val authHeader = Regex("""(?i)\b(authorization)(\s*[:=]\s*)(?:(?:bearer|basic)\s+)?[^\s&,;]+""")

    // Longer names first so "api_key" is not read as "key" and "username" not as "user".
    private val secretPair = Regex(
        """(?i)(?<![A-Za-z0-9_])(api[-_]?key|access_token|refresh_token|password|username|token|pass|user|key|auth|code)=([^&\s]+)""",
    )

    /** `null` for null or blank input, so the caller chooses its localised fallback. */
    fun redact(message: String?): String? {
        if (message.isNullOrBlank()) return null
        var text = url.replace(message) { match -> redactUrl(match.value) }
        text = xtreamPath.replace(text) { "${it.groupValues[1]}$REDACTED/$REDACTED/" }
        text = authHeader.replace(text) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
        text = secretPair.replace(text) { "${it.groupValues[1]}=$REDACTED" }
        return text
    }

    private fun redactUrl(raw: String): String {
        val core = raw.trimEnd { it in TRAILING_PUNCTUATION }
        val trailing = raw.substring(core.length)
        val replacement = try {
            val uri = URI(core)
            val host = uri.host
            if (host.isNullOrEmpty()) {
                REDACTED_URL
            } else {
                val port = if (uri.port >= 0) ":${uri.port}" else ""
                "${uri.scheme.lowercase()}://$host$port/$REDACTED"
            }
        } catch (_: Exception) {
            REDACTED_URL
        }
        return replacement + trailing
    }
}
