package com.sohva.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceSecrets
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceStoreTest {
    private class MapSecrets : SecretValues {
        val values = HashMap<String, String>()
        var failWrites = false

        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])

        override suspend fun write(key: String, value: String?): Outcome<Unit> {
            if (failWrites) return Outcome.Failed(AppError.SecretsUnreadable)
            if (value == null) values.remove(key) else values[key] = value
            return Outcome.Ok(Unit)
        }
    }

    private val clock = object : Clock {
        var now = 1_000L
        override fun wallMillis(): Long = now
        override fun monotonicNanos(): Long = 0
    }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java).build()
    private val secrets = MapSecrets()
    private val store = SourceStore(db.sources(), secrets, clock)

    @After
    fun close() = db.close()

    private fun m3u(id: String = "m3u-1", name: String = "Home", url: String = " http://provider.example/list.m3u ") =
        SourceConfig(Source(id, name, SourceType.M3U, importScope = ImportScope.LIVE_TV), SourceSecrets(m3uUrl = url, xmlTvUrl = " "))

    @Test
    fun savedSourcesLoadNormalisedWithSecretsOutsideTheTable() = runBlocking {
        val saved = store.save(m3u()) as Outcome.Ok
        assertEquals("http://provider.example/list.m3u", saved.value.secrets.m3uUrl)
        val loaded = (store.load("m3u-1") as Outcome.Ok).value!!
        assertEquals(saved.value, loaded)
        assertEquals(ImportScope.LIVE_TV, loaded.source.importScope)
        assertTrue(secrets.values.keys.single() == "source:m3u-1")
        val row = db.sources().get("m3u-1")!!
        assertFalse(row.toString().contains("provider.example"))
        assertEquals(Outcome.Ok(null), store.load("absent"))
    }

    @Test
    fun updatesKeepCreationTimeAndTypeIsFixed() = runBlocking {
        store.save(m3u())
        clock.now = 5_000L
        store.save(m3u(name = "Renamed"))
        val row = db.sources().get("m3u-1")!!
        assertEquals(1_000L, row.createdAt)
        assertEquals(5_000L, row.updatedAt)
        assertEquals("Renamed", row.name)
        val xtream = SourceConfig(Source("m3u-1", "X", SourceType.XTREAM), SourceSecrets(xtreamBaseUrl = "http://panel.example", xtreamUsername = "u", xtreamPassword = "p"))
        assertEquals(Outcome.Failed(AppError.Unknown), store.save(xtream))
    }

    @Test
    fun invalidSourcesAndTheLimitAreRefused() = runBlocking {
        assertEquals(Outcome.Failed(AppError.SourceNameRequired), store.save(m3u(name = " ")))
        assertEquals(Outcome.Failed(AppError.Unknown), store.save(m3u(id = "bad id")))
        for (i in 1..100) assertTrue(store.save(m3u(id = "m3u-$i", name = "S$i")) is Outcome.Ok)
        assertEquals(Outcome.Failed(AppError.SourceLimitReached(100)), store.save(m3u(id = "m3u-101")))
        assertTrue("an existing source can still be edited", store.save(m3u(id = "m3u-7", name = "Seven")) is Outcome.Ok)
    }

    @Test
    fun aSecretThatCannotBeWrittenLeavesNoRow() = runBlocking {
        secrets.failWrites = true
        assertEquals(Outcome.Failed(AppError.SecretsUnreadable), store.save(m3u()))
        assertNull(db.sources().get("m3u-1"))
    }

    @Test
    fun missingOrDamagedSecretsAreReportedNotHidden() = runBlocking {
        store.save(m3u())
        secrets.values["source:m3u-1"] = "garbage"
        assertEquals(Outcome.Failed(AppError.SecretsUnreadable), store.load("m3u-1"))
        secrets.values.clear()
        assertEquals(Outcome.Failed(AppError.SecretsUnreadable), store.load("m3u-1"))
        assertEquals(listOf("m3u-1"), store.all().map { it.id })
    }

    @Test
    fun orderByPriorityThenNameAndUnknownTypesAreLeftOut() = runBlocking {
        store.save(m3u(id = "m3u-b", name = "Beta"))
        store.save(m3u(id = "m3u-a", name = "Alpha"))
        db.sources().upsert(SourceEntity("m3u-p", "Zeta", "M3U", true, 5, 1, "BOTH", 0, 0, 0))
        db.sources().upsert(SourceEntity("future-1", "Future", "STALKER", true, 0, 1, "BOTH", 0, 0, 0))
        assertEquals(listOf("m3u-p", "m3u-a", "m3u-b"), store.observe().first().map { it.id })
    }

    @Test
    fun removingDeletesRowAndSecret() = runBlocking {
        store.save(m3u())
        assertEquals(Outcome.Ok(Unit), store.removeConfiguration("m3u-1"))
        assertNull(db.sources().get("m3u-1"))
        assertTrue(secrets.values.isEmpty())
    }
}
