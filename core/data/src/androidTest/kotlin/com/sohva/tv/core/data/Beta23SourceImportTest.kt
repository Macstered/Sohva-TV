package com.sohva.tv.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.time.SystemClock
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one-time import of beta 23's sources against a store laid out exactly as beta 23 wrote it,
 * with its Keystore alias and envelope names, inside this test app's own sandbox (the release-key
 * check against a real beta 23 install is the owner-approved step that follows).
 */
@RunWith(AndroidJUnit4::class)
class Beta23SourceImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val spec = EnvelopeSpec.BETA23_MAIN
    private lateinit var db: SohvaDatabase

    private class MapSecrets : SecretValues {
        val values = HashMap<String, String>()
        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])
        override suspend fun write(key: String, value: String?): Outcome<Unit> {
            if (value == null) values.remove(key) else values[key] = value
            return Outcome.Ok(Unit)
        }
    }

    private val secrets = MapSecrets()

    @Before
    fun open() {
        clearOldStore()
        db = Room.inMemoryDatabaseBuilder(context, SohvaDatabase::class.java).build()
    }

    @After
    fun close() {
        db.close()
        clearOldStore()
    }

    private fun clearOldStore() {
        for (name in listOf(Beta23SourceImport.FILE, Beta23SourceImport.LEGACY_FILE, spec.prefsFile)) {
            context.deleteSharedPreferences(name)
        }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
    }

    /** Beta 23's own writing side: its Keystore key, its wrapped data key, its value format. */
    private fun beta23Cipher() = EnvelopeCipher(spec, AndroidKeystoreKeyProvider(spec.keystoreAlias), PrefsWrappedKeyStore(context, spec))

    private fun prefsExists(name: String) = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()

    private fun encodedList(): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { d ->
            fun s(v: String) = v.toByteArray().let { d.writeInt(it.size); d.write(it) }
            fun n(v: String?) {
                d.writeBoolean(v != null)
                if (v != null) s(v)
            }
            d.writeInt(0x53544D53)
            d.writeInt(3)
            d.writeInt(2)
            s("m3u-home"); s("Home"); s("M3U"); d.writeBoolean(true); d.writeInt(2); d.writeInt(0)
            n("http://provider.example/list.m3u"); n("http://provider.example/guide.xml"); n(null); n(null); n(null)
            s("LIVE_TV"); d.writeInt(60)
            s("xtream-main"); s("Panel"); s("XTREAM"); d.writeBoolean(false); d.writeInt(1); d.writeInt(0)
            n(null); n(null); n("http://panel.example:8080"); n("viewer"); n("pass word")
            s("BOTH"); d.writeInt(0)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun importer() = Beta23SourceImport(
        context,
        SourceStore(db.sources(), secrets, SystemClock),
        ServiceKeys(secrets),
        db.appMeta(),
        Dispatchers.IO,
    )

    @Test
    fun theSourceListAndKeysImportOnce() = runBlocking {
        val cipher = beta23Cipher()
        context.getSharedPreferences(Beta23SourceImport.FILE, Context.MODE_PRIVATE).edit()
            .putString("sources_v1", cipher.encrypt(encodedList()))
            .putString(ServiceKeys.TMDB_TOKEN, cipher.encrypt("tmdb-token"))
            .putBoolean(ServiceKeys.TMDB_ENABLED, true)
            .putString(ServiceKeys.API_SPORTS_KEY, cipher.encrypt("sports-key"))
            .commit()

        assertEquals(Beta23SourceImport.Result.Imported(listOf("m3u-home", "xtream-main")), importer().run())
        val store = SourceStore(db.sources(), secrets, SystemClock)
        val home = (store.load("m3u-home") as Outcome.Ok).value!!
        assertEquals(ImportScope.LIVE_TV, home.source.importScope)
        assertEquals(60, home.source.epgOffsetMinutes)
        assertEquals("http://provider.example/guide.xml", home.secrets.xmlTvUrl)
        val panel = (store.load("xtream-main") as Outcome.Ok).value!!
        assertFalse(panel.source.enabled)
        assertEquals("pass word", panel.secrets.xtreamPassword)
        assertEquals("tmdb-token", secrets.values[ServiceKeys.TMDB_TOKEN])
        assertEquals("sports-key", secrets.values[ServiceKeys.API_SPORTS_KEY])
        assertEquals("the old store stays for the later importers", true, prefsExists(Beta23SourceImport.FILE))
        assertEquals(Beta23SourceImport.Result.Nothing, importer().run())
    }

    @Test
    fun theFirstBuildsSingleSourceImportsAsIptv() = runBlocking {
        val cipher = beta23Cipher()
        context.getSharedPreferences(Beta23SourceImport.LEGACY_FILE, Context.MODE_PRIVATE).edit()
            .putString("m3u", cipher.encrypt("http://provider.example/old.m3u"))
            .putString("xmltv", cipher.encrypt("http://provider.example/old.xml"))
            .commit()
        assertEquals(Beta23SourceImport.Result.Imported(listOf("m3u-primary")), importer().run())
        val source = SourceStore(db.sources(), secrets, SystemClock).source("m3u-primary")!!
        assertEquals("IPTV", source.name)
    }

    @Test
    fun anUnreadableStoreIsReportedAndNothingIsWrittenOverIt() = runBlocking {
        val cipher = beta23Cipher()
        context.getSharedPreferences(Beta23SourceImport.FILE, Context.MODE_PRIVATE).edit()
            .putString("sources_v1", cipher.encrypt(encodedList()))
            .commit()
        // The Keystore key is gone (cleared data, a firmware update): the wrapped data key cannot open.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        val wrappedBefore = context.getSharedPreferences(spec.prefsFile, Context.MODE_PRIVATE).getString(spec.wrappedKeyName, null)
        assertEquals(Beta23SourceImport.Result.Failed(AppError.SecretsUnreadable), importer().run())
        assertEquals(AppError.SecretsUnreadable, importer().problem())
        assertEquals(wrappedBefore, context.getSharedPreferences(spec.prefsFile, Context.MODE_PRIVATE).getString(spec.wrappedKeyName, null))
        assertFalse("no new Keystore key", KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(spec.keystoreAlias))
        assertNull(db.sources().get("m3u-home"))
    }

    @Test
    fun anInstallWithoutBeta23CreatesNothing() = runBlocking {
        assertEquals(Beta23SourceImport.Result.Nothing, importer().run())
        assertFalse(prefsExists(Beta23SourceImport.FILE))
        assertFalse(prefsExists(spec.prefsFile))
    }
}
