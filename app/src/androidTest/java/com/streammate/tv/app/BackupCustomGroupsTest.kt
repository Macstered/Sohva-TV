package com.streammate.tv.app

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
    fun organizationRulesAndFilmAliasesSurviveEncryptedBackup() = runBlocking {
        val dao = database.organizationDao()
        dao.registerFilmAliases(listOf(listOf("vod:movie:source:1", "work:film")))
        val identity = dao.aliases().first().identity
        dao.change(listOf(com.streammate.tv.core.database.OrganizationChange(
            com.streammate.tv.core.model.OrganizationKey(com.streammate.tv.core.model.LibraryRoom.MOVIES, groupKey = "name:films", itemKey = identity),
            enabled = false, changeEnabled = true, position = 3, changePosition = true,
        )))
        val snapshot = dao.snapshot()
        manager.write(Uri.fromFile(file), PASSPHRASE)
        dao.restore(com.streammate.tv.core.database.OrganizationSnapshot())
        manager.restore(Uri.fromFile(file), PASSPHRASE)
        val restored = dao.snapshot()
        assertTrue(restored.rules.containsAll(snapshot.rules))
        assertEquals(snapshot.aliases, restored.aliases)
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
