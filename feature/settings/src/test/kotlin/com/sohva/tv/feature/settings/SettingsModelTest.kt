package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.RefreshState
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.sync.SourceChecks
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsModelTest {
    private class FakeProfiles : ProfileSettingsServices {
        override val household = MutableStateFlow(com.sohva.tv.core.model.profile.Household())
        override suspend fun add(name: String): Boolean = false
        override suspend fun remove(profileId: String) = Unit
        override fun switchTo(profileId: String) = Unit
        override suspend fun setAskAtStart(on: Boolean) = Unit
        override suspend fun restriction(profileId: String) = com.sohva.tv.core.model.profile.Restriction.NONE
        override suspend fun anyRestricted(): Boolean = false
        override suspend fun groupChoices(room: com.sohva.tv.core.model.org.OrgRoom) = emptyList<com.sohva.tv.core.data.profile.GroupChoice>()
        override suspend fun setAllowed(profileId: String, room: com.sohva.tv.core.model.org.OrgRoom, groupKey: String, allowed: Boolean) = Unit
        override suspend fun setPin(pin: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun removePin(current: String): Boolean = false
        override suspend fun changePin(current: String, next: String): Outcome<Boolean> = Outcome.Ok(false)
    }

    private class FakeServices : SettingsServices {
        override val library: LibrarySettingsServices = FakeLibrary()
        override val profiles: ProfileSettingsServices = FakeProfiles()
        override val general: GeneralSettingsServices = object : GeneralSettingsServices {
            override suspend fun languageTag(): String? = null
            override fun applyLanguage(tag: String?) = Unit
            override fun scale() = flowOf(com.sohva.tv.core.model.settings.InterfaceScale.NORMAL)
            override suspend fun setScale(value: com.sohva.tv.core.model.settings.InterfaceScale) = Unit
            override fun theme() = flowOf(com.sohva.tv.core.model.settings.ColorThemeId.ORIGINAL)
            override suspend fun setTheme(value: com.sohva.tv.core.model.settings.ColorThemeId) = Unit
            override fun channelNumbers() = flowOf(true)
            override suspend fun setChannelNumbers(on: Boolean) = Unit
            override fun timeZone() = flowOf<String?>(null)
            override suspend fun setTimeZone(id: String?) = Unit
            override fun deviceZone() = com.sohva.tv.core.model.settings.ZoneRow("UTC", "UTC", "UTC", "UTC")
            override suspend fun zones() = emptyList<com.sohva.tv.core.model.settings.ZoneRow>()
            override fun startupScreen() = flowOf(com.sohva.tv.core.model.settings.StartupScreen.HOME)
            override suspend fun setStartupScreen(value: com.sohva.tv.core.model.settings.StartupScreen) = Unit
        }
        override val playback: PlaybackSettingsServices = object : PlaybackSettingsServices {
            override fun settings() = flowOf(com.sohva.tv.core.model.player.PlaybackSettings())
            override fun autoPlayNext() = flowOf(true)
            override suspend fun setBuffer(value: com.sohva.tv.core.model.player.BufferProfile) = Unit
            override suspend fun setReconnect(value: com.sohva.tv.core.model.player.ReconnectPolicy) = Unit
            override suspend fun setSkipStep(value: com.sohva.tv.core.model.player.SkipStep) = Unit
            override suspend fun setMatchFrameRate(on: Boolean) = Unit
            override suspend fun setAutoPlayNext(on: Boolean) = Unit
            override suspend fun setPictureInPicture(on: Boolean) = Unit
            override suspend fun setSubtitleSize(value: com.sohva.tv.core.model.player.SubtitleSize) = Unit
            override suspend fun setSubtitleColor(value: com.sohva.tv.core.model.player.SubtitleColor) = Unit
            override suspend fun setSubtitleBackground(value: com.sohva.tv.core.model.player.SubtitleBackground) = Unit
            override suspend fun setVodLanguage(slot: com.sohva.tv.core.model.settings.VodLanguageSlot, code: String?) = Unit
        }

        val sources = MutableStateFlow<List<Source>>(emptyList())
        val health = MutableStateFlow<List<SourceHealth>>(emptyList())
        val interval = MutableStateFlow(RefreshInterval.TWENTY_FOUR_HOURS)
        val saved = HashMap<String, SourceConfig>()
        val synced = ArrayList<String>()
        val removed = ArrayList<String>()
        var refreshResult: SourceHealth? = null
        var playlistResult: SourceChecks.Result = SourceChecks.Result.Playlist(0, false)
        var films = 0 to 0

        override fun sources(): Flow<List<Source>> = sources
        override suspend fun importProblem(): AppError? = null
        override fun health(): Flow<List<SourceHealth>> = health
        override fun refreshInterval(): Flow<RefreshInterval> = interval
        override suspend fun load(sourceId: String): Outcome<SourceConfig?> = Outcome.Ok(saved[sourceId])
        override suspend fun save(config: SourceConfig): Outcome<SourceConfig> = when (val r = SourceRules.validate(config)) {
            is SourceRules.Result.Invalid -> Outcome.Failed(r.error)
            is SourceRules.Result.Valid -> {
                saved[r.config.source.id] = r.config
                sources.value = saved.values.map { it.source }
                Outcome.Ok(r.config)
            }
        }
        override suspend fun remove(sourceId: String): Outcome<Unit> {
            removed += sourceId
            saved.remove(sourceId)
            sources.value = saved.values.map { it.source }
            return Outcome.Ok(Unit)
        }
        override fun syncNow(sourceId: String) {
            synced += sourceId
        }
        override suspend fun refresh(sourceId: String, kind: RefreshKind): SourceHealth? = refreshResult
        override suspend fun catalogueCounts(sourceId: String): Pair<Int, Int> = films
        override suspend fun testPlaylist(address: String): SourceChecks.Result = playlistResult
        override suspend fun testXtream(account: XtreamAccount): SourceChecks.Result = SourceChecks.Result.Account(4)
        override fun remindersCanOpen(): Boolean? = null

        override fun openOverlaySettings(): Boolean = true

        override fun remoteMapping(): Flow<RemoteMapping> = flowOf(RemoteMapping.DEFAULTS)

        override suspend fun setRemoteAction(button: RemoteButton, gesture: Gesture, action: RemoteAction) = Unit

        override suspend fun resetRemoteMapping() = Unit

        override suspend fun setRefreshInterval(interval: RefreshInterval) {
            this.interval.value = interval
        }
        val phone = MutableStateFlow<PhoneSetupState>(PhoneSetupState.Closed)
        override fun phoneSetup(): Flow<PhoneSetupState> = phone
        override fun openPhoneSetup() {
            phone.value = PhoneSetupState.Open("http://192.0.2.5:4321/#token", 0, null, false)
        }
        override fun closePhoneSetup() {
            phone.value = PhoneSetupState.Closed
        }
        override suspend fun qrCode(url: String): QrMatrix? = QrMatrix(1, booleanArrayOf(true))
    }

    private val services = FakeServices()
    private lateinit var model: SettingsModel

    @Before
    fun start() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        model = SettingsModel(services, accounts = false)
    }

    @After
    fun stop() = Dispatchers.resetMain()

    private val state get() = model.ui.value
    private val status get() = state.messages[SettingsSection.SOURCES]

    private fun newM3u(url: String = "http://provider.example/list.m3u"): SourceDraft {
        model.addSource(SourceType.M3U)
        model.edit { it.copy(m3uUrl = url) }
        return state.page!!
    }

    @Test
    fun newPagesGetDefaultNamesPerType() {
        assertEquals("IPTV 1", newM3u().name)
        model.save()
        model.closePage()
        model.addSource(SourceType.M3U)
        assertEquals("IPTV 2", state.page!!.name)
        model.addSource(SourceType.XTREAM)
        assertEquals("Xtream 1", state.page!!.name)
        assertEquals(SettingsMessage.Text(R.string.source_new_xtream), status)
    }

    @Test
    fun savingANewSourceSyncsItAndARenameAloneDoesNot() {
        val page = newM3u()
        model.save()
        assertEquals(listOf(page.id), services.synced)
        assertEquals(SettingsMessage.Text(R.string.source_saved_syncing), status)
        assertFalse(state.page!!.isNew)

        model.edit { it.copy(name = "Renamed") }
        model.save()
        assertEquals(1, services.synced.size)
        assertEquals(SettingsMessage.Text(R.string.source_saved), status)

        model.edit { it.copy(scope = ImportScope.LIVE_TV) }
        model.save()
        assertEquals("a changed scope syncs (plan/09 M1)", 2, services.synced.size)
    }

    @Test
    fun anInvalidPageSavesAndSyncsNothing() {
        newM3u(url = "provider.example/list.m3u")
        model.save()
        assertEquals(SettingsMessage.Failure(AppError.SourceUrlInvalid(AppError.FieldLabel.M3U)), status)
        assertTrue(services.saved.isEmpty())
        assertTrue(services.synced.isEmpty())
        assertTrue(state.page!!.isNew)
    }

    @Test
    fun testingNeverSaves() {
        newM3u()
        services.playlistResult = SourceChecks.Result.Playlist(42, more = false)
        model.test()
        assertEquals(SettingsMessage.Count(R.plurals.source_test_m3u_ok, 42), status)
        services.playlistResult = SourceChecks.Result.Playlist(500, more = true)
        model.test()
        assertEquals(SettingsMessage.Text(R.string.source_test_m3u_ok_more, listOf(500)), status)
        services.playlistResult = SourceChecks.Result.Failed(AppError.PlaylistNotM3u)
        model.test()
        assertEquals(SettingsMessage.Failure(AppError.PlaylistNotM3u), status)
        assertTrue(services.saved.isEmpty())

        model.addSource(SourceType.XTREAM)
        model.edit { it.copy(server = "http://panel.example", username = "u", password = "p") }
        model.test()
        assertEquals(SettingsMessage.ConnectionOk(4), status)
        assertTrue(services.saved.isEmpty())
    }

    @Test
    fun refreshReportsTheStoredResult() {
        val page = newM3u()
        services.refreshResult = SourceHealth(page.id, RefreshKind.PLAYLIST, RefreshState.SUCCESS, null, 56_164, 0)
        model.refresh(RefreshKind.PLAYLIST)
        assertEquals(SettingsMessage.Count(R.plurals.source_imported_channels, 56_164), status)
        services.refreshResult = SourceHealth(page.id, RefreshKind.EPG, RefreshState.FAILED, AppError.EpgUnmatched, 10, 1)
        model.refresh(RefreshKind.EPG)
        assertEquals(SettingsMessage.Failure(AppError.EpgUnmatched), status)
        services.refreshResult = SourceHealth(page.id, RefreshKind.CATALOGUE, RefreshState.SUCCESS, null, 3, 0)
        services.films = 2 to 1
        model.refresh(RefreshKind.CATALOGUE)
        assertEquals(SettingsMessage.Catalogue(2, 1), status)
        assertFalse(state.busy)
    }

    @Test
    fun removingAsksFirstThenReturnsToTheList() {
        val page = newM3u()
        model.save()
        model.askDelete()
        assertTrue(state.confirmingDelete)
        model.cancelDelete()
        assertFalse(state.confirmingDelete)
        assertTrue(services.removed.isEmpty())
        model.askDelete()
        model.confirmDelete()
        assertEquals(listOf(page.id), services.removed)
        assertNull(state.page)
        assertEquals(SettingsMessage.Text(R.string.source_deleted), status)
        assertEquals(FocusTarget.SectionStart, state.focus.target)
    }

    @Test
    fun closingAPageFocusesItsRowAndRowsCarryTheFirstFailure() {
        val page = newM3u()
        model.save()
        services.health.value = listOf(
            SourceHealth(page.id, RefreshKind.CATALOGUE, RefreshState.FAILED, AppError.CatalogueEmpty, 0, 1),
            SourceHealth(page.id, RefreshKind.EPG, RefreshState.FAILED, AppError.EpgEmpty, 0, 1),
            SourceHealth(page.id, RefreshKind.PLAYLIST, RefreshState.SUCCESS, null, 5, 0),
        )
        model.closePage()
        assertEquals(FocusTarget.SourceRowOf(page.id), state.focus.target)
        assertEquals(AppError.EpgEmpty, state.sources!!.single().failure)
    }

    @Test
    fun aPhoneReceiptShowsInThePlaylistsStatusLine() {
        model.openPhoneSetup()
        assertTrue(state.phone is PhoneSetupState.Open)
        assertEquals(1, state.qr?.size)
        services.phone.value = PhoneSetupState.Open("http://192.0.2.5:4321/#token", 1, "Living room", false)
        assertEquals(SettingsMessage.Text(R.string.phone_setup_received, listOf("Living room")), status)
        services.phone.value = PhoneSetupState.Open("http://192.0.2.5:4321/#token", 2, "Living room", true)
        assertEquals(SettingsMessage.Text(R.string.phone_setup_received_keys), status)
        model.closePhoneSetup()
        assertEquals(PhoneSetupState.Closed, state.phone)
        assertNull(state.qr)
    }

    @Test
    fun choosingARailSectionClosesThePageAndTheIntervalReportsInGeneral() {
        newM3u()
        model.select(SettingsSection.GENERAL)
        assertNull(state.page)
        assertEquals(SettingsSection.GENERAL, state.section)
        model.setRefreshInterval(RefreshInterval.FOUR_HOURS)
        assertEquals(RefreshInterval.FOUR_HOURS, state.refreshInterval)
        assertEquals(SettingsMessage.IntervalSaved(RefreshInterval.FOUR_HOURS), state.messages[SettingsSection.GENERAL])
        assertEquals("Playlists keeps its own line", SettingsMessage.Text(R.string.source_new_m3u), state.messages[SettingsSection.SOURCES])
    }
}
