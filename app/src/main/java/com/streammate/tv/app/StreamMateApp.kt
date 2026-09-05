package com.streammate.tv.app

import com.streammate.tv.feature.today.TodayPollingPolicy
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.session.SessionToken
import androidx.tv.material3.Text
import com.streammate.tv.R
import com.streammate.tv.feature.guide.GuideScreen
import com.streammate.tv.feature.home.HomeScreen
import com.streammate.tv.feature.legal.LegalInformationScreen
import com.streammate.tv.feature.player.PlayerScreen
import com.streammate.tv.feature.catalogue.CatalogueMode
import com.streammate.tv.feature.catalogue.MovieDetailsScreen
import com.streammate.tv.feature.catalogue.SeriesDetailsScreen
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserSession
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserV2
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowseTarget
import com.streammate.tv.feature.search.SearchScreen
import com.streammate.tv.feature.settings.SettingsScreen
import com.streammate.tv.feature.settings.PhoneSetupUiState
import com.streammate.tv.feature.settings.PhoneSetupActions
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.BarcodeFormat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap
import com.streammate.tv.feature.settings.AppUpdateUiState
import com.streammate.tv.feature.settings.AppUpdateActions
import com.streammate.tv.feature.settings.ChannelEditorScreen
import com.streammate.tv.feature.settings.LibraryManagerScreen
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.feature.settings.ParentalPinScreen
import com.streammate.tv.feature.today.TodayScreen
import com.streammate.tv.feature.today.TodayViewModel
import com.streammate.tv.feature.today.sportMateStatusSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.iptv.xtream.derivedXtreamSourceOrNull
import com.streammate.tv.iptv.repository.VodSeries
import com.streammate.tv.iptv.repository.VodMovie

/**
 * Live playback started from the guide, the home rows or a channel number
 * lands back on the guide, on the channel just watched. Playback started from
 * somewhere else - a match card in Sohva Sport - goes back to where it came
 * from, and catch-up always does.
 */
internal fun shouldReturnPlaybackToGuide(
    catchupStartEpochMillis: Long?,
    catchupStopEpochMillis: Long?,
    launchedForGuide: Boolean = true,
): Boolean = launchedForGuide && catchupStartEpochMillis == null && catchupStopEpochMillis == null

