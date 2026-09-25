package com.sohva.tv.core.data.metadata

import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The metadata settings as every lookup reads them (spec 41 §4.1). */
data class MetadataConfig(
    val tmdbSwitch: Boolean,
    val credential: String?,
    val tvmazeEnabled: Boolean,
    val language: String,
) {
    /** "TMDB enabled" = the stored switch and a credential (META-FR-01). */
    val tmdbEnabled: Boolean get() = tmdbSwitch && !credential.isNullOrBlank()

    /** Anything to look up with (META-FR-13): nothing is requested otherwise. */
    val enabled: Boolean get() = tmdbEnabled || tvmazeEnabled

    override fun toString(): String = "MetadataConfig(tmdb=$tmdbEnabled, tvmaze=$tvmazeEnabled, language=$language)"

    companion object {
        /** The 22 TMDB languages, in picker order (META-FR-10). */
        val LANGUAGES: List<String> = listOf(
            "en-US", "fi-FI", "sv-SE", "nb-NO", "da-DK", "de-DE", "nl-NL", "fr-FR", "es-ES", "pt-PT", "pt-BR", "it-IT",
            "pl-PL", "cs-CZ", "hu-HU", "ru-RU", "tr-TR", "el-GR", "ar-SA", "ja-JP", "ko-KR", "zh-CN",
        )

        /** The stored language if supported, else Finnish for a Finnish interface and US English otherwise. */
        fun language(stored: String?, interfaceLanguage: String): String =
            stored?.takeIf { it in LANGUAGES } ?: if (interfaceLanguage.startsWith("fi")) "fi-FI" else "en-US"
    }
}

/** Where the metadata language is kept (DataStore `metadata_language`, device-wide). */
interface MetadataLanguageStore {
    suspend fun metadataLanguage(): String?

    suspend fun setMetadataLanguage(tag: String)
}

/**
 * The metadata settings (spec 41 §4.1): the TMDB switch and credential and the TVmaze switch in the
 * encrypted settings under spec 70's names, the language in the preferences. The credential is
 * decrypted once per change, off the main thread, and screens observe [config] instead of reading
 * it per call (spec 41 §9.3, VOD lesson 12). Both switches default off: a fresh install makes no
 * metadata request.
 */
class MetadataSettings(
    private val secrets: SecretValues,
    private val languages: MetadataLanguageStore,
    private val interfaceLanguage: () -> String,
    private val io: CoroutineDispatcher,
) {
    private val _config = MutableStateFlow<MetadataConfig?>(null)

    /** Null until first read; lookups call [current], which reads it once. */
    val config: StateFlow<MetadataConfig?> = _config.asStateFlow()

    suspend fun current(): MetadataConfig = _config.value ?: reload()

    suspend fun reload(): MetadataConfig = withContext(io) {
        val tmdb = (secrets.read(ServiceKeys.TMDB_ENABLED) as? Outcome.Ok)?.value == "true"
        val credential = (secrets.read(ServiceKeys.TMDB_TOKEN) as? Outcome.Ok)?.value
        val tvmaze = (secrets.read(TVMAZE_ENABLED) as? Outcome.Ok)?.value == "true"
        MetadataConfig(tmdb, credential, tvmaze, MetadataConfig.language(languages.metadataLanguage(), interfaceLanguage()))
            .also { _config.value = it }
    }

    /**
     * "Save key" (META-FR-04): the trimmed credential (blank removes it), both switches as they
     * are. Validation: at most 2,048 characters, one line; the TMDB switch on needs a credential.
     * Returns whether the credential changed, which clears negative cache rows (META-FR-05).
     */
    suspend fun save(credential: String, tmdbOn: Boolean, tvmazeOn: Boolean): Outcome<Boolean> = withContext(io) {
        val key = credential.trim()
        if (key.length > MAX_KEY || key.any { it == '\n' || it == '\r' }) return@withContext Outcome.Failed(AppError.TmdbKeyInvalid)
        if (tmdbOn && key.isEmpty()) return@withContext Outcome.Failed(AppError.TmdbKeyRequired)
        val before = current()
        val writes = listOf(
            secrets.write(ServiceKeys.TMDB_TOKEN, key.ifEmpty { null }),
            secrets.write(ServiceKeys.TMDB_ENABLED, tmdbOn.toString()),
            secrets.write(TVMAZE_ENABLED, tvmazeOn.toString()),
        )
        if (writes.any { it is Outcome.Failed }) return@withContext Outcome.Failed(AppError.MetadataSaveFailed)
        reload()
        Outcome.Ok(before.credential.orEmpty().trim() != key)
    }

    /** The TMDB switch on its own (META-FR-02): on needs a saved credential. */
    suspend fun setTmdb(on: Boolean): Outcome<Unit> = withContext(io) {
        if (on && current().credential.isNullOrBlank()) return@withContext Outcome.Failed(AppError.TmdbKeyRequired)
        val saved = secrets.write(ServiceKeys.TMDB_ENABLED, on.toString())
        reload()
        if (saved is Outcome.Failed) Outcome.Failed(AppError.MetadataSaveFailed) else Outcome.Ok(Unit)
    }

    suspend fun setTvmaze(on: Boolean): Outcome<Unit> = withContext(io) {
        val saved = secrets.write(TVMAZE_ENABLED, on.toString())
        reload()
        if (saved is Outcome.Failed) Outcome.Failed(AppError.MetadataSaveFailed) else Outcome.Ok(Unit)
    }

    /** A new language; true when it changed (the caller then resets and restarts enrichment, META-FR-79). */
    suspend fun setLanguage(tag: String): Boolean = withContext(io) {
        if (tag !in MetadataConfig.LANGUAGES || tag == current().language) return@withContext false
        languages.setMetadataLanguage(tag)
        reload()
        true
    }

    companion object {
        const val TVMAZE_ENABLED: String = "metadata_tvmaze_enabled_v1"
        private const val MAX_KEY = 2_048
    }
}
