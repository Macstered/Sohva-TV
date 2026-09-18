package com.streammate.tv.app

import com.streammate.tv.app.Profile
import com.streammate.tv.app.Profiles
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.CatalogueGenre
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.repository.GuideRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Groups somebody defined have to come back from a backup.
 *
 * Driven through the real write and restore rather than the serialisation
 * underneath, because what matters is that a group survives the whole journey -
 * encrypted, written to a file, read back and applied to the preferences.
 */
@RunWith(AndroidJUnit4::class)
class BackupCustomGroupsTest {

    private lateinit var database: StreamMateDatabase
    private lateinit var preferences: AppPreferencesRepository
    private lateinit var manager: StreamMateBackupManager
    private lateinit var file: File

    @Before
    fun createManager() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        preferences = AppPreferencesRepository(context)
        manager = StreamMateBackupManager(
            context = context,
            secretSettingsStore = SecretSettingsStore(context, TestCipher),
            preferencesRepository = preferences,
            guideRepository = GuideRepository(database.guideDao(), organization = com.streammate.tv.iptv.repository.OrganizationRepository(database.organizationDao())),
        )
        file = File(context.cacheDir, "backup-test.smb")
        GROUPS.forEach { preferences.saveCustomCatalogueGroup(it) }
    }

    @After
    fun cleanUp() = runBlocking {
        GROUPS.forEach { preferences.deleteCustomCatalogueGroup(it.id) }
        file.delete()
        database.close()
    }

    @Test
    fun versionOneBackupRemainsReadableWithoutOrganizationTables() = runBlocking {
        manager.write(Uri.fromFile(file), PASSPHRASE)
        val bytes = com.streammate.tv.core.security.PortableBackupCipher.decrypt(file.readBytes(), PASSPHRASE.toCharArray())
        val root = kotlinx.serialization.json.Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as kotlinx.serialization.json.JsonObject
        val old = kotlinx.serialization.json.JsonObject(root.toMutableMap().apply {
            this["formatVersion"] = kotlinx.serialization.json.JsonPrimitive(1)
            remove("organization")
        })
        file.writeBytes(com.streammate.tv.core.security.PortableBackupCipher.encrypt(old.toString().toByteArray(), PASSPHRASE.toCharArray()))
        GROUPS.forEach { preferences.deleteCustomCatalogueGroup(it.id) }
        manager.restore(Uri.fromFile(file), PASSPHRASE)
        assertEquals(GROUPS.sortedBy { it.id }, storedGroups().sortedBy { it.id })
    }

    @Test
    fun sportsCountryPriorityKeepsItsOrderAcrossBackupRestore() = runBlocking {
        val previous = preferences.preferences.first().sportsChannelPriority
        try {
            preferences.setSportsChannelPriority(listOf("ES", "ALB", "EN"))
            manager.write(Uri.fromFile(file), PASSPHRASE)
            preferences.setSportsChannelPriority(emptyList())
            manager.restore(Uri.fromFile(file), PASSPHRASE)
            assertEquals(listOf("ES", "AL", "EN"), preferences.preferences.first().sportsChannelPriority)
        } finally { preferences.setSportsChannelPriority(previous) }
    }

    @Test
    fun aChannelsOwnLogoAndNumberComeBackFromTheBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secrets = SecretSettingsStore(context, TestCipher)
        val source = com.streammate.tv.core.model.IptvSourceConfiguration(
            id = "backup-logo-source",
            name = "Backup source",
            type = com.streammate.tv.core.model.IptvSourceType.M3U,
            m3uUrl = "http://provider.example/list.m3u",
        )
        secrets.upsertSource(source)
        val logos = ChannelLogoStore(context)
        val url = logos.save("backup-logo-source:one", TINY_PNG)
        database.guideDao().upsertChannelPreference(
            com.streammate.tv.core.database.ChannelPreferenceEntity(
                "backup-logo-source:one", "backup-logo-source", null, null, false, null, null, 1,
                customLogoUrl = url, channelNumber = 12,
            ),
        )
        try {
            manager.write(Uri.fromFile(file), PASSPHRASE)
            // The logo lives only on this TV, so a restore has to rebuild the file itself.
            database.guideDao().deleteChannelPreference("backup-logo-source:one")
            logos.delete(url)
            assertFalse(File(java.net.URI(url)).exists())

            manager.restore(Uri.fromFile(file), PASSPHRASE)

            val restored = database.guideDao().channelPreference("backup-logo-source:one")!!
            assertEquals(12, restored.channelNumber)
            assertTrue(logos.isLocal(restored.customLogoUrl))
            assertTrue(File(java.net.URI(restored.customLogoUrl!!)).isFile)
            logos.delete(restored.customLogoUrl)
        } finally {
            secrets.deleteSource(source.id)
        }
    }

    @Test
    fun organizationRulesAndFilmAliasesSurviveEncryptedBackup() = runBlocking {
        val dao = database.organizationDao()
        dao.registerFilmAliases(listOf(listOf("vod:movie:source:1", "work:film")))
        val identity = dao.aliases().first().identity
        dao.change(listOf(com.streammate.tv.core.database.OrganizationChange(
            com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.MOVIES, groupKey = "name:films", itemKey = identity),
            enabled = false, changeEnabled = true, position = 3, changePosition = true,
        )))
        val snapshot = dao.snapshot()
        // Imported identities with no user rule are rebuilt from the catalogue.
        // They must not turn a settings backup into a full catalogue export.
        dao.registerFilmAliases(listOf(listOf("vod:movie:source:unmodified", "work:unmodified")))
        preferences.setMovieIdentityMark("old-catalogue-already-indexed")
        manager.write(Uri.fromFile(file), PASSPHRASE)
        dao.restore(com.streammate.tv.core.database.OrganizationSnapshot())
        manager.restore(Uri.fromFile(file), PASSPHRASE)
        val restored = dao.snapshot()
        assertTrue(restored.rules.containsAll(snapshot.rules))
        assertEquals(snapshot.aliases.toSet(), restored.aliases.toSet())
        assertEquals(null, preferences.movieIdentityMark())
    }

    @Test
    fun largeCatalogueBackupKeepsOnlyCustomizedFilmIdentities() = runBlocking {
        val dao = database.organizationDao()
        // More index entries than fit as a JSON object tree on a small TV heap.
        // Seed in batches so the test itself never materializes the full index.
        repeat(150) { batch ->
            dao.upsertAliases(List(1_000) { offset ->
                val index = batch * 1_000 + offset
                com.streammate.tv.core.database.OrganizationAliasEntity(
                    "vod:movie:large-provider:$index", "film:catalogue:$index",
                )
            })
        }
        val keptAliases = listOf(
            com.streammate.tv.core.database.OrganizationAliasEntity("vod:movie:one:kept", "film:kept"),
            com.streammate.tv.core.database.OrganizationAliasEntity("vod:movie:two:kept", "film:kept"),
            com.streammate.tv.core.database.OrganizationAliasEntity("work:kept", "film:kept"),
            com.streammate.tv.core.database.OrganizationAliasEntity("vod:movie:one:legacy", "film:legacy"),
            com.streammate.tv.core.database.OrganizationAliasEntity("vod:movie:two:legacy", "film:legacy"),
        )
        dao.upsertAliases(keptAliases)
        val keptRules = listOf(
            com.streammate.tv.core.database.OrganizationRuleEntity("MOVIES", "", "", "film:kept", false, null, null),
            com.streammate.tv.core.database.OrganizationRuleEntity("MOVIES", "two", "id:7", "film:kept", null, null, 4),
            com.streammate.tv.core.database.OrganizationRuleEntity("MOVIES", "one", "id:8", "vod:movie:one:legacy", false, null, 9),
            com.streammate.tv.core.database.OrganizationRuleEntity("MOVIES", "", "name:hidden", "", false, "TITLE_ASC", null),
            com.streammate.tv.core.database.OrganizationRuleEntity("LIVE", "", "name:news", "channel:1", false, null, 2),
        )
        dao.upsertRules(keptRules)
        manager.write(Uri.fromFile(file), PASSPHRASE)
        assertTrue("Backup should contain settings, not the catalogue index", file.length() in 1..65_536)
        assertEquals(150_005L, dao.observeAliasCount().first())

        dao.restore(com.streammate.tv.core.database.OrganizationSnapshot())
        manager.restore(Uri.fromFile(file), PASSPHRASE)
        val restored = dao.snapshot()
        assertTrue(restored.rules.containsAll(keptRules))
        assertEquals(keptAliases.toSet(), restored.aliases.toSet())
        // A playlist refresh may re-register an unmodified movie without
        // changing the identities/rules which were restored from the backup.
        dao.registerFilmAliases(listOf(listOf("vod:movie:one:kept", "work:kept")))
        assertTrue(dao.rules().containsAll(keptRules))
        assertEquals("film:kept", dao.identities(listOf("vod:movie:two:kept"))["vod:movie:two:kept"])
    }

    @Test
    fun everyProfileAndWhatItKeepsSurvivesABackup() = runBlocking {
        val kids = requireNotNull(preferences.addProfile("Kids", 2))
        preferences.setActiveProfile(kids.id)
        preferences.setFavouriteChannel("cartoons", true)
        preferences.setAllowedGroups(kids.id, com.streammate.tv.core.model.LibraryRoom.LIVE, setOf("name:cartoons"))
        preferences.setActiveProfile(Profiles.DEFAULT_ID)
        preferences.setFavouriteChannel("news", true)
        try {
            manager.write(Uri.fromFile(file), PASSPHRASE)

            preferences.removeProfile(kids.id)
            preferences.setFavouriteChannel("news", false)
            assertEquals(emptyList<Profile>(), preferences.preferences.first().profiles)

            manager.restore(Uri.fromFile(file), PASSPHRASE)

            val restored = preferences.preferences.first()
            assertEquals(listOf(kids), restored.profiles)
            assertEquals(Profiles.DEFAULT_ID, restored.activeProfileId)
            assertEquals(setOf("news"), restored.favouriteChannelIds)
            assertEquals(setOf("cartoons"), preferences.profileData(kids.id).favouriteChannelIds)
            assertEquals(setOf("name:cartoons"), preferences.profileData(kids.id).restriction.live)
        } finally {
            preferences.removeProfile(kids.id)
            preferences.setFavouriteChannel("news", false)
        }
    }

    @Test
    fun groupsOfYourOwnSurviveABackupAndRestore() = runBlocking {
        manager.write(Uri.fromFile(file), PASSPHRASE)
        assertTrue("the backup file was not written", file.length() > 0)

        // Gone, as though from a fresh install.
        GROUPS.forEach { preferences.deleteCustomCatalogueGroup(it.id) }
        assertEquals(emptyList<CatalogueCustomGroup>(), storedGroups())

        manager.restore(Uri.fromFile(file), PASSPHRASE)

        assertEquals(GROUPS.sortedBy { it.id }, storedGroups().sortedBy { it.id })
    }

    @Test
    fun retiredBrowserSwitchInAnOldBackupIsIgnoredAndNotWrittenAgain() = runBlocking {
        manager.write(Uri.fromFile(file), PASSPHRASE)
        val cipher = com.streammate.tv.core.security.PortableBackupCipher
        val root = kotlinx.serialization.json.Json.parseToJsonElement(
            cipher.decrypt(file.readBytes(), PASSPHRASE.toCharArray()).toString(Charsets.UTF_8),
        ) as kotlinx.serialization.json.JsonObject
        val oldPreferences = root["preferences"] as kotlinx.serialization.json.JsonObject
        val legacy = kotlinx.serialization.json.JsonObject(root.toMutableMap().apply {
            this["preferences"] = kotlinx.serialization.json.JsonObject(oldPreferences.toMutableMap().apply {
                this["catalogueBrowserV2Enabled"] = kotlinx.serialization.json.JsonPrimitive(false)
            })
        })
        file.writeBytes(cipher.encrypt(legacy.toString().toByteArray(), PASSPHRASE.toCharArray()))
        GROUPS.forEach { preferences.deleteCustomCatalogueGroup(it.id) }
        manager.restore(Uri.fromFile(file), PASSPHRASE)
        assertEquals(GROUPS.sortedBy { it.id }, storedGroups().sortedBy { it.id })
        manager.write(Uri.fromFile(file), PASSPHRASE)
        val rewritten = kotlinx.serialization.json.Json.parseToJsonElement(
            cipher.decrypt(file.readBytes(), PASSPHRASE.toCharArray()).toString(Charsets.UTF_8),
        ) as kotlinx.serialization.json.JsonObject
        assertFalse((rewritten["preferences"] as kotlinx.serialization.json.JsonObject).containsKey("catalogueBrowserV2Enabled"))
    }

    private suspend fun storedGroups(): List<CatalogueCustomGroup> =
        preferences.preferences.first().customCatalogueGroups

    @Test
    fun colorThemeSurvivesBackupAndOlderBackupsUseTheDefault() = runBlocking {
        val original = preferences.preferences.first().colorTheme
        try {
            ColorTheme.entries.filter { it != ColorTheme.DEFAULT }.forEach { theme ->
                preferences.setColorTheme(theme)
                manager.write(Uri.fromFile(file), PASSPHRASE)
                preferences.setColorTheme(ColorTheme.DEFAULT)
                manager.restore(Uri.fromFile(file), PASSPHRASE)
                assertEquals(theme, preferences.preferences.first().colorTheme)
            }

            val cipher = com.streammate.tv.core.security.PortableBackupCipher
            val root = kotlinx.serialization.json.Json.parseToJsonElement(
                cipher.decrypt(file.readBytes(), PASSPHRASE.toCharArray()).toString(Charsets.UTF_8),
            ) as kotlinx.serialization.json.JsonObject
            val savedPreferences = root["preferences"] as kotlinx.serialization.json.JsonObject
            val legacy = kotlinx.serialization.json.JsonObject(root.toMutableMap().apply {
                this["preferences"] = kotlinx.serialization.json.JsonObject(savedPreferences.toMutableMap().apply { remove("colorTheme") })
            })
            file.writeBytes(cipher.encrypt(legacy.toString().toByteArray(), PASSPHRASE.toCharArray()))
            manager.restore(Uri.fromFile(file), PASSPHRASE)
            assertEquals(ColorTheme.DEFAULT, preferences.preferences.first().colorTheme)
        } finally {
            preferences.setColorTheme(original)
        }
    }

    private object TestCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "test:$plainText"
        override fun decrypt(encoded: String): String = encoded.removePrefix("test:")
    }

    private companion object {
        const val PASSPHRASE = "kolmetoista merkkia"

        val GROUPS = listOf(
            CatalogueCustomGroup(
                id = "children",
                name = "Lasten elokuvat",
                genres = setOf(CatalogueGenre.FAMILY, CatalogueGenre.ANIMATION),
            ),
            // Every field set, so nothing can be quietly dropped in transit.
            CatalogueCustomGroup(
                id = "eighties",
                name = "80-luvun toiminta",
                genres = setOf(CatalogueGenre.ACTION),
                fromYear = 1980,
                toYear = 1989,
                minRating = 6.5,
            ),
        )
    }
}

/** A one-pixel transparent PNG, enough for the logo store to accept and rewrite. */
private val TINY_PNG: ByteArray = kotlin.io.encoding.Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
)
