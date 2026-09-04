package com.streammate.tv.core.network

import com.streammate.tv.core.R
import com.streammate.tv.core.error.LocalizedException
import java.net.URI

object IptvSourceUrlPolicy {
    /**
     * @param label what the address is for, named in the failure message.
     *   Pass a [com.streammate.tv.core.error.ResourceArgument] for anything
     *   that needs translating; a plain string is right for a format name such
     *   as "M3U" that reads the same in every language.
     */
    fun normalize(value: String, label: Any): String {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme !in SUPPORTED_SCHEMES || uri?.host.isNullOrBlank()) {
            throw LocalizedException(R.string.error_source_url_invalid, label)
        }
        return trimmed
    }

    private val SUPPORTED_SCHEMES = setOf("http", "https")
}
