package com.streammate.tv.addons

import android.content.Context
import com.sohva.tv.addons.AddonClient
import com.sohva.tv.addons.AddonBrowseRepository
import com.sohva.tv.addons.EncryptedFileAddonCache
import java.io.File
import com.sohva.tv.addons.AddonManagementAccess
import com.sohva.tv.addons.AddonManager
import com.sohva.tv.addons.AddonSecretCipher
import com.sohva.tv.addons.storage.AddonDatabase
import com.sohva.tv.addons.storage.EncryptedAddonStore
import com.sohva.tv.addons.storage.RoomAddonPersistence
import com.streammate.tv.app.StreamMateContainer
import com.streammate.tv.app.activeRestriction
import com.streammate.tv.core.security.AesGcmSecretCipher
import com.streammate.tv.core.security.AndroidKeystoreKeyProvider
import com.streammate.tv.core.security.EnvelopeSecretCipher
import com.streammate.tv.core.security.WrappedKeyStore
import kotlinx.coroutines.flow.first

/** Application-lifetime connection, created only on explicit Discover entry. */
internal class AddonHost(context: Context, container: StreamMateContainer) {
    private val database = AddonDatabase.open(context)
    val client = AddonClient()
    val preferences = container.preferencesRepository.preferences
    private val uiPreferences = context.getSharedPreferences("sohva_addon_ui", Context.MODE_PRIVATE)
    private val allSubtitleLanguages = kotlinx.coroutines.flow.MutableStateFlow(uiPreferences.getBoolean("all_subtitle_languages", false))
    val showAllSubtitleLanguages: kotlinx.coroutines.flow.StateFlow<Boolean> = allSubtitleLanguages
    suspend fun setShowAllSubtitleLanguages(value: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        check(uiPreferences.edit().putBoolean("all_subtitle_languages", value).commit())
        allSubtitleLanguages.value = value
    }
    private val keyProvider = AndroidKeystoreKeyProvider("sohva.addons.v1")
    private val keyPreferences = context.getSharedPreferences("sohva_addon_secret_envelope", Context.MODE_PRIVATE)
    private val addonCipher = EnvelopeSecretCipher(AesGcmSecretCipher(keyProvider::getOrCreate), object : WrappedKeyStore {
        override fun load(): String? = keyPreferences.getString("data_key", null)
        override fun save(wrapped: String) {
            // Must be durable BEFORE the database/cache ciphertext is committed. apply()
            // can lose a newly generated key when the process exits immediately afterward.
            check(keyPreferences.edit().putString("data_key", wrapped).commit())
        }
    })
    private val cipher = object : AddonSecretCipher {
        override fun encrypt(plaintext: String) = addonCipher.encrypt(plaintext)
        override fun decrypt(ciphertext: String) = addonCipher.decrypt(ciphertext)
    }
    val store = EncryptedAddonStore(RoomAddonPersistence(database), cipher)
    val access = AddonManagementAccess { profileId ->
        val preferences = container.preferencesRepository.preferences.first()
        container.runtimePolicy.addonsAllowed && preferences.activeProfileId == profileId &&
            !preferences.activeRestriction.restricted
    }
    val manager = AddonManager(client, store, access)
    val catalogOrder = com.sohva.tv.addons.AddonCatalogOrderRepository(store, object : com.sohva.tv.addons.AddonCatalogOrderPersistence {
        override suspend fun read(profileKey: String): List<String> = try {
            val array = org.json.JSONArray(uiPreferences.getString("catalog_order_$profileKey", "[]"))
            (0 until array.length()).map { array.getString(it) }
        } catch (_: Exception) { emptyList() }
        override suspend fun write(profileKey: String, catalogKeys: List<String>) {
            check(uiPreferences.edit().putString("catalog_order_$profileKey", org.json.JSONArray(catalogKeys).toString()).commit())
        }
    }, access)
    val catalogVisibility = com.sohva.tv.addons.AddonCatalogVisibilityRepository(store, object : com.sohva.tv.addons.AddonCatalogVisibilityPersistence {
        override suspend fun read(profileKey: String): Set<String> {
            val array = org.json.JSONArray(uiPreferences.getString("catalog_hidden_$profileKey", "[]"))
            return (0 until array.length()).map { array.getString(it) }.toSet()
        }
        override suspend fun write(profileKey: String, hidden: Set<String>) {
            check(uiPreferences.edit().putString("catalog_hidden_$profileKey", org.json.JSONArray(hidden.sorted()).toString()).commit())
        }
    }, access)
    val batchImport = com.sohva.tv.addons.AddonBatchImport(client, store, access)
    val stremioImport = com.sohva.tv.addons.StremioCopyImport(access)
    val sources = com.sohva.tv.addons.AddonSourceRepository(client, store, access)
    val cache = EncryptedFileAddonCache(File(context.noBackupFilesDir, "addon-responses"), cipher, maxBytes = 32L * 1024 * 1024, maxEntries = 128)
    val browser = AddonBrowseRepository(client, store, cache, access)
    val search = com.sohva.tv.addons.AddonSearchRepository(access, { profile, entry, extras ->
        browser.catalog(profile, entry.installation.installationId, entry.catalog.type, entry.catalog.id, extras, itemLimit = 100)
    })
    private val progressDatabase = com.sohva.tv.addons.storage.AddonProgressDatabase.open(context)
    val progress = com.sohva.tv.addons.AddonProgressRepository(
        com.sohva.tv.addons.storage.RoomAddonProgressPersistence(progressDatabase.progress()), cipher, access)
    private val libraryDatabase = com.sohva.tv.addons.storage.AddonLibraryDatabase.open(context)
    val library = com.sohva.tv.addons.AddonLibraryRepository(
        com.sohva.tv.addons.storage.RoomAddonLibraryPersistence(libraryDatabase.library()), cipher, access)
    val playbackAccess = com.sohva.tv.addons.AddonPlaybackAccess(client, store, access)
    // Survives a screen disposal long enough to commit the last progress snapshot.
    val persistenceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    var activePlayback: AddonPlayback? = null
    var pendingProgressWrite: kotlinx.coroutines.Job? = null

    companion object {
        @Volatile private var instance: AddonHost? = null
        fun get(context: Context, container: StreamMateContainer): AddonHost = instance ?: synchronized(this) {
            instance ?: AddonHost(context.applicationContext, container).also { instance = it }
        }
    }
}
