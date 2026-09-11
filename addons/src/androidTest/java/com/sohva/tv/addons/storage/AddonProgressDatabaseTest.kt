package com.sohva.tv.addons.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonProgressRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Separate synthetic DB only; the Lab process-death test covers actual keystore-backed payloads. */
class AddonProgressDatabaseTest {
    @Test fun historyIsProfileScopedBoundedAndDurable(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "addon-progress-fixture.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, AddonProgressDatabase::class.java, name).build()
        try {
            val persistence = RoomAddonProgressPersistence(database.progress())
            repeat(205) { persistence.put(AddonProgressRow("a-$it", "adult", "encrypted-fixture-$it", it.toLong())) }
            persistence.put(AddonProgressRow("b-1", "other", "encrypted-other", 0))
            assertEquals(200, persistence.recent("adult").size)
            assertNull(persistence.get("adult", "a-0"))
            assertNull(persistence.get("other", "a-204"))
            database.close()
            database = Room.databaseBuilder(context, AddonProgressDatabase::class.java, name).build()
            val restored = RoomAddonProgressPersistence(database.progress())
            assertEquals("encrypted-fixture-204", restored.get("adult", "a-204")!!.encryptedPayload)
            assertEquals(1, restored.recent("other").size)
            restored.remove("other", "a-204")
            assertNotNull(restored.get("adult", "a-204"))
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
