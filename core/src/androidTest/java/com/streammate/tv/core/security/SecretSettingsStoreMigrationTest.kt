package com.streammate.tv.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretSettingsStoreMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("sportmate_secure_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun migratesLegacySingleSourceIntoEncryptedSourceCollection() {
        context.getSharedPreferences("sportmate_secure_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("m3u", TestCipher.encrypt("http://provider.example/list.m3u"))
            .putString("xmltv", TestCipher.encrypt("http://provider.example/guide.xml"))
            .commit()
        val store = SecretSettingsStore(context, TestCipher)

        val source = store.loadSources().single()

        assertEquals(IptvSourceConfiguration.LEGACY_SOURCE_ID, source.id)
        assertEquals(IptvSourceType.M3U, source.type)
        assertEquals(1, source.connectionLimit)
        assertEquals("http://provider.example/list.m3u", source.m3uUrl)
        assertTrue(
            context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
                .contains("sources_v1"),
        )
        assertFalse(
            context.getSharedPreferences("sportmate_secure_settings", Context.MODE_PRIVATE)
                .contains("m3u"),
        )
    }

    @Test
    fun epgOffsetPersistsInEncryptedSourceCollection() {
        val store = SecretSettingsStore(context, TestCipher)
        val source = IptvSourceConfiguration(
            id = "offset-source",
            name = "Offset source",
            type = IptvSourceType.M3U,
            epgOffsetMinutes = -90,
        )

        store.upsertSource(source)

        assertEquals(-90, store.loadSources().single().epgOffsetMinutes)
        val stored = context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
            .getString("sources_v1", null)
            .orEmpty()
        assertTrue(stored.startsWith("encrypted:"))
        assertFalse(stored.contains("Offset source"))
    }

    @Test
    fun parentalPinIsEncryptedVerifiedAndCleared() {
        val store = SecretSettingsStore(context, TestCipher)

        store.saveParentalPin("2468")

        assertTrue(store.verifyParentalPin("2468"))
        assertFalse(store.verifyParentalPin("1357"))
        assertEquals("2468", store.parentalPinForEncryptedBackup())
        assertFalse(
            context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
                .getString("parental_pin_v1", null)
                .orEmpty()
                .equals("2468"),
        )

        store.clearParentalPin()
        assertFalse(store.verifyParentalPin("2468"))
    }

    @Test
    fun metadataOptInEncryptsTmdbTokenAndKeepsProvidersDisabledByDefault() {
        val store = SecretSettingsStore(context, TestCipher)

        assertEquals(MetadataSettings(), store.loadMetadataSettings())
        store.saveMetadataSettings(
            MetadataSettings(
                tmdbEnabled = true,
                tmdbReadAccessToken = "user-read-token",
                tvmazeEnabled = true,
            ),
        )

        assertEquals(
            MetadataSettings(true, "user-read-token", true),
            store.loadMetadataSettings(),
        )
        val rawToken = context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
            .getString("metadata_tmdb_token_v1", null)
        assertEquals("encrypted:user-read-token", rawToken)
        assertFalse(rawToken == "user-read-token")
        assertTrue(
            runCatching {
                store.saveMetadataSettings(MetadataSettings(tmdbEnabled = true))
            }.isFailure,
        )
    }

    @Test
    fun sportsApiKeyIsEncryptedAndRedacted() {
        val store = SecretSettingsStore(context, TestCipher)

        store.saveSportsApiSettings(SportsApiSettings("sports-user-key"))

        val loaded = store.loadSportsApiSettings()
        assertEquals("sports-user-key", loaded.apiKey)
        assertFalse(loaded.toString().contains("sports-user-key"))
        assertEquals(
            "encrypted:sports-user-key",
            context.getSharedPreferences("streammate_secure_sources", Context.MODE_PRIVATE)
                .getString("sports_api_key_v1", null),
        )
    }
}

private object TestCipher : SecretCipher {
    override fun encrypt(plainText: String): String = "encrypted:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("encrypted:")
}
