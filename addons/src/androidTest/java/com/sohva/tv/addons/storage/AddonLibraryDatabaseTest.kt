package com.sohva.tv.addons.storage

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.AddonLibraryRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AddonLibraryDatabaseTest {
    @Test fun libraryPersistsAndCapacityDoesNotPruneBookmarks(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "addon-library-fixture.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, AddonLibraryDatabase::class.java, name).build()
        try {
            var storage = RoomAddonLibraryPersistence(database.library())
            repeat(1000) { assertTrue(storage.put(AddonLibraryRow("a-$it", "adult", "encrypted-$it", it.toLong()))) }
            assertFalse(storage.put(AddonLibraryRow("overflow", "adult", "encrypted", 1001)))
            assertTrue(storage.put(AddonLibraryRow("b-1", "other", "encrypted", 1001)))
            database.close(); database = Room.databaseBuilder(context, AddonLibraryDatabase::class.java, name).build()
            storage = RoomAddonLibraryPersistence(database.library())
            assertEquals(1000, storage.list("adult").size); assertNotNull(storage.get("adult", "a-0"))
            assertNull(storage.get("other", "a-0")); storage.remove("other", "a-0")
            assertNotNull(storage.get("adult", "a-0")); storage.remove("adult", "a-0")
            assertTrue(storage.put(AddonLibraryRow("overflow", "adult", "encrypted", 1001)))
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