@Composable
fun StreamMateApp(container: StreamMateContainer) {
    var backStack by remember { mutableStateOf(listOf<Destination>(Destination.Home)) }
    // Screens leave the composition while the player is up. The sport screen
    // keeps the match card it had open in saveable state, so it needs a holder
    // to come back to the same card rather than the day's list.
    val saveableStateHolder = rememberSaveableStateHolder()
    var startupApplied by remember { mutableStateOf(false) }
    var guideManagementReturn by remember { mutableStateOf(false) }
    var guideManagedGroup by remember { mutableStateOf<String?>(null) }
    var guideFocusChannelId by remember { mutableStateOf<String?>(null) }
    // The channel watched before the one playing now, for zap-back. Session
    // only; the persisted last channel stands in until there is one.
    var previousChannelId by remember { mutableStateOf<String?>(null) }
    val movieCatalogueSession = remember { CatalogueBrowserSession(CatalogueMode.MOVIES) }
    val seriesCatalogueSession = remember { CatalogueBrowserSession(CatalogueMode.SERIES) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val genericChannelName = stringResource(R.string.generic_channel)
    val playbackSessionToken = remember(context) {
        SessionToken(context, ComponentName(context, StreamMatePlaybackService::class.java))
    }
    val destination = backStack.last()
    val appPreferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
    )
    LaunchedEffect(container) {
        val preferences = container.preferencesRepository.preferences.first()
        val parentalPinConfigured = container.secretSettingsStore.hasParentalPin()
        if (preferences.parentalPinConfigured != parentalPinConfigured) {
            container.preferencesRepository.setParentalPinConfigured(parentalPinConfigured)
        }
        val lockedChannelIds = if (parentalPinConfigured) preferences.lockedChannelIds else emptySet()
        backStack = when (preferences.startupScreen) {
            StartupScreen.HOME -> listOf(Destination.Home)
            StartupScreen.GUIDE -> listOf(Destination.Home, Destination.Guide)
            StartupScreen.LAST_CHANNEL -> {
                val channel = preferences.lastChannelId
                    ?.let { container.guideRepository.activeChannel(it) }
                if (channel == null) {
                    listOf(Destination.Home, Destination.Guide)
                } else if (channel.channelId in lockedChannelIds) {
                    guideFocusChannelId = channel.channelId
                    listOf(
                        Destination.Home,
                        Destination.Guide,
                        Destination.PinGate(channel.channelId, channel.name, replacePlayer = false),
                    )
                } else {
                    guideFocusChannelId = channel.channelId
                    listOf(Destination.Home, Destination.Guide, Destination.Player(channel.channelId))
                }
            }
        }
        startupApplied = true
    }
    LaunchedEffect(container, context) {
        if (container.demoMode) return@LaunchedEffect
        container.preferencesRepository.preferences
            .map { it.playlistEpgRefreshInterval }
            .distinctUntilChanged()
            .collect { interval -> GuideRefreshScheduler.schedule(context, interval) }
    }
    // Once a day, from app start, ask the release list whether a newer beta
    // exists; nothing is downloaded until the tester presses the button.
    LaunchedEffect(container) {
        if (!container.demoMode) container.appUpdateChecker.checkIfDue()
    }
    val appUpdateState by container.appUpdateChecker.state.collectAsStateWithLifecycle()
    val phoneSetupState by container.phoneSetupServer.state.collectAsStateWithLifecycle()
    // The phone page lives only while Settings is open.
    LaunchedEffect(destination) {
        if (destination != Destination.Settings) container.phoneSetupServer.stop()
    }
    val phoneSetupQr = remember(phoneSetupState) {
        (phoneSetupState as? PhoneSetupState.Running)?.let { running -> qrCodeBitmap(running.url) }
    }
    LaunchedEffect(container) {
        container.preferencesRepository.preferences
            .map { it.metadataLanguage }
            .distinctUntilChanged()
            .collect { language -> container.metadataRepository.defaultLanguage = language }
    }
    fun navigateTo(next: Destination) {
        if (next != destination) backStack = backStack + next
    }
    fun navigateBack() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    fun rememberPreviousChannel(nextChannelId: String) {
        val playing = (destination as? Destination.Player)?.channelId
        val candidate = playing ?: appPreferences.lastChannelId ?: previousChannelId
        if (candidate != null && candidate != nextChannelId) previousChannelId = candidate
    }
    fun playChannel(channelId: String, rememberForGuide: Boolean = true) {
        if (rememberForGuide) guideFocusChannelId = channelId
        rememberPreviousChannel(channelId)
        coroutineScope.launch {
            if (channelId in appPreferences.lockedChannelIds) {
                val channelName = container.guideRepository.activeChannel(channelId)?.name ?: genericChannelName
                navigateTo(
                    Destination.PinGate(
                        channelId = channelId,
                        channelName = channelName,
                        replacePlayer = false,
                        rememberForGuide = rememberForGuide,
                    ),
                )
            } else {
                if (rememberForGuide) container.preferencesRepository.recordRecentChannel(channelId)
                navigateTo(Destination.Player(channelId, returnToGuide = rememberForGuide))
            }
        }
    }
    fun playCatchup(channelId: String, startEpochMillis: Long, stopEpochMillis: Long) {
        guideFocusChannelId = channelId
        rememberPreviousChannel(channelId)
        coroutineScope.launch {
            if (channelId in appPreferences.lockedChannelIds) {
                val channelName = container.guideRepository.activeChannel(channelId)?.name ?: genericChannelName
                navigateTo(
                    Destination.PinGate(
                        channelId = channelId,
                        channelName = channelName,
                        replacePlayer = false,
                        catchupStartEpochMillis = startEpochMillis,
                        catchupStopEpochMillis = stopEpochMillis,
                    ),
                )
            } else {
                container.preferencesRepository.recordRecentChannel(channelId)
                navigateTo(Destination.Player(channelId, startEpochMillis, stopEpochMillis))
            }
        }
    }
    fun zapToChannel(channelId: String) {
        guideFocusChannelId = channelId
        rememberPreviousChannel(channelId)
        coroutineScope.launch {
            if (channelId in appPreferences.lockedChannelIds) {
                val channelName = container.guideRepository.activeChannel(channelId)?.name ?: genericChannelName
                backStack = backStack + Destination.PinGate(channelId, channelName, replacePlayer = true)
            } else {
                container.preferencesRepository.recordRecentChannel(channelId)
                backStack = backStack.dropLast(1) + Destination.Player(channelId)
            }
        }
    }
    fun playVod(contentKey: String, resumePositionMillis: Long) {
        navigateTo(Destination.VodPlayer(contentKey, resumePositionMillis))
    }
    fun playVodFromHome(contentKey: String, resumePositionMillis: Long) {
        coroutineScope.launch {
            val detailRoute = container.catalogueRepository.movie(contentKey)?.let { movie ->
                listOf<Destination>(
                    Destination.Catalogue(CatalogueMode.MOVIES),
                    Destination.MovieDetails(movie),
                )
            } ?: container.catalogueRepository.seriesForEpisode(contentKey)?.let { series ->
                listOf<Destination>(
                    Destination.Catalogue(CatalogueMode.SERIES),
                    Destination.SeriesDetails(series),
                )
            }.orEmpty()
            if (backStack.lastOrNull() == Destination.Home) {
                backStack = backStack + detailRoute + Destination.VodPlayer(
                    contentKey,
                    resumePositionMillis,
                )
            }
        }
    }
    fun continueToNextEpisode(player: Destination.VodPlayer) {
        if (!appPreferences.autoPlayNextEpisodeEnabled) return
        coroutineScope.launch {
            val next = container.catalogueRepository.nextEpisode(player.contentKey) ?: return@launch
            if (backStack.lastOrNull() == player) {
                backStack = backStack.dropLast(1) + Destination.VodPlayer(
                    contentKey = next.contentKey,
                    resumePositionMillis = 0L,
                )
            }
        }
    }
    suspend fun refreshCatalogues(): Result<String> = try {
        if (container.refreshDemoContent()) {
            return Result.success(resources.getString(R.string.catalogue_demo_refreshed))
        }
        val sources = container.secretSettingsStore.loadSources()
            .filter { it.enabled && it.importScope.importsVod }
        require(sources.isNotEmpty()) { resources.getString(R.string.catalogue_add_xtream_first) }
        var movieCount = 0
        var seriesCount = 0
        sources.forEach { source ->
            container.guideRepository.upsertSourceState(source)
            val result = when (source.type) {
                IptvSourceType.M3U -> source.derivedXtreamSourceOrNull()
                    ?.let { container.xtreamCatalogueImportService.refresh(it) }
                    ?: container.m3uCatalogueImportService.refresh(source)
                IptvSourceType.XTREAM -> container.xtreamCatalogueImportService.refresh(source)
            }
            movieCount += result.movies
            seriesCount += result.series
        }
        CatalogueMetadataScheduler.restart(context)
        val movies = resources.getQuantityString(
            R.plurals.catalogue_imported_movies,
            movieCount,
            movieCount,
        )
        val series = resources.getQuantityString(
            R.plurals.catalogue_imported_series,
            seriesCount,
            seriesCount,
        )
        Result.success(resources.getString(R.string.catalogue_imported, movies, series))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
    val todayViewModel: TodayViewModel = viewModel(
        factory = remember(container) {
            TodayViewModel.factory(
                repository = container.sportsRepository,
                matchingRepository = container.eventChannelMatchingRepository,
                preferencesRepository = container.preferencesRepository,
            )
        },
    )
    val todayUiState by todayViewModel.uiState.collectAsStateWithLifecycle()
    // Polling has to stop when nobody is looking, not just when navigation has
    // moved on. Leaving the app for the launcher, or handing a stream to an
    // external player, both leave the sports screen as the current destination
    // while the daily API-Sports allowance quietly drains.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(destination, lifecycleState, todayViewModel) {
        todayViewModel.setAutoRefreshEnabled(
            TodayPollingPolicy.shouldPoll(
                onSportsScreen = destination == Destination.Today,
                appInForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
            ),
        )
    }
    fun handleBack() {
        if (destination == Destination.Settings) todayViewModel.refresh()
        navigateBack()
    }
    fun handlePlayerBack(player: Destination.Player) {
        if (
            shouldReturnPlaybackToGuide(
                player.catchupStartEpochMillis,
                player.catchupStopEpochMillis,
                launchedForGuide = player.returnToGuide,
            )
        ) {
            guideFocusChannelId = player.channelId
            backStack = listOf(Destination.Home, Destination.Guide)
        } else {
            handleBack()
        }
    }
    BackHandler(enabled = backStack.size > 1, onBack = ::handleBack)
    StreamMateTheme {
        val palette = StreamMateThemeTokens.palette
        if (!startupApplied) {
            Box(
                modifier = Modifier.fillMaxSize().background(palette.backgroundBottom),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.app_name))
            }
        } else when (val current = destination) {
            Destination.Home -> HomeScreen(
                guideRepository = container.guideRepository,
                catalogueRepository = container.catalogueRepository,
                preferencesRepository = container.preferencesRepository,
                // The same list SearchScreen already reads. Home only shows
                // what is there; it starts no fetch of its own.
                sportsEvents = todayUiState.events,
                onLiveTv = { navigateTo(Destination.Guide) },
                onSportMate = { navigateTo(Destination.Today) },
                onMovies = { navigateTo(Destination.Catalogue(CatalogueMode.MOVIES)) },
                onSeries = { navigateTo(Destination.Catalogue(CatalogueMode.SERIES)) },
                onSearch = { navigateTo(Destination.Search) },
                onSettings = { navigateTo(Destination.Settings) },
                onPlayChannel = ::playChannel,
                onPlayVod = ::playVodFromHome,
            )
            Destination.Search -> SearchScreen(
                guideRepository = container.guideRepository,
                catalogueRepository = container.catalogueRepository,
                sportsEvents = todayUiState.events,
                onPlayChannel = ::playChannel,
                onPlayVod = ::playVod,
                onOpenMovie = { navigateTo(Destination.MovieDetails(it)) },
                onOpenSeries = { navigateTo(Destination.SeriesDetails(it)) },
                onSportMate = { navigateTo(Destination.Today) },
                onBack = ::handleBack,
            )
            is Destination.Catalogue -> {
                CatalogueBrowserV2(
                    mode = current.mode,
                    repository = container.catalogueRepository,
                    preferredCopy = appPreferences.preferredCatalogueCopy,
                    customGroups = appPreferences.customCatalogueGroups,
                    onManageGroups = { group ->
                        navigateTo(Destination.LibraryManager(
                            if (current.mode == CatalogueMode.MOVIES) LibraryRoom.MOVIES else LibraryRoom.SERIES,
                            group,
                        ))
                    },
                    onRefresh = ::refreshCatalogues,
                    session = if (current.mode == CatalogueMode.MOVIES) {
                        movieCatalogueSession
                    } else {
                        seriesCatalogueSession
                    },
                    onOpenEntry = { entry ->
                        coroutineScope.launch {
                            when (val target = entry.target) {
                                is CatalogueBrowseTarget.Movie -> container.catalogueRepository
                                    .movie(entry.contentKey)
                                    ?.let { navigateTo(Destination.MovieDetails(it)) }
                                is CatalogueBrowseTarget.Series -> container.catalogueRepository
                                    .series(target.sourceId, target.seriesId)
                                    ?.let { navigateTo(Destination.SeriesDetails(it)) }
                            }
                        }
                    },
                    onBack = ::handleBack,
                )
            }
            is Destination.MovieDetails -> MovieDetailsScreen(
                movie = current.movie,
                repository = container.catalogueRepository,
                metadataRepository = container.metadataRepository,
                onPlay = ::playVod,
                onOpenMovie = { navigateTo(Destination.MovieDetails(it)) },
                onBack = ::handleBack,
            )
            is Destination.SeriesDetails -> SeriesDetailsScreen(
                series = current.series,
                repository = container.catalogueRepository,
                metadataRepository = container.metadataRepository,
                onRefreshEpisodes = {
                    val source = container.secretSettingsStore.loadSources()
                        .firstOrNull { it.id == current.series.sourceId && it.enabled }
                    if (source == null) {
                        Result.failure(
                            IllegalStateException(resources.getString(R.string.catalogue_source_disabled)),
                        )
                    } else if (source.type == IptvSourceType.M3U) {
                        val derived = source.derivedXtreamSourceOrNull()
                        if (derived == null) {
                            Result.success(0)
                        } else {
                            try {
                                Result.success(
                                    container.xtreamCatalogueImportService.refreshEpisodes(
                                        derived,
                                        current.series.seriesId,
                                    ),
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        }
                    } else {
                        try {
                            Result.success(
                                container.xtreamCatalogueImportService.refreshEpisodes(
                                    source,
                                    current.series.seriesId,
                                ),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Result.failure(error)
                        }
                    }
                },
                onPlay = ::playVod,
                onBack = ::handleBack,
            )
            Destination.Today -> saveableStateHolder.SaveableStateProvider(key = "today") {
                TodayScreen(
                    uiState = todayUiState,
                    onRefresh = todayViewModel::refresh,
                    onLoadDetails = { eventId -> todayViewModel.loadEventDetails(eventId) },
                    onRefreshDetails = { eventId -> todayViewModel.loadEventDetails(eventId, force = true) },
                    onMatchDecision = todayViewModel::setMatchDecision,
                    onGuide = { navigateTo(Destination.Guide) },
                    onSettings = { navigateTo(Destination.Settings) },
                    // Not for the guide: back from the stream returns here,
                    // to the match card it was chosen from.
                    onPlay = { channelId ->
                        playChannel(channelId, rememberForGuide = false)
                    },
                )
            }
            Destination.Guide -> GuideScreen(
                guideRepository = container.guideRepository,
                preferencesRepository = container.preferencesRepository,
                metadataRepository = container.metadataRepository,
                initialChannelId = guideFocusChannelId,
                startInOptions = guideManagementReturn,
                initialManagedGroup = guideManagedGroup.takeIf { guideManagementReturn },
                onManagementReturnHandled = { guideManagementReturn = false },
                onBack = ::handleBack,
                onSettings = { navigateTo(Destination.Settings) },
                onChannels = { navigateTo(Destination.ChannelEditor) },
                onManageGroups = { group, source -> guideManagementReturn = true; guideManagedGroup = group; navigateTo(Destination.LibraryManager(LibraryRoom.LIVE, group, source)) },
                onPlay = { channelId ->
                    playChannel(channelId)
                },
                onPlayCatchup = ::playCatchup,
                onSyncNow = { GuideRefreshScheduler.syncNow(context) },
            )
            Destination.Settings -> SettingsScreen(
                secretSettingsStore = container.secretSettingsStore,
                guideImportService = container.guideImportService,
                m3uCatalogueImportService = container.m3uCatalogueImportService,
                xtreamImportService = container.xtreamImportService,
                xtreamCatalogueImportService = container.xtreamCatalogueImportService,
                metadataRepository = container.metadataRepository,
                guideRepository = container.guideRepository,
                preferencesRepository = container.preferencesRepository,
                sportStatusText = sportMateStatusSummary(todayUiState, appPreferences.timeZoneId),
                loadSportsCompetitions = { sport ->
                    try {
                        Result.success(container.sportsRepository.competitions(sport))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                },
                onExportBackup = { uri, passphrase ->
                    runBackupOperation { container.backupManager.write(uri, passphrase) }
                },
                onRestoreBackup = { uri, passphrase ->
                    runBackupOperation { container.backupManager.restore(uri, passphrase) }
                },
                onLegalInformation = { navigateTo(Destination.LegalInformation) },
                onSyncNow = { sourceId -> GuideRefreshScheduler.syncNow(context, sourceId) },
                onMetadataLanguageChanged = {
                    coroutineScope.launch {
                        container.metadataRepository.resetCatalogueEnrichment()
                        CatalogueMetadataScheduler.restart(context)
                    }
                },
                phoneSetup = phoneSetupState.toUiState(phoneSetupQr),
                phoneSetupActions = PhoneSetupActions(
                    onStart = { container.phoneSetupServer.start() },
                    onStop = { container.phoneSetupServer.stop() },
                ),
                appUpdate = appUpdateState.toUiState(container.appUpdateChecker.installedVersionName),
                appUpdateActions = AppUpdateActions(
                    onCheck = { coroutineScope.launch { container.appUpdateChecker.check() } },
                    onDownload = {
                        (appUpdateState as? AppUpdateState.Available)?.let { available ->
                            coroutineScope.launch { container.appUpdateChecker.download(available.update) }
                        }
                    },
                    onInstall = {
                        when (val current = appUpdateState) {
                            is AppUpdateState.Downloaded -> container.appUpdateChecker.install(current.update, current.file)
                            is AppUpdateState.NeedsInstallPermission -> container.appUpdateChecker.retryInstall()
                            else -> Unit
                        }
                    },
                    onOpenInstallPermission = { container.appUpdateChecker.openInstallPermissionSettings() },
                ),
                onManageLibrary = { navigateTo(Destination.LibraryManager(LibraryRoom.LIVE)) },
                onBack = ::handleBack,
            )
            Destination.LegalInformation -> LegalInformationScreen(onBack = ::handleBack)
            is Destination.LibraryManager -> LibraryManagerScreen(
                repository = container.organizationRepository,
                guideRepository = container.guideRepository,
                initialRoom = current.room,
                initialGroup = current.group,
                initialSource = current.source,
                onAdvanced = { navigateTo(Destination.ChannelEditor) },
                onBack = ::handleBack,
            )
            Destination.ChannelEditor -> ChannelEditorScreen(
                guideRepository = container.guideRepository,
                preferencesRepository = container.preferencesRepository,
                onBack = ::handleBack,
            )
            is Destination.PinGate -> ParentalPinScreen(
                channelName = current.channelName,
                pinConfigured = container.secretSettingsStore.hasParentalPin(),
                onVerify = container.secretSettingsStore::verifyParentalPin,
                onUnlocked = {
                    coroutineScope.launch {
                        if (current.rememberForGuide) {
                            guideFocusChannelId = current.channelId
                            container.preferencesRepository.recordRecentChannel(current.channelId)
                        }
                        val player = Destination.Player(
                            current.channelId,
                            current.catchupStartEpochMillis,
                            current.catchupStopEpochMillis,
                            returnToGuide = current.rememberForGuide,
                        )
                        backStack = if (current.replacePlayer) {
                            backStack.dropLast(2) + player
                        } else {
                            backStack.dropLast(1) + player
                        }
                    }
                },
                onBack = ::handleBack,
            )
            is Destination.Player -> PlayerScreen(
                channelId = current.channelId,
                catchupStartEpochMillis = current.catchupStartEpochMillis,
                catchupStopEpochMillis = current.catchupStopEpochMillis,
                sessionToken = playbackSessionToken,
                guideRepository = container.guideRepository,
                metadataRepository = container.metadataRepository,
                timeZoneId = appPreferences.timeZoneId,
                remoteMappings = appPreferences.remoteMappings,
                playbackReconnectPolicy = appPreferences.playbackReconnectPolicy,
                autoFrameRateEnabled = appPreferences.autoFrameRateEnabled,
                onChannelChange = ::zapToChannel,
                onSwitchToPreviousChannel = previousChannelId
                    ?.takeIf { it != current.channelId }
                    ?.let { previous -> { zapToChannel(previous) } },
                onOpenGuideAtChannel = {
                    guideFocusChannelId = current.channelId
                    backStack = listOf(Destination.Home, Destination.Guide)
                },
                onGoHome = { backStack = listOf(Destination.Home) },
                onGoGuide = { backStack = listOf(Destination.Home, Destination.Guide) },
                onGoSport = { backStack = listOf(Destination.Home, Destination.Today) },
                onOpenExternal = { channelId ->
                    container.externalPlayerLauncher.launch(channelId).fold(
                        onSuccess = { Result.success(Unit) },
                        onFailure = { Result.failure(it) },
                    )
                },
                onBack = { handlePlayerBack(current) },
                previewArtworkUrl = container.demoPlaybackArtworkUrl,
            )
            is Destination.VodPlayer -> PlayerScreen(
                channelId = current.contentKey,
                vodContentKey = current.contentKey,
                resumePositionMillis = current.resumePositionMillis,
                sessionToken = playbackSessionToken,
                guideRepository = container.guideRepository,
                metadataRepository = container.metadataRepository,
                timeZoneId = appPreferences.timeZoneId,
                remoteMappings = appPreferences.remoteMappings,
                playbackReconnectPolicy = appPreferences.playbackReconnectPolicy,
                autoFrameRateEnabled = appPreferences.autoFrameRateEnabled,
                onGoHome = { backStack = listOf(Destination.Home) },
                onGoGuide = { backStack = listOf(Destination.Home, Destination.Guide) },
                onGoSport = { backStack = listOf(Destination.Home, Destination.Today) },
                preferredAudioLanguage = appPreferences.preferredAudioLanguage,
                secondaryAudioLanguage = appPreferences.secondaryAudioLanguage,
                preferredSubtitleLanguage = appPreferences.preferredSubtitleLanguage,
                secondarySubtitleLanguage = appPreferences.secondarySubtitleLanguage,
                onChannelChange = {},
                onOpenExternal = {
                    Result.failure(
                        IllegalStateException(resources.getString(R.string.recording_external_unavailable)),
                    )
                },
                onBack = ::handleBack,
                onPlaybackEnded = { continueToNextEpisode(current) },
                previewArtworkUrl = container.demoPlaybackArtworkUrl,
            )
        }
    }
}

private sealed interface Destination {
    data object Home : Destination
    data object Today : Destination
    data object Guide : Destination
    data object Settings : Destination
    data object LegalInformation : Destination
    data object ChannelEditor : Destination
    data class LibraryManager(val room: LibraryRoom, val group: String? = null, val source: String? = null) : Destination
    data object Search : Destination
    data class Catalogue(val mode: CatalogueMode) : Destination
    data class MovieDetails(val movie: VodMovie) : Destination
    data class SeriesDetails(val series: VodSeries) : Destination
    data class PinGate(
        val channelId: String,
        val channelName: String,
        val replacePlayer: Boolean,
        val rememberForGuide: Boolean = true,
        val catchupStartEpochMillis: Long? = null,
        val catchupStopEpochMillis: Long? = null,
    ) : Destination
    data class Player(
        val channelId: String,
        val catchupStartEpochMillis: Long? = null,
        val catchupStopEpochMillis: Long? = null,
        /** False when playback began somewhere the guide had no part in, such as a match card. */
        val returnToGuide: Boolean = true,
    ) : Destination
    data class VodPlayer(val contentKey: String, val resumePositionMillis: Long) : Destination
}

private suspend fun runBackupOperation(operation: suspend () -> Unit): Result<Unit> = try {
    operation()
    Result.success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

/** The checker's state in the words the settings screen understands. */
internal fun AppUpdateState.toUiState(installedVersionName: String): AppUpdateUiState = when (this) {
    AppUpdateState.Idle -> AppUpdateUiState(AppUpdateUiState.Phase.IDLE, installedVersionName)
    AppUpdateState.Checking -> AppUpdateUiState(AppUpdateUiState.Phase.CHECKING, installedVersionName)
    AppUpdateState.UpToDate -> AppUpdateUiState(AppUpdateUiState.Phase.UP_TO_DATE, installedVersionName)
    is AppUpdateState.Available -> AppUpdateUiState(
        AppUpdateUiState.Phase.AVAILABLE, installedVersionName, update.versionName, update.notes,
    )
    is AppUpdateState.Downloading -> AppUpdateUiState(
        AppUpdateUiState.Phase.DOWNLOADING, installedVersionName, update.versionName, update.notes, percent,
    )
    is AppUpdateState.Downloaded -> AppUpdateUiState(
        AppUpdateUiState.Phase.DOWNLOADED, installedVersionName, update.versionName, update.notes,
    )
    is AppUpdateState.NeedsInstallPermission -> AppUpdateUiState(
        AppUpdateUiState.Phase.NEEDS_PERMISSION, installedVersionName, update.versionName, update.notes,
    )
    is AppUpdateState.Failed -> AppUpdateUiState(
        AppUpdateUiState.Phase.FAILED, installedVersionName, update?.versionName, update?.notes,
        failure = when (reason) {
            AppUpdateFailure.NETWORK -> AppUpdateUiState.Failure.NETWORK
            AppUpdateFailure.NO_CHECKSUMS -> AppUpdateUiState.Failure.NO_CHECKSUMS
            AppUpdateFailure.CHECKSUM_MISMATCH -> AppUpdateUiState.Failure.CHECKSUM_MISMATCH
            AppUpdateFailure.INSTALL_BLOCKED -> AppUpdateUiState.Failure.INSTALL_BLOCKED
        },
    )
}

internal fun PhoneSetupState.toUiState(qrCode: ImageBitmap?): PhoneSetupUiState = when (this) {
    PhoneSetupState.Stopped -> PhoneSetupUiState()
    PhoneSetupState.NoNetwork -> PhoneSetupUiState(noNetwork = true)
    is PhoneSetupState.Running -> PhoneSetupUiState(
        running = true,
        url = url,
        qrCode = qrCode,
        receivedCount = receivedCount,
        lastSourceName = lastSourceName,
    )
}

/** The setup address as a QR code the phone camera reads across the room. */
internal fun qrCodeBitmap(text: String, size: Int = 512): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index ->
        if (matrix.get(index % size, index / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
