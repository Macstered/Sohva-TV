package com.sohva.tv.core.model.source

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * One source's addresses and credentials as one string for the secret store, which encrypts it
 * under the source's id (plan/04 §15.2). `v1&m=…&x=…&b=…&u=…&p=…`, values URL-encoded, absent
 * fields left out; unknown fields are ignored so a later version can add some.
 */
object SourceSecretsCodec {
    private const val VERSION = "v1"

    fun encode(secrets: SourceSecrets): String = buildString {
        append(VERSION)
        field("m", secrets.m3uUrl)
        field("x", secrets.xmlTvUrl)
        field("b", secrets.xtreamBaseUrl)
        field("u", secrets.xtreamUsername)
        field("p", secrets.xtreamPassword)
    }

    /** Null when [text] is not this format; the caller reports the store as damaged (SRC-FR-109). */
    fun decode(text: String): SourceSecrets? {
        val parts = text.split('&')
        if (parts.firstOrNull() != VERSION) return null
        val values = HashMap<String, String>()
        for (part in parts.drop(1)) {
            val eq = part.indexOf('=')
            if (eq <= 0) return null
            val value = try {
                URLDecoder.decode(part.substring(eq + 1), "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }
            values[part.substring(0, eq)] = value
        }
        return SourceSecrets(values["m"], values["x"], values["b"], values["u"], values["p"])
    }

    private fun StringBuilder.field(name: String, value: String?) {
        if (value == null) return
        append('&').append(name).append('=').append(URLEncoder.encode(value, "UTF-8"))
    }
}
