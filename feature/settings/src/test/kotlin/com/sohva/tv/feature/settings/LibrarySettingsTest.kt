package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Library section that remembers what it was asked to do. */
internal class FakeLibrary : LibrarySettingsServices {
    var artworkBytes = 3L * 1024 * 1024
    var limit = com.sohva.tv.core.model.settings.ArtworkCacheLimit.MEDIUM
    override suspend fun artworkLimit() = limit
    override suspend fun setArtworkLimit(limit: com.sohva.tv.core.model.settings.ArtworkCacheLimit) { this.limit = limit }
    override suspend fun artworkUsage(): Long = artworkBytes
    override suspend fun clearArtwork() { artworkBytes = 0 }
    override fun openManager() = Unit

    override fun customGroups(): kotlinx.coroutines.flow.Flow<List<com.sohva.tv.core.model.vod.CustomGroup>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun saveCustomGroup(group: com.sohva.tv.core.model.vod.CustomGroup) = Unit

    override suspend fun deleteCustomGroup(id: String) = Unit

    override suspend fun libraryGenres(): List<com.sohva.tv.core.model.vod.Genre> = emptyList()

    val view = MutableStateFlow(MetadataSettingsView(tmdbSwitch = false, credential = "", tvmaze = false, language = "en-US", keyRefused = false))
    val calls = mutableListOf<String>()
    var testResult: Outcome<Unit> = Outcome.Ok(Unit)

    override fun metadata(): Flow<MetadataSettingsView> = view

    override suspend fun setTmdb(on: Boolean, typed: String): Outcome<Unit> {
        calls += "tmdb:$on:$typed"
        view.update { it.copy(tmdbSwitch = on, credential = typed) }
        return Outcome.Ok(Unit)
    }

    override suspend fun setTvmaze(on: Boolean): Outcome<Unit> {
        calls += "tvmaze:$on"
        view.update { it.copy(tvmaze = on) }
        return Outcome.Ok(Unit)
    }

    override suspend fun saveKey(typed: String): Outcome<Unit> {
        calls += "save:$typed"
        view.update { it.copy(credential = typed.trim(), keyRefused = false) }
        return Outcome.Ok(Unit)
    }

    override suspend fun testTmdb(typed: String): Outcome<Unit> = testResult.also { calls += "test:$typed" }

    override suspend fun setLanguage(tag: String) {
        calls += "language:$tag"
    }

    override suspend fun clearMetadata() {
        calls += "clear"
    }

    override fun preferredCopy(): Flow<PreferredCopy> = MutableStateFlow(PreferredCopy.NONE)

    override suspend fun setPreferredCopy(copy: PreferredCopy) {
        calls += "copy:$copy"
    }

    override fun openWeb(url: String) {
        calls += "web:$url"
    }
}

/** Spec 41 §11 "Settings": the switch, the key, the test and the status line. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySettingsTest {
    private val services = FakeLibrary()
    private val library = LibrarySettings(services, CoroutineScope(UnconfinedTestDispatcher()))

    @Test
    fun tmdbDoesNotTurnOnWithoutAKey() {
        library.toggleTmdb()
        assertEquals(SettingsMessage.Text(R.string.metadata_key_required), library.state.value.status)
        assertTrue(library.state.value.statusIsError)
        assertEquals(emptyList<String>(), services.calls)
        assertFalse(library.state.value.stored!!.tmdbSwitch)
    }

    @Test
    fun theTypedKeyIsSavedAndTheSwitchSavesItToo() {
        library.type("  fictional-key ")
        library.saveKey()
        assertEquals(SettingsMessage.Text(R.string.metadata_saved), library.state.value.status)
        assertEquals("fictional-key", library.state.value.typed)
        library.toggleTmdb()
        assertEquals(listOf("save:  fictional-key ", "tmdb:true:fictional-key"), services.calls)
        assertTrue(library.state.value.stored!!.tmdbSwitch)
    }

    @Test
    fun aFailedTestSaysWhyInTheErrorColour() {
        services.testResult = Outcome.Failed(AppError.MetadataHttp("TMDB", 401))
        library.type("wrong")
        library.testTmdb()
        assertEquals(SettingsMessage.Failure(AppError.MetadataHttp("TMDB", 401)), library.state.value.status)
        assertTrue(library.state.value.statusIsError)
        services.testResult = Outcome.Ok(Unit)
        library.testTmdb()
        assertEquals(SettingsMessage.Text(R.string.metadata_tmdb_test_ok), library.state.value.status)
        assertFalse(library.state.value.statusIsError)
    }

    @Test
    fun typingIsKeptWhenTheStoredSettingsChangeElsewhere() {
        library.type("half typed")
        services.view.update { it.copy(tvmaze = true) }
        assertEquals("half typed", library.state.value.typed)
    }
}
