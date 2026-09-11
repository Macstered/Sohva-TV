package com.streammate.tv.release

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.PreferredLanguageSlot
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.core.database.PlaybackProgressEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.VodMovieEntity
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opted-in disposable emulator only. No clearing, uninstall or downgrade. */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication

    @Before fun requireDisposableEmulator() {
        check(InstrumentationRegistry.getArguments().getString("sohvaDisposableReleaseTest") == "true")
        check(app.packageName == "com.streammate.tv")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }

    // Uses only beta-12 APIs. Run against the published beta-12 APK, then install
    // beta 13 with -r and run verifyUpgrade in a separate instrumentation process.
    @Test fun seedBeta12(): Unit = runBlocking {
        assertEquals(13, versionCode())
        assertTrue("Never overwrite existing sources", app.container.secretSettingsStore.loadSources().isEmpty())
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
        app.container.secretSettingsStore.saveSources(listOf(IptvSourceConfiguration(
            id = SOURCE, name = "Synthetic release fixture", type = IptvSourceType.XTREAM,
            enabled = false, xtreamBaseUrl = "https://example.invalid",
            xtreamUsername = "fixture-user", xtreamPassword = "fixture-only-password",
        )))
        assertEquals(1, app.container.secretSettingsStore.loadSources().size)
        val preferences = app.container.preferencesRepository
        preferences.setFavouriteChannel("$SOURCE:channel", true)
        preferences.setShowChannelNumbers(false)
        preferences.setPreferredLanguage(PreferredLanguageSlot.PRIMARY_SUBTITLE, "fi")
        val database = StreamMateDatabase.create(app)
        try {
            database.catalogueDao().upsertMovies(listOf(VodMovieEntity(
                sourceId = SOURCE, snapshotId = "release-fixture", movieId = "movie",
                name = "Synthetic upgrade movie", normalizedName = "synthetic upgrade movie",
                categoryName = "Fixture", posterUrl = null,
                encryptedStreamUrl = app.container.secretCipher.encrypt("https://example.invalid/movie.mp4"),
                year = 2026, rating = null, plot = "Synthetic offline fixture",
            )))
            database.catalogueDao().activateCatalogueSnapshot(SOURCE, "release-fixture", 1, System.currentTimeMillis())
            database.catalogueDao().upsertProgress(PlaybackProgressEntity(
                contentKey = KEY, sourceId = SOURCE, contentType = "movie", itemId = "movie",
                positionMillis = 42_000, durationMillis = 600_000, completed = false,
                lastWatchedEpochMillis = System.currentTimeMillis(),
            ))
        } finally { database.close() }
        } finally {
            // Exercise an ordinary Activity stop before the instrumentation
            // runner kills the process; Android flushes queued preferences here.
            activity.close()
        }
    }

    @Test fun verifySeedColdRestart(): Unit = runBlocking {
        assertEquals(13, versionCode())
        verifyLegacyState()
    }

    @Test fun verifyUpgrade(): Unit = runBlocking {
        assertEquals(14, versionCode())
        verifyLegacyState()
        ReleaseDiscoverAssertions.assertEmpty(app)
    }

    private suspend fun verifyLegacyState() {
        val encrypted = app.getSharedPreferences("streammate_secure_sources", 0).getString("sources_v1", null)
        assertNotNull("Source payload must survive the update", encrypted)
        assertTrue("Wrapped data key must survive process death", app.getSharedPreferences("streammate_secret_envelope", 0).contains("data_key_v2"))
        // Exercise failures directly: loadSources intentionally returns an empty
        // list for unreadable data, which would hide the cause in this test.
        com.streammate.tv.core.security.IptvSourceConfigurationCodec.decode(app.container.secretCipher.decrypt(encrypted!!))
        val source = app.container.secretSettingsStore.loadSources().single()
        assertEquals(SOURCE, source.id)
        assertEquals("fixture-user", source.xtreamUsername)
        assertEquals("fixture-only-password", source.xtreamPassword)
        assertFalse(source.enabled)
        val preferences = app.container.preferencesRepository.preferences.first()
        assertTrue("$SOURCE:channel" in preferences.favouriteChannelIds)
        assertFalse(preferences.showChannelNumbers)
        assertEquals("fi", preferences.preferredSubtitleLanguage)
        val database = StreamMateDatabase.create(app)
        try {
            // Disabled sources are intentionally excluded from browse queries.
            // Inspect the persisted row without enabling any network service.
            database.openHelper.readableDatabase.query(
                "SELECT name, encryptedStreamUrl FROM vod_movies WHERE sourceId = ?",
                arrayOf(SOURCE),
            ).use { movie ->
                assertEquals(1, movie.count)
                assertTrue(movie.moveToFirst())
                assertEquals("Synthetic upgrade movie", movie.getString(0))
                assertEquals("https://example.invalid/movie.mp4", app.container.secretCipher.decrypt(movie.getString(1)))
            }
            assertEquals(42_000L, database.catalogueDao().progress(KEY, "default")?.positionMillis)
        } finally { database.close() }
    }

    @Test fun verifyFreshInstall(): Unit = runBlocking {
        assertEquals(14, versionCode())
        assertTrue(app.container.secretSettingsStore.loadSources().isEmpty())
        assertTrue(app.container.preferencesRepository.preferences.first().favouriteChannelIds.isEmpty())
        ReleaseDiscoverAssertions.assertEmpty(app)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(): Int = app.packageManager.getPackageInfo(app.packageName, 0).versionCode

    private companion object {
        const val SOURCE = "release-upgrade-fixture"
        const val KEY = "movie:$SOURCE:movie"
    }
}

// Kept outside the beta-12 seed class: the published old APK has no addon module.
private object ReleaseDiscoverAssertions {
    suspend fun assertEmpty(app: StreamMateApplication) {
        val host = com.streammate.tv.addons.AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        assertTrue(host.manager.list(profile).isEmpty())
        assertTrue(host.library.list(profile).isEmpty())
        assertTrue(host.progress.recent(profile).isEmpty())
        assertFalse(app.container.runtimePolicy.isLab)
    }
}
