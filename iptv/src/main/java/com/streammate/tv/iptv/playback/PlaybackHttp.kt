package com.streammate.tv.iptv.playback

import com.streammate.tv.core.security.SecretRedactor

/**
 * How the player introduces itself and explains a failure. Both were found
 * wanting by a tester whose 1,700-channel list played everywhere but here:
 * the app sent OkHttp's own user agent, which some panels drop, and the
 * screen showed a bare error code.
 */
object PlaybackHttp {
    /** The app's own name for a provider: sent to a stream when the playlist names no agent, and to every playlist and guide address. */
    fun userAgent(versionName: String, androidRelease: String): String =
        "Sohva TV/$versionName (Android TV $androidRelease)"

    /** [headers] with [userAgent] added unless the playlist already set one, under any casing. */
    fun withDefaultUserAgent(headers: Map<String, String>, userAgent: String): Map<String, String> =
        if (headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) headers
        else headers + ("User-Agent" to userAgent)

    /**
     * The code name plus the innermost cause that has something to say, with
     * addresses and credentials removed, so a report says "Connection reset"
     * or "Response code: 403" rather than only the code.
     */
    fun failureDetail(errorCodeName: String, error: Throwable?): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val told = causes.lastOrNull { !it.message.isNullOrBlank() && it.message != errorCodeName } ?: return errorCodeName
        val message = SecretRedactor.redact(told.message)?.take(MAX_CAUSE_CHARS) ?: return errorCodeName
        return "$errorCodeName · ${told::class.java.simpleName}: $message"
    }

    private const val MAX_CAUSE_CHARS = 140
}
