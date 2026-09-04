package com.streammate.tv.core.security

import com.streammate.tv.core.R
import com.streammate.tv.core.error.LocalizedException
import android.content.Context
import com.streammate.tv.core.model.IptvConfiguration
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import java.security.MessageDigest

data class MetadataSettings(
    val tmdbEnabled: Boolean = false,
    val tmdbReadAccessToken: String = "",
    val tvmazeEnabled: Boolean = false,
) {
    override fun toString(): String =
        "MetadataSettings(tmdbEnabled=$tmdbEnabled, tmdbReadAccessToken=<redacted>, " +
            "tvmazeEnabled=$tvmazeEnabled)"
}

data class SportsApiSettings(
    val apiKey: String = "",
) {
    val configured: Boolean get() = apiKey.isNotBlank()

    override fun toString(): String = "SportsApiSettings(apiKey=<redacted>)"
}

class SecretSettingsStore(
    context: Context,
    private val cipher: SecretCipher,
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val legacyPreferences = context.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun loadSources(): List<IptvSourceConfiguration> {
        val encryptedSources = preferences.getString(KEY_SOURCES, null)
        if (encryptedSources != null) {
            return runCatching {
                IptvSourceConfigurationCodec.decode(cipher.decrypt(encryptedSources))
            }.getOrDefault(emptyList())
        }
        val legacy = loadLegacy() ?: return emptyList()
        val migrated = listOf(IptvSourceConfiguration.fromLegacy(legacy))
        saveSources(migrated)
        legacyPreferences.edit().clear().commit()
        return migrated
    }

    @Synchronized
    fun saveSources(sources: List<IptvSourceConfiguration>) {
        val encoded = IptvSourceConfigurationCodec.encode(sources)
        check(
            preferences.edit()
                .putString(KEY_SOURCES, cipher.encrypt(encoded))
                .commit(),
        ) { "Could not persist encrypted IPTV sources" }
    }

    @Synchronized
    fun upsertSource(source: IptvSourceConfiguration) {
        val existing = loadSources()
        val updated = if (existing.any { it.id == source.id }) {
            existing.map { if (it.id == source.id) source else it }
        } else {
            existing + source
        }
        saveSources(updated)
    }

    @Synchronized
    fun deleteSource(sourceId: String) {
        saveSources(loadSources().filterNot { it.id == sourceId })
    }

    fun load(): IptvConfiguration? = loadSources()
        .asSequence()
        .filter { it.enabled && it.type == IptvSourceType.M3U }
        .sortedByDescending { it.priority }
        .mapNotNull(IptvSourceConfiguration::asM3uConfiguration)
        .firstOrNull()

    fun save(configuration: IptvConfiguration) {
        val source = loadSources().firstOrNull { it.type == IptvSourceType.M3U }
            ?.copy(m3uUrl = configuration.m3uUrl, xmlTvUrl = configuration.xmlTvUrl)
            ?: IptvSourceConfiguration.fromLegacy(configuration)
        upsertSource(source)
    }

    @Synchronized
    fun loadMetadataSettings(): MetadataSettings {
        val token = preferences.getString(KEY_TMDB_TOKEN, null)
            ?.let { encrypted -> runCatching { cipher.decrypt(encrypted) }.getOrNull() }
            .orEmpty()
        return MetadataSettings(
            tmdbEnabled = preferences.getBoolean(KEY_TMDB_ENABLED, false) && token.isNotBlank(),
            tmdbReadAccessToken = token,
            tvmazeEnabled = preferences.getBoolean(KEY_TVMAZE_ENABLED, false),
        )
    }

    @Synchronized
    fun saveMetadataSettings(settings: MetadataSettings) {
        val token = settings.tmdbReadAccessToken.trim()
        if (token.length > MAX_METADATA_TOKEN_LENGTH || '\n' in token || '\r' in token) {
            throw LocalizedException(R.string.error_tmdb_key_invalid)
        }
        if (settings.tmdbEnabled && token.isBlank()) {
            throw LocalizedException(R.string.error_tmdb_key_required)
        }
        val editor = preferences.edit()
            .putBoolean(KEY_TMDB_ENABLED, settings.tmdbEnabled)
            .putBoolean(KEY_TVMAZE_ENABLED, settings.tvmazeEnabled)
        if (token.isBlank()) {
            editor.remove(KEY_TMDB_TOKEN)
        } else {
            editor.putString(KEY_TMDB_TOKEN, cipher.encrypt(token))
        }
        if (!editor.commit()) throw LocalizedException(R.string.error_metadata_settings_save)
    }

    @Synchronized
    fun loadSportsApiSettings(): SportsApiSettings = SportsApiSettings(
        apiKey = preferences.getString(KEY_SPORTS_API_KEY, null)
            ?.let { encrypted -> runCatching { cipher.decrypt(encrypted) }.getOrNull() }
            .orEmpty(),
    )

    @Synchronized
    fun saveSportsApiSettings(settings: SportsApiSettings) {
        val apiKey = settings.apiKey.trim()
        if (apiKey.length > MAX_SPORTS_API_KEY_LENGTH || '\n' in apiKey || '\r' in apiKey) {
            throw LocalizedException(R.string.error_api_sports_key_invalid)
        }
        val editor = preferences.edit()
        if (apiKey.isBlank()) {
            editor.remove(KEY_SPORTS_API_KEY)
        } else {
            editor.putString(KEY_SPORTS_API_KEY, cipher.encrypt(apiKey))
        }
        if (!editor.commit()) throw LocalizedException(R.string.error_api_sports_settings_save)
    }

    @Synchronized
    fun saveParentalPin(pin: String) {
        if (!pin.matches(PARENTAL_PIN_PATTERN)) {
            throw LocalizedException(R.string.error_pin_format)
        }
        check(
            preferences.edit()
                .putString(KEY_PARENTAL_PIN, cipher.encrypt(pin))
                .commit(),
        ) { "Could not persist parental PIN" }
    }

    @Synchronized
    fun verifyParentalPin(candidate: String): Boolean {
        val encrypted = preferences.getString(KEY_PARENTAL_PIN, null) ?: return false
        val stored = runCatching { cipher.decrypt(encrypted) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8),
        )
    }

    @Synchronized
    fun parentalPinForEncryptedBackup(): String? {
        val encrypted = preferences.getString(KEY_PARENTAL_PIN, null) ?: return null
        return runCatching { cipher.decrypt(encrypted) }.getOrNull()
    }

    @Synchronized
    fun hasParentalPin(): Boolean = preferences.contains(KEY_PARENTAL_PIN)

    @Synchronized
    fun clearParentalPin() {
        check(preferences.edit().remove(KEY_PARENTAL_PIN).commit()) {
            "Could not clear parental PIN"
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
        legacyPreferences.edit().clear().commit()
    }

    private fun loadLegacy(): IptvConfiguration? {
        val encryptedM3u = legacyPreferences.getString(LEGACY_KEY_M3U, null) ?: return null
        val encryptedXmlTv = legacyPreferences.getString(LEGACY_KEY_XMLTV, null) ?: return null
        return runCatching {
            IptvConfiguration(
                m3uUrl = cipher.decrypt(encryptedM3u),
                xmlTvUrl = cipher.decrypt(encryptedXmlTv),
            )
        }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "streammate_secure_sources"
        const val KEY_SOURCES = "sources_v1"
        const val KEY_PARENTAL_PIN = "parental_pin_v1"
        const val KEY_TMDB_ENABLED = "metadata_tmdb_enabled_v1"
        const val KEY_TMDB_TOKEN = "metadata_tmdb_token_v1"
        const val KEY_TVMAZE_ENABLED = "metadata_tvmaze_enabled_v1"
        const val KEY_SPORTS_API_KEY = "sports_api_key_v1"
        const val LEGACY_FILE_NAME = "sportmate_secure_settings"
        const val LEGACY_KEY_M3U = "m3u"
        const val LEGACY_KEY_XMLTV = "xmltv"
        val PARENTAL_PIN_PATTERN = Regex("\\d{4,8}")
        const val MAX_METADATA_TOKEN_LENGTH = 2_048
        const val MAX_SPORTS_API_KEY_LENGTH = 512
    }
}
