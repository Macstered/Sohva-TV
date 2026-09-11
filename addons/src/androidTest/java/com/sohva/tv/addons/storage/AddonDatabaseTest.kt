package com.sohva.tv.addons.storage

import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonFailure
import com.sohva.tv.addons.AddonSecretCipher
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only in the standalone addon test APK's storage; never opens the IPTV database. */
@RunWith(AndroidJUnit4::class)
class AddonDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AddonDatabase
    private val cipher = FixtureCipher()
    private fun open() = Room.databaseBuilder(context, AddonDatabase::class.java, TEST_DB).build()
    private fun store() = EncryptedAddonStore(RoomAddonPersistence(database), cipher)
    private fun endpoint(config: String = "PrivateConfig") = AddonEndpoint.parse("https://example.invalid/$config/manifest.json")

    @Before fun prepare() {
        context.deleteDatabase(TEST_DB)
        database = open()
    }
    @After fun cleanup() {
        database.close()
        context.deleteDatabase(TEST_DB)
    }
    @Test fun encryptedInstallationsSurviveDatabaseReopen(): Unit = runBlocking {
        val original = store().install("adult", endpoint(), MANIFEST)
        database.close()
        database = open()
        val restored = store().list("adult").single()
        assertEquals(original.installationId, restored.installationId)
        assertEquals(endpoint().exportConfiguredUrl(), restored.endpoint.exportConfiguredUrl())
        assertEquals("PrivateMetadata", restored.manifest.name)
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val disk = context.getDatabasePath(TEST_DB).readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(disk.contains("PrivateConfig"))
        assertFalse(disk.contains("PrivateMetadata"))
    }
    @Test fun concurrentDuplicateInstallationsUseOneIdentity(): Unit = runBlocking {
        val storage = store()
        val ids = (1..8).map { async { storage.install("adult", endpoint(), MANIFEST).installationId } }.awaitAll()
        assertEquals(1, ids.toSet().size)
        assertEquals(1, database.installations().list("adult").size)
    }
    @Test fun staleRefreshAndWrongProfileCannotChangeRows(): Unit = runBlocking {
        val storage = store()
        val original = storage.install("adult", endpoint(), MANIFEST)
        storage.setEnabled("adult", original.installationId, false)
        assertFalse(storage.refresh("adult", original.installationId, original.revision, MANIFEST))
        assertFalse(storage.refresh("child", original.installationId, original.revision, MANIFEST))
        assertTrue(storage.list("child").isEmpty())
        assertFalse(storage.list("adult").single().enabled)
    }
    @Test fun rejectedReorderLeavesTheOriginalOrder(): Unit = runBlocking {
        val storage = store()
        val first = storage.install("adult", endpoint("First"), MANIFEST)
        val second = storage.install("adult", endpoint("Second"), MANIFEST)
        try {
            storage.reorder("adult", listOf(first.installationId, first.installationId))
            fail("Expected conflict")
        } catch (error: AddonException) {
            assertEquals(AddonFailure.CONFLICT, error.failure)
        }
        assertEquals(listOf(first.installationId, second.installationId), storage.list("adult").map { it.installationId })
        storage.reorder("adult", listOf(second.installationId, first.installationId))
        assertEquals(second.installationId, storage.list("adult").first().installationId)
    }
    private class FixtureCipher : AddonSecretCipher {
        private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        override fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext.toByteArray()), Base64.NO_WRAP)
        }
        override fun decrypt(ciphertext: String): String {
            val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }
    }
    private companion object {
        const val TEST_DB = "addon-storage-fixture.db"
        const val MANIFEST = """{"id":"test.metadata","name":"PrivateMetadata","version":"1","types":["movie"],"resources":["meta"],"catalogs":[]}"""
    }
}
