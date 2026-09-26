package com.sohva.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.ContentGroupEntity
import com.sohva.tv.core.data.database.FavouriteChannelEntity
import com.sohva.tv.core.data.database.RecentChannelEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.database.WatchProgressEntity
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.data.profile.GroupChoice
import com.sohva.tv.core.data.profile.ProfileStore
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Profiles
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Spec 04 §11 "Unit": adding, removing with everything kept, switching, restrictions, locks and the PIN. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileStoreTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private class MapSecrets : SecretValues {
        val values = HashMap<String, String>()

        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(values[key])

        override suspend fun write(key: String, value: String?): Outcome<Unit> {
            if (value == null) values.remove(key) else values[key] = value
            return Outcome.Ok(Unit)
        }
    }

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SohvaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_790_000_000_000L
    private val clock = object : Clock {
        override fun wallMillis(): Long = now++
        override fun monotonicNanos(): Long = 0
    }
    private val prefs = AppPreferences(MemoryStore())
    private val secrets = MapSecrets()
    private val store = ProfileStore(prefs, { db.profiles() }, secrets, Dispatchers.Unconfined, clock, CoroutineScope(Dispatchers.Unconfined))

    @After
    fun tearDown() = db.close()

    @Test
    fun addingNamesAndColoursAProfileWithoutSwitchingToIt() = runBlocking {
        assertTrue(store.add("Aino"))
        val household = store.household.value
        assertEquals(listOf("default", "p1790000000000"), household.shown.map { it.id })
        assertEquals(1, household.shown[1].colorIndex)
        assertEquals("default", store.activeId)
        assertEquals(household, prefs.household.first())
        repeat(4) { store.add("More $it") }
        assertFalse(store.add("Seventh"))
        assertEquals(6, store.household.value.shown.size)
    }

    @Test
    fun removingAProfileTakesEverythingItKeptAndReturnsToTheFirstViewer() = runBlocking {
        store.add("Aino")
        val aino = store.household.value.shown[1].id
        store.switchTo(aino)
        assertEquals(aino, store.activeId)
        for (profile in listOf(aino, Profiles.DEFAULT_ID)) {
            db.viewer().addFavourite(FavouriteChannelEntity(profile, "s:c1", 1))
            db.viewer().recordRecent(RecentChannelEntity(profile, "s:c1", 1))
            db.progress().put(WatchProgressEntity(profile, "vod:movie:s:1", "s", "MOVIE", null, null, 1, 10, false, 1))
            store.setAllowed(profile, OrgRoom.LIVE, "name:news", true)
        }
        store.setLocked("s:c2", true)
        prefs.setLastChannel(aino, "s:c1")
        prefs.setLastChannel(Profiles.DEFAULT_ID, "s:c9")

        store.remove(aino)

        assertEquals(Profiles.DEFAULT_ID, store.activeId)
        assertEquals(listOf("default"), store.household.value.shown.map { it.id })
        assertEquals(emptyList<String>(), db.viewer().favouriteKeys(aino))
        assertEquals(emptyList<String>(), db.viewer().recentKeys(aino))
        assertNull(db.progress().get(aino, "vod:movie:s:1"))
        assertFalse(store.restriction(aino).restricted)
        assertEquals(emptyList<String>(), db.profiles().lockedKeys(aino))
        assertNull(prefs.lastChannel(aino).first())
        // The first viewer's data stays.
        assertEquals(listOf("s:c1"), db.viewer().favouriteKeys(Profiles.DEFAULT_ID))
        assertEquals("s:c9", prefs.lastChannel(Profiles.DEFAULT_ID).first())
        assertTrue(store.restriction(Profiles.DEFAULT_ID).restricted)
        // The first viewer cannot be removed.
        store.remove(Profiles.DEFAULT_ID)
        assertEquals(listOf("s:c1"), db.viewer().favouriteKeys(Profiles.DEFAULT_ID))
    }

    @Test
    fun thePinGuardsEnteringUnrestrictedProfilesOnly() = runBlocking {
        store.add("Kids")
        val kids = store.household.value.shown[1].id
        store.setAllowed(kids, OrgRoom.LIVE, "name:cartoons", true)
        assertFalse(store.entryNeedsPin(Profiles.DEFAULT_ID))
        store.setPin("2468")
        assertTrue(store.entryNeedsPin(Profiles.DEFAULT_ID))
        assertFalse(store.entryNeedsPin(kids))
        assertFalse(store.managementNeedsPin())
        store.switchTo(kids)
        assertTrue(store.managementNeedsPin())
        // Lifting the last group lifts the restriction.
        store.setAllowed(kids, OrgRoom.LIVE, "name:cartoons", false)
        assertFalse(store.entryNeedsPin(Profiles.DEFAULT_ID))
    }

    @Test
    fun aPinIsStoredEncryptedChecksAndGuardsItsRemoval() = runBlocking {
        assertEquals(Outcome.Failed(AppError.PinFormat), store.setPin("12"))
        assertFalse(store.household.value.pinConfigured)
        assertFalse(store.verifyPin("1234"))
        assertEquals(Outcome.Ok(Unit), store.setPin("1234"))
        assertTrue(store.household.value.pinConfigured)
        assertEquals("1234", secrets.values[ProfileStore.PIN_KEY])
        assertTrue(store.verifyPin("1234"))
        assertFalse(store.verifyPin("4321"))
        assertFalse(store.removePin("4321"))
        assertTrue(store.household.value.pinConfigured)
    }

    @Test
    fun removingThePinUnlocksEveryProfileAndChangingItKeepsTheLocks() = runBlocking {
        store.add("Aino")
        val aino = store.household.value.shown[1].id
        store.setPin("1234")
        store.setLocked("s:c1", true)
        store.switchTo(aino)
        store.setLocked("s:c2", true)
        assertTrue(store.isLocked("s:c2"))
        assertFalse(store.isLocked("s:c1"))

        assertEquals(Outcome.Ok(false), store.changePin("9999", "5678"))
        assertEquals(Outcome.Ok(true), store.changePin("1234", "5678"))
        assertTrue(store.isLocked("s:c2"))

        assertTrue(store.removePin("5678"))
        assertFalse(store.household.value.pinConfigured)
        assertNull(secrets.values[ProfileStore.PIN_KEY])
        assertEquals(emptyList<String>(), db.profiles().lockedKeys(aino))
        assertEquals(emptyList<String>(), db.profiles().lockedKeys(Profiles.DEFAULT_ID))
    }

    @Test
    fun theSecretStoreWinsOverTheMirroredFlag() = runBlocking {
        prefs.editHousehold { it.copy(pinConfigured = true) }
        store.seed(prefs.household.first())
        store.setLocked("s:c1", true)
        store.reconcilePin()
        assertFalse(store.household.value.pinConfigured)
        assertEquals(emptyList<String>(), db.profiles().lockedKeys(Profiles.DEFAULT_ID))
        secrets.values[ProfileStore.PIN_KEY] = "1234"
        store.reconcilePin()
        assertTrue(store.household.value.pinConfigured)
    }

    @Test
    fun groupChoicesAreOnePerKeyWithTheSourcesThatCarryThem() = runBlocking {
        db.sources().upsert(SourceEntity("a", "Aurora", "M3U", true, 0, 1, "BOTH", 60, 0, 0))
        db.sources().upsert(SourceEntity("b", "Borealis", "M3U", true, 1, 1, "BOTH", 60, 0, 0))
        db.sources().upsert(SourceEntity("c", "Off", "M3U", false, 2, 1, "BOTH", 60, 0, 0))
        db.groupImport().insert(group("a", "name:news", "News", 0))
        db.groupImport().insert(group("b", "name:news", "", 0))
        db.groupImport().insert(group("b", "name:", "", 1))
        db.groupImport().insert(group("c", "name:hidden", "Hidden", 0))
        assertEquals(
            listOf(GroupChoice("name:news", "News", listOf("Aurora", "Borealis")), GroupChoice("name:", null, listOf("Borealis"))),
            store.groupChoices(OrgRoom.LIVE),
        )
    }

    private fun group(source: String, key: String, name: String, order: Int) = ContentGroupEntity(
        sourceId = source, room = "LIVE", groupKey = key, name = name, providerOrder = order, itemCount = 3,
        shown = true, position = order, sortMode = null,
    )
}
