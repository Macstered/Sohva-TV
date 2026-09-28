package com.sohva.tv.core.net.phone

import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import java.util.Base64
import java.util.Locale

/** What the phone page sent (spec 11 PHONE-FR-30). Prints without addresses, credentials or keys. */
sealed interface PhoneSubmission {
    /** A source, already validated and normalised by the Settings rules (SRC-FR-20…23). */
    class NewSource(val config: SourceConfig) : PhoneSubmission {
        override fun toString(): String = "NewSource(${config.source.type})"

        override fun equals(other: Any?): Boolean = other is NewSource && other.config.secrets == config.secrets &&
            other.config.source.copy(id = "") == config.source.copy(id = "")

        override fun hashCode(): Int = config.secrets.hashCode() * 31 + config.source.copy(id = "").hashCode()
    }

    class Keys(val tmdbToken: String?, val apiSportsKey: String?) : PhoneSubmission {
        override fun toString(): String = "Keys(tmdb=${tmdbToken != null}, apiSports=${apiSportsKey != null})"

        override fun equals(other: Any?): Boolean = other is Keys && other.tmdbToken == tmdbToken && other.apiSportsKey == apiSportsKey

        override fun hashCode(): Int = (tmdbToken?.hashCode() ?: 0) * 31 + (apiSportsKey?.hashCode() ?: 0)
    }

    /** A Discover addon URL list (spec 50 ADDON-FR-45): the raw bytes, already checked by the mode. */
    class AddonList(val bytes: ByteArray) : PhoneSubmission {
        override fun toString(): String = "AddonList(${bytes.size} bytes)"
    }

    /**
     * A Trakt list's address or number (spec 02 HOME-FR-100), already checked by the mode; the
     * receiver writes the added list's name into [added] for the page's answer.
     */
    class TraktList(val text: String, val added: TraktListAdded = TraktListAdded()) : PhoneSubmission {
        override fun toString(): String = "TraktList"
    }

    /** A picture for one channel's logo (spec 21 CHAN-FR-42): the PNG bytes, 1…2,000,000 of them. */
    class Logo(val channelKey: String, val png: ByteArray) : PhoneSubmission {
        override fun toString(): String = "Logo(${png.size} bytes)"

        override fun equals(other: Any?): Boolean = other is Logo && other.channelKey == channelKey && other.png.contentEquals(png)

        override fun hashCode(): Int = channelKey.hashCode() * 31 + png.contentHashCode()
    }

    companion object {
        const val MAX_LOGO_BYTES: Int = 2_000_000
        private const val PNG_DATA = "data:image/png;base64,"

        /**
         * A form of the Sources page as a submission, or null when it is invalid: an unknown type,
         * no name, an address the address rule refuses, missing Xtream credentials, or keys with
         * neither key. Every other source field takes its default (in use, limit 1, TV and VOD,
         * no EPG correction).
         */
        fun parse(form: Map<String, String>): PhoneSubmission? {
            val tmdb = form["tmdb_token"]?.trim()?.ifEmpty { null }
            val sports = form["api_sports_key"]?.trim()?.ifEmpty { null }
            return when (form["type"]?.lowercase(Locale.ROOT)) {
                "logo" -> logo(form)
                "keys" -> if (tmdb == null && sports == null) null else Keys(tmdb, sports)
                "m3u" -> source(form, SourceType.M3U, SourceSecrets(m3uUrl = form["m3u_url"].orEmpty(), xmlTvUrl = form["xmltv_url"]))
                "xtream" -> source(
                    form,
                    SourceType.XTREAM,
                    SourceSecrets(
                        xtreamBaseUrl = form["xtream_url"].orEmpty(),
                        xtreamUsername = form["xtream_username"].orEmpty(),
                        xtreamPassword = form["xtream_password"].orEmpty(),
                    ),
                )
                else -> null
            }
        }

        private fun logo(form: Map<String, String>): PhoneSubmission? {
            val channel = form["channel"]?.takeIf { it.isNotBlank() } ?: return null
            val image = form["image"]?.takeIf { it.startsWith(PNG_DATA) } ?: return null
            val bytes = runCatching { Base64.getDecoder().decode(image.substring(PNG_DATA.length)) }.getOrNull() ?: return null
            return if (bytes.isEmpty() || bytes.size > MAX_LOGO_BYTES) null else Logo(channel, bytes)
        }

        private fun source(form: Map<String, String>, type: SourceType, secrets: SourceSecrets): PhoneSubmission? {
            val name = form["name"]?.trim().orEmpty()
            if (name.isEmpty()) return null
            val config = SourceConfig(Source(SourceRules.newId(type), name, type), secrets)
            return when (val result = SourceRules.validate(config)) {
                is SourceRules.Result.Valid -> NewSource(result.config)
                is SourceRules.Result.Invalid -> null
            }
        }
    }
}

/** The name of the list a [PhoneSubmission.TraktList] added (HOME-FR-100). */
class TraktListAdded {
    @Volatile var name: String? = null
}
