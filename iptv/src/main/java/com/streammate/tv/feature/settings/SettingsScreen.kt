package com.streammate.tv.feature.settings

import com.streammate.tv.app.Profiles
import com.streammate.tv.app.SubtitleBackground
import com.streammate.tv.app.SubtitleTextColor
import com.streammate.tv.app.SubtitleTextSize
import com.streammate.tv.app.PlaybackSeekStep
import android.content.res.Resources
import com.streammate.tv.app.MetadataLanguages
import androidx.compose.ui.focus.onFocusChanged
import com.streammate.tv.app.RemoteAction
import com.streammate.tv.app.RemoteActionGroup
import com.streammate.tv.app.RemoteActionScope
import com.streammate.tv.app.RemoteButton
import com.streammate.tv.app.RemoteGesture
import com.streammate.tv.app.RemoteMappings
import com.streammate.tv.app.RemoteSlot
import com.streammate.tv.app.ArtworkCacheSettings
import com.streammate.tv.app.ArtworkCacheLimit
import com.streammate.tv.core.R as CoreR
import com.streammate.tv.core.error.userMessage
import android.net.Uri
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streammate.tv.iptv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AppPreferencesRepository
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.app.PlaybackBufferProfile
import com.streammate.tv.app.PlaybackReconnectPolicy
import com.streammate.tv.app.PlaylistEpgRefreshInterval
import com.streammate.tv.app.AppLocale
import com.streammate.tv.app.InterfaceScale
import com.streammate.tv.app.deviceTimeZoneId
import com.streammate.tv.app.PreferredLanguageSlot
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.app.StartupScreen
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.core.model.SportType
import com.streammate.tv.core.model.SportsCompetition
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.core.security.MetadataSettings
import com.streammate.tv.core.security.SportsApiSettings
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvIcons
import com.streammate.tv.feature.common.TvListRow
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import com.streammate.tv.iptv.repository.GuideImportService
import com.streammate.tv.iptv.repository.GuideImportException
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.SourceRefreshHealth
import com.streammate.tv.iptv.repository.M3uCatalogueImportService
import com.streammate.tv.iptv.repository.XtreamImportService
import com.streammate.tv.iptv.repository.XtreamCatalogueImportService
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.xtream.derivedXtreamSourceOrNull
import java.util.UUID
import kotlinx.coroutines.launch

private class SettingsColumnScope {
    @Composable
    fun item(content: @Composable () -> Unit) {
        content()
    }

    @Composable
    fun <T> items(
        entries: List<T>,
        key: ((T) -> Any)? = null,
        itemContent: @Composable (T) -> Unit,
    ) {
        entries.forEach { entry ->
            if (key == null) {
                itemContent(entry)
            } else {
                key(key(entry)) { itemContent(entry) }
            }
        }
    }
}

@Composable
private fun SettingsColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable SettingsColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = remember { SettingsColumnScope() }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        scope.content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Where the string files live; a native reader can correct a phrase there. */
private const val TRANSLATIONS_URL = "https://github.com/Macstered/Sohva-TV"

@Composable
fun SettingsScreen(
    secretSettingsStore: SecretSettingsStore,
    guideImportService: GuideImportService,
    m3uCatalogueImportService: M3uCatalogueImportService,
    xtreamImportService: XtreamImportService,
    xtreamCatalogueImportService: XtreamCatalogueImportService,
    metadataRepository: MetadataRepository,
    guideRepository: GuideRepository,
    preferencesRepository: AppPreferencesRepository,
    sportStatusText: String,
    loadSportsCompetitions: suspend (SportType) -> Result<List<SportsCompetition>>,
    onExportBackup: suspend (Uri, String) -> Result<Unit>,
    onRestoreBackup: suspend (Uri, String) -> Result<Unit>,
    onLegalInformation: () -> Unit,
    /** Starts a full background sync, channels then guide then catalogue, for one source. */
    onSyncNow: (String) -> Unit = {},
    /** After the metadata language changed and was stored: clear derived titles and re-run the worker. */
    onMetadataLanguageChanged: () -> Unit = {},
    appUpdate: AppUpdateUiState = AppUpdateUiState(),
    appUpdateActions: AppUpdateActions = AppUpdateActions(),
    phoneSetup: PhoneSetupUiState = PhoneSetupUiState(),
    phoneSetupActions: PhoneSetupActions = PhoneSetupActions(),
    onBack: () -> Unit,
    onManageLibrary: (() -> Unit)? = null,
    /** Writes the redacted diagnostics file to the location the tester chose. */
    onSaveDiagnostics: (suspend (Uri) -> Result<Unit>)? = null,
    /** Whether a due reminder may bring the app forward over another app; null where that is not offered. */
    reminderOpenAllowed: Boolean? = null,
    onOpenReminderSettings: () -> Unit = {},
    /** After a profile is removed: whatever else it owned, such as watched positions, goes too. */
    onProfileRemoved: suspend (String) -> Unit = {},
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val palette = StreamMateThemeTokens.palette
    val initialSources = remember { secretSettingsStore.loadSources() }
    val initialMetadataSettings = remember { secretSettingsStore.loadMetadataSettings() }
    val initialSportsApiSettings = remember { secretSettingsStore.loadSportsApiSettings() }
    var sources by remember { mutableStateOf(initialSources) }
    var selectedSourceId by remember {
        mutableStateOf(
            initialSources.firstOrNull { it.type == IptvSourceType.M3U }?.id ?: newM3uSourceId(),
        )
    }
    val selectedSource = sources.firstOrNull { it.id == selectedSourceId }
    val sourceType = selectedSource?.type ?: if (selectedSourceId.startsWith("xtream-")) {
        IptvSourceType.XTREAM
    } else {
        IptvSourceType.M3U
    }
    var sourceName by remember(selectedSourceId) {
        val typeName = if (sourceType == IptvSourceType.M3U) "IPTV" else "Xtream"
        val typeCount = sources.count { it.type == sourceType } + 1
        mutableStateOf(selectedSource?.name ?: "$typeName $typeCount")
    }
    var sourceEnabled by remember(selectedSourceId) { mutableStateOf(selectedSource?.enabled ?: true) }
    var importScope by remember(selectedSourceId) {
        mutableStateOf(selectedSource?.importScope ?: IptvImportScope.BOTH)
    }
    var epgOffsetMinutes by remember(selectedSourceId) {
        mutableIntStateOf(
            selectedSource?.epgOffsetMinutes
                ?: IptvSourceConfiguration.DEFAULT_EPG_OFFSET_MINUTES,
        )
    }
    var connectionLimit by remember(selectedSourceId) {
        mutableIntStateOf(selectedSource?.connectionLimit ?: IptvSourceConfiguration.DEFAULT_CONNECTION_LIMIT)
    }
    var m3uUrl by remember(selectedSourceId) { mutableStateOf(selectedSource?.m3uUrl.orEmpty()) }
    var xmlTvUrl by remember(selectedSourceId) { mutableStateOf(selectedSource?.xmlTvUrl.orEmpty()) }
    var xtreamBaseUrl by remember(selectedSourceId) { mutableStateOf(selectedSource?.xtreamBaseUrl.orEmpty()) }
    var xtreamUsername by remember(selectedSourceId) { mutableStateOf(selectedSource?.xtreamUsername.orEmpty()) }
    var xtreamPassword by remember(selectedSourceId) { mutableStateOf(selectedSource?.xtreamPassword.orEmpty()) }
    val initialStatus = if (initialSources.isEmpty()) {
        stringResource(R.string.settings_add_first_source)
    } else {
        stringResource(R.string.settings_sources_loaded)
    }
    var status by remember(initialStatus) {
        mutableStateOf(initialStatus)
    }
    var busy by remember { mutableStateOf(false) }
    var parentalPin by remember { mutableStateOf("") }
    var backupPassphrase by remember { mutableStateOf("") }
    var tmdbEnabled by remember { mutableStateOf(initialMetadataSettings.tmdbEnabled) }
    var tmdbToken by remember { mutableStateOf(initialMetadataSettings.tmdbReadAccessToken) }
    var tvmazeEnabled by remember { mutableStateOf(initialMetadataSettings.tvmazeEnabled) }
    var metadataStatus by remember { mutableStateOf<String?>(null) }
    var sportsApiKey by remember { mutableStateOf(initialSportsApiSettings.apiKey) }
    var sportsKeyConfigured by remember { mutableStateOf(initialSportsApiSettings.apiKey.isNotBlank()) }
    var sportsFollowMenuOpen by remember { mutableStateOf(false) }
    var selectedFollowSport by remember { mutableStateOf(SportType.FOOTBALL) }
    var competitionCatalogues by remember {
        mutableStateOf<Map<SportType, List<SportsCompetition>>>(emptyMap())
    }
    var competitionsLoading by remember { mutableStateOf(false) }
    var competitionLoadError by remember { mutableStateOf<String?>(null) }
    var competitionLoadGeneration by remember { mutableIntStateOf(0) }
    var competitionQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf(SettingsSection.SOURCES) }
    var timeZonePickerOpen by remember { mutableStateOf(false) }
    var maintenanceStatus by remember { mutableStateOf("") }
    // A source is a page: the Playlists section lists them, and opening one
    // shows its form and actions alone. Back returns to the row it came from.
    var sourcePageOpen by remember { mutableStateOf(false) }
    val sourceRowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val sourcePageFocus = remember { FocusRequester() }
    LaunchedEffect(sourcePageOpen, selectedSourceId) {
        if (sourcePageOpen) sourcePageFocus.requestFocusWhenAttached()
    }
    // One picker at a time, over the page; the row that opened it gets focus back.
    var openPicker by remember { mutableStateOf<SettingsPickerTarget?>(null) }
    val pickerRowFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun rowFocus(key: String): FocusRequester = pickerRowFocus.getOrPut(key) { FocusRequester() }
    var interfaceLanguage by remember { mutableStateOf(AppLocale.stored(context)) }
    // A source that arrived from the phone page was saved outside this
    // screen's own state; pick it up and show it in the list.
    LaunchedEffect(phoneSetup.receivedCount) {
        if (phoneSetup.receivedCount > 0) {
            sources = secretSettingsStore.loadSources()
            status = phoneSetup.lastSourceName?.let { resources.getString(R.string.phone_setup_received, it) }
                ?: resources.getString(R.string.phone_setup_received_keys)
        }
    }
    var sectionFocusGeneration by remember { mutableIntStateOf(0) }
    val sectionFocusRequesters = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    // The first row of a section is also where the section's focus lands.
    remember {
        pickerRowFocus[SettingsPickerTarget.InterfaceLanguage.key] = sectionFocusRequesters.getValue(SettingsSection.GENERAL)
        pickerRowFocus[SettingsPickerTarget.Buffer.key] = sectionFocusRequesters.getValue(SettingsSection.PLAYBACK)
        true
    }
    val sportsFollowFocusRequester = remember { FocusRequester() }
    val sourceHealth by guideRepository.observeSourceRefreshHealth()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val appPreferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = com.streammate.tv.app.AppPreferences(),
    )
    val scope = rememberCoroutineScope()
    var editedGroup by remember { mutableStateOf<CatalogueCustomGroup?>(null) }
    val libraryGenres by remember(metadataRepository) {
        metadataRepository.observeCatalogueGenres()
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val uriHandler = LocalUriHandler.current

    fun saveMetadataSettings(clearCache: Boolean) {
        scope.launch {
            busy = true
            val result = runCatching {
                secretSettingsStore.saveMetadataSettings(
                    MetadataSettings(
                        tmdbEnabled = tmdbEnabled,
                        tmdbReadAccessToken = tmdbToken,
                        tvmazeEnabled = tvmazeEnabled,
                    ),
                )
                if (clearCache) metadataRepository.clearCache()
                resources.getString(R.string.metadata_saved)
            }.getOrElse { it.userMessage(context) }
            metadataStatus = result
            status = result
            busy = false
        }
    }

    val metadataDisplayLocale = LocalResources.current.configuration.locales[0]
    val metadataLanguageOptions = remember(metadataDisplayLocale) {
        MetadataLanguages.TAGS.map { tag ->
            val name = java.util.Locale.forLanguageTag(tag).getDisplayName(metadataDisplayLocale)
                .replaceFirstChar { it.titlecase(metadataDisplayLocale) }
            tag to name
        }
    }
    val languageOptions = preferredLanguageOptions()
    val interfaceLanguageChoices = interfaceLanguageOptions()
    val profileDefaultName = stringResource(R.string.profile_default_name)
    var newProfileName by remember { mutableStateOf("") }
    val interfaceScaleChoices = interfaceScaleOptions()
    fun closePicker(target: SettingsPickerTarget) {
        openPicker = null
        scope.launch { rowFocus(target.key).requestFocusWhenAttached() }
    }
    fun intervalLabel(interval: PlaylistEpgRefreshInterval): String =
        resources.getQuantityString(R.plurals.source_refresh_interval_hours, interval.hours.toInt(), interval.hours.toInt())
    openPicker?.let { target ->
        when (target) {
            SettingsPickerTarget.InterfaceLanguage -> SettingsPickerDialog(
                title = stringResource(R.string.interface_language_title),
                options = interfaceLanguageChoices.map { (tag, label) ->
                    SettingsPickerOption(tag, label, testTag = "settings-interface-language-${tag ?: "system"}")
                },
                selected = interfaceLanguage,
                onSelect = { tag ->
                    closePicker(target)
                    interfaceLanguage = tag
                    // Only the pre-33 path needs this; from Tiramisu the framework restarts us.
                    if (AppLocale.apply(context, tag)) context.findActivity()?.recreate()
                },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.InterfaceScale -> SettingsPickerDialog(
                title = stringResource(R.string.interface_scale_title),
                options = interfaceScaleChoices.map { (scale, label) ->
                    SettingsPickerOption(scale, label, testTag = "settings-interface-scale-${scale.name.lowercase()}")
                },
                selected = appPreferences.interfaceScale,
                onSelect = { scale -> closePicker(target); scope.launch { preferencesRepository.setInterfaceScale(scale) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.Startup -> SettingsPickerDialog(
                title = stringResource(R.string.startup_title),
                options = StartupScreen.entries.map { screen ->
                    SettingsPickerOption(
                        screen, screen.localizedLabel(),
                        description = if (screen == StartupScreen.LAST_CHANNEL) stringResource(R.string.startup_last_channel_help) else null,
                        testTag = "settings-startup-${screen.name.lowercase()}",
                    )
                },
                selected = appPreferences.startupScreen,
                onSelect = { screen -> closePicker(target); scope.launch { preferencesRepository.setStartupScreen(screen) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.RefreshInterval -> SettingsPickerDialog(
                title = stringResource(R.string.source_refresh_schedule),
                options = PlaylistEpgRefreshInterval.entries.map { interval ->
                    SettingsPickerOption(interval, intervalLabel(interval), testTag = "settings-refresh-interval-${interval.hours}")
                },
                selected = appPreferences.playlistEpgRefreshInterval,
                onSelect = { interval ->
                    closePicker(target)
                    scope.launch {
                        preferencesRepository.setPlaylistEpgRefreshInterval(interval)
                        status = resources.getString(R.string.source_refresh_schedule_saved, intervalLabel(interval))
                    }
                },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.Buffer -> SettingsPickerDialog(
                title = stringResource(R.string.playback_buffer_title),
                options = PlaybackBufferProfile.entries.map { profile ->
                    SettingsPickerOption(
                        profile, profile.localizedLabel(), description = profile.localizedHelp(),
                        testTag = "settings-buffer-${profile.name.lowercase()}",
                    )
                },
                selected = appPreferences.playbackBufferProfile,
                onSelect = { profile -> closePicker(target); scope.launch { preferencesRepository.setPlaybackBufferProfile(profile) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.SeekStep -> SettingsPickerDialog(
                title = stringResource(R.string.playback_seek_step_title),
                options = PlaybackSeekStep.entries.map { step ->
                    SettingsPickerOption(step, step.localizedLabel(), testTag = "settings-seek-step-${step.name.lowercase()}")
                },
                selected = appPreferences.playbackSeekStep,
                onSelect = { step -> closePicker(target); scope.launch { preferencesRepository.setPlaybackSeekStep(step) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.SubtitleSize -> SettingsPickerDialog(
                title = stringResource(R.string.subtitle_size_title),
                options = SubtitleTextSize.entries.map { size ->
                    SettingsPickerOption(size, size.localizedLabel(), testTag = "settings-subtitle-size-${size.name.lowercase()}")
                },
                selected = appPreferences.subtitleTextSize,
                onSelect = { size -> closePicker(target); scope.launch { preferencesRepository.setSubtitleTextSize(size) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.SubtitleColor -> SettingsPickerDialog(
                title = stringResource(R.string.subtitle_color_title),
                options = SubtitleTextColor.entries.map { color ->
                    SettingsPickerOption(color, color.localizedLabel(), testTag = "settings-subtitle-color-${color.name.lowercase()}")
                },
                selected = appPreferences.subtitleTextColor,
                onSelect = { color -> closePicker(target); scope.launch { preferencesRepository.setSubtitleTextColor(color) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.SubtitleBackgroundChoice -> SettingsPickerDialog(
                title = stringResource(R.string.subtitle_background_title),
                options = SubtitleBackground.entries.map { background ->
                    SettingsPickerOption(background, background.localizedLabel(), testTag = "settings-subtitle-background-${background.name.lowercase()}")
                },
                selected = appPreferences.subtitleBackground,
                onSelect = { background -> closePicker(target); scope.launch { preferencesRepository.setSubtitleBackground(background) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.ProfileChoice -> SettingsPickerDialog(
                title = stringResource(R.string.profile_active_title),
                options = Profiles.withDefault(appPreferences.profiles, profileDefaultName).map { profile ->
                    SettingsPickerOption(profile.id, profile.name.ifBlank { profileDefaultName }, testTag = "settings-profile-${profile.id}")
                },
                selected = appPreferences.activeProfileId,
                onSelect = { id -> closePicker(target); scope.launch { preferencesRepository.setActiveProfile(id) } },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.ProfileRemove -> SettingsPickerDialog(
                title = stringResource(R.string.profile_remove),
                options = appPreferences.profiles.filter { it.id != Profiles.DEFAULT_ID }.map { profile ->
                    SettingsPickerOption(profile.id, profile.name, testTag = "settings-profile-remove-${profile.id}")
                },
                selected = "",
                onSelect = { id ->
                    closePicker(target)
                    scope.launch {
                        preferencesRepository.removeProfile(id)
                        onProfileRemoved(id)
                    }
                },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.Reconnect -> SettingsPickerDialog(
                title = stringResource(R.string.playback_reconnect_title),
                options = PlaybackReconnectPolicy.entries.map { policy ->
                    SettingsPickerOption(
                        policy, policy.localizedLabel(), description = policy.localizedHelp(),
                        testTag = "settings-reconnect-${policy.name.lowercase()}",
                    )
                },
                selected = appPreferences.playbackReconnectPolicy,
                onSelect = { policy -> closePicker(target); scope.launch { preferencesRepository.setPlaybackReconnectPolicy(policy) } },
                onDismiss = { closePicker(target) },
            )
            is SettingsPickerTarget.Language -> SettingsPickerDialog(
                title = stringResource(target.slot.labelResource()),
                options = languageOptions.mapIndexed { index, (code, label) ->
                    SettingsPickerOption(code, label, testTag = "settings-${target.key}-option-$index")
                },
                selected = appPreferences.languageFor(target.slot),
                onSelect = { code ->
                    closePicker(target)
                    scope.launch {
                        preferencesRepository.setPreferredLanguage(target.slot, code)
                        val duplicateSlot = target.slot.pairedSlot()
                        if (code != null && code == appPreferences.languageFor(duplicateSlot)) {
                            preferencesRepository.setPreferredLanguage(duplicateSlot, null)
                        }
                    }
                },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.MetadataLanguage -> SettingsPickerDialog(
                title = stringResource(R.string.metadata_language_title),
                options = metadataLanguageOptions.map { (tag, name) ->
                    SettingsPickerOption(tag, name, testTag = "settings-metadata-language-$tag")
                },
                selected = appPreferences.metadataLanguage,
                onSelect = { tag ->
                    closePicker(target)
                    if (tag != appPreferences.metadataLanguage) {
                        scope.launch {
                            preferencesRepository.setMetadataLanguage(tag)
                            onMetadataLanguageChanged()
                        }
                    }
                },
                onDismiss = { closePicker(target) },
            )
            SettingsPickerTarget.PreferredCopy -> SettingsPickerDialog(
                title = stringResource(R.string.preferred_copy_title),
                options = CataloguePreferredCopy.entries.map { preferred ->
                    SettingsPickerOption(preferred, preferred.localizedLabel(), testTag = "settings-preferred-copy-${preferred.name.lowercase()}")
                },
                selected = appPreferences.preferredCatalogueCopy,
                onSelect = { preferred -> closePicker(target); scope.launch { preferencesRepository.setPreferredCatalogueCopy(preferred) } },
                onDismiss = { closePicker(target) },
            )
        }
    }
    fun closeSourcePage() {
        sourcePageOpen = false
        val target = sourceRowFocus[selectedSourceId] ?: sectionFocusRequesters.getValue(SettingsSection.SOURCES)
        scope.launch { target.requestFocusWhenAttached() }
    }
    BackHandler(enabled = sourcePageOpen && selectedSection == SettingsSection.SOURCES) { closeSourcePage() }
    LaunchedEffect(selectedSection, sectionFocusGeneration, sportsFollowMenuOpen) {
        val requester = if (selectedSection == SettingsSection.SPORT && sportsFollowMenuOpen) {
            sportsFollowFocusRequester
        } else {
            sectionFocusRequesters.getValue(selectedSection)
        }
        requester.requestFocus()
    }
    LaunchedEffect(
        sportsFollowMenuOpen,
        sportsKeyConfigured,
        selectedFollowSport,
        competitionLoadGeneration,
    ) {
        if (
            sportsFollowMenuOpen &&
            sportsKeyConfigured &&
            competitionCatalogues[selectedFollowSport] == null
        ) {
            competitionsLoading = true
            competitionLoadError = null
            loadSportsCompetitions(selectedFollowSport)
                .onSuccess { competitions ->
                    competitionCatalogues = competitionCatalogues + (selectedFollowSport to competitions)
                }
                .onFailure { error ->
                    competitionLoadError = error.userMessage(context)
                }
            competitionsLoading = false
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = onExportBackup(uri, backupPassphrase).fold(
                    onSuccess = { resources.getString(R.string.settings_backup_saved) },
                    onFailure = { it.userMessage(context) },
                )
                backupPassphrase = ""
                busy = false
            }
        }
    }
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }
    val saveDiagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val save = onSaveDiagnostics
        if (uri != null && save != null) {
            scope.launch {
                diagnosticsStatus = save(uri).fold(
                    onSuccess = { resources.getString(R.string.diagnostics_saved) },
                    onFailure = { it.userMessage(context) },
                )
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = onRestoreBackup(uri, backupPassphrase).fold(
                    onSuccess = {
                        sources = secretSettingsStore.loadSources()
                        selectedSourceId = sources.firstOrNull()?.id ?: newM3uSourceId()
                        resources.getString(R.string.settings_backup_restored)
                    },
                    onFailure = { it.userMessage(context) },
                )
                backupPassphrase = ""
                busy = false
            }
        }
    }

    fun validatedSource(): Result<IptvSourceConfiguration> = runCatching {
            require(sourceName.isNotBlank()) { resources.getString(R.string.settings_source_name_required) }
            val source = (selectedSource ?: IptvSourceConfiguration(
                id = selectedSourceId,
                name = sourceName.trim(),
                type = sourceType,
            )).copy(
                name = sourceName.trim(),
                enabled = sourceEnabled,
                connectionLimit = connectionLimit,
                importScope = importScope,
                epgOffsetMinutes = epgOffsetMinutes,
            )
            when (sourceType) {
                IptvSourceType.M3U -> {
                    source.copy(
                        m3uUrl = IptvConfigurationValidator.validateM3uUrl(m3uUrl),
                        xmlTvUrl = IptvConfigurationValidator.validateOptionalXmlTvUrl(xmlTvUrl),
                    )
                }
                IptvSourceType.XTREAM -> {
                    val configuration = XtreamConfigurationValidator.validate(
                        xtreamBaseUrl,
                        xtreamUsername,
                        xtreamPassword,
                    ).getOrThrow()
                    source.copy(
                        xtreamBaseUrl = configuration.baseUrl,
                        xtreamUsername = configuration.username,
                        xtreamPassword = configuration.password,
                    )
                }
            }
        }

    /** Saves [source]; true when it was new to this device. */
    suspend fun persistSource(source: IptvSourceConfiguration): Boolean {
        val existed = sources.any { it.id == source.id }
        secretSettingsStore.upsertSource(source)
        guideRepository.upsertSourceState(source)
        sources = if (existed) {
            sources.map { if (it.id == source.id) source else it }
        } else {
            sources + source
        }
        return !existed
    }

    val selectedHealth = sourceHealth.filter { it.sourceId == selectedSourceId }
    val healthSummary = selectedHealth.joinToString(" · ") { health ->
        val kind = when (health.kind) {
            "playlist" -> resources.getString(R.string.health_playlist)
            "catalogue" -> resources.getString(R.string.health_catalogue)
            else -> resources.getString(R.string.health_epg)
        }
        when (health.status) {
            "success" -> resources.getQuantityString(
                R.plurals.health_success,
                health.itemCount,
                kind,
                health.itemCount,
            )
            // The reason, when the import left one: "error (1)" told a tester
            // nothing about a catalogue the provider had just refused.
            "failed" -> readableImportError(resources, health.lastError)
                ?.let { resources.getString(R.string.health_failed_detail, kind, it) }
                ?: resources.getString(R.string.health_failed, kind, health.consecutiveFailures)
            else -> resources.getString(R.string.health_updating, kind)
        }
    }

    editedGroup?.let { group ->
        CustomGroupEditor(
            group = group.takeIf { it.id.isNotBlank() },
            // Only what the library actually holds. Offering a genre that
            // would return nothing wastes the one thing a remote is short of.
            availableGenres = remember(libraryGenres) {
                libraryGenres.values.flatten().distinct().sorted()
            },
            onSave = { saved -> scope.launch { preferencesRepository.saveCustomCatalogueGroup(saved) } },
            onDelete = group.id.takeIf(String::isNotBlank)?.let {
                { id: String -> scope.launch { preferencesRepository.deleteCustomCatalogueGroup(id) } }
            },
            onDismiss = { editedGroup = null },
        )
    }
    StreamMateScreenBackground { contentModifier ->
        Column(modifier = contentModifier) {
            // Title and the section being looked at, nothing else. The
            // security note that used to sit under the title has moved to the
            // footer beside the credential controls it is actually about.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = palette.textPrimary,
                    fontSize = StreamMateThemeTokens.typography.display.fontSize,
                    lineHeight = StreamMateThemeTokens.typography.display.lineHeight,
                    fontWeight = FontWeight.Black,
                    letterSpacing = StreamMateThemeTokens.typography.display.letterSpacing,
                )
                Text(
                    text = SETTINGS_BREADCRUMB_SEPARATOR + selectedSection.localizedLabel().uppercase(),
                    modifier = Modifier
                        .padding(start = 14.dp, bottom = 6.dp)
                        .weight(1f)
                        .testTag("settings-breadcrumb"),
                    color = palette.textDim,
                    fontSize = StreamMateThemeTokens.typography.label.fontSize,
                    lineHeight = StreamMateThemeTokens.typography.label.lineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = StreamMateThemeTokens.typography.overline.letterSpacing,
                    maxLines = 1,
                )
                TvActionButton(
                    label = stringResource(R.string.action_back),
                    icon = TvIcons.Back,
                    onClick = onBack,
                    modifier = Modifier.padding(bottom = 4.dp),
                    testTag = "settings-back",
                    compact = true,
                )
            }
            Spacer(Modifier.height(SETTINGS_HEADER_GAP))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SETTINGS_CONTENT_GAP),
            ) {
                SettingsSectionRail(
                    modifier = Modifier.width(SETTINGS_SIDEBAR_WIDTH).fillMaxHeight(),
                    selected = selectedSection,
                    onSelected = { section ->
                        openPicker = null
                        sourcePageOpen = false
                        selectedSection = section
                        sectionFocusGeneration += 1
                    },
                )
                SettingsColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRestorer()
                        .focusGroup()
                        .testTag("settings-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 14.dp),
                ) {
            if (selectedSection == SettingsSection.GENERAL) {
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = stringResource(R.string.interface_language_title),
                        subtitle = stringResource(R.string.interface_language_help),
                        value = interfaceLanguageChoices.firstOrNull { it.first == interfaceLanguage }?.second.orEmpty(),
                        icon = TvIcons.Info,
                        onClick = { openPicker = SettingsPickerTarget.InterfaceLanguage },
                        focusRequester = rowFocus(SettingsPickerTarget.InterfaceLanguage.key),
                        divider = false,
                        testTag = "settings-interface-language",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.interface_scale_title),
                        subtitle = stringResource(R.string.interface_scale_help),
                        value = interfaceScaleChoices.firstOrNull { it.first == appPreferences.interfaceScale }?.second.orEmpty(),
                        icon = TvIcons.Aspect,
                        onClick = { openPicker = SettingsPickerTarget.InterfaceScale },
                        focusRequester = rowFocus(SettingsPickerTarget.InterfaceScale.key),
                        testTag = "settings-interface-scale",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.sports_timezone_title),
                        subtitle = stringResource(R.string.sports_timezone_help),
                        value = if (appPreferences.timeZoneFollowsDevice) {
                            stringResource(R.string.sports_timezone_device, timeZoneLabel(appPreferences.timeZoneId))
                        } else {
                            timeZoneLabel(appPreferences.timeZoneId)
                        },
                        icon = TvIcons.Epg,
                        onClick = { timeZonePickerOpen = true },
                        focusRequester = rowFocus("time-zone"),
                        testTag = "settings-time-zone",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.startup_title),
                        value = appPreferences.startupScreen.localizedLabel(),
                        icon = TvIcons.Home,
                        onClick = { openPicker = SettingsPickerTarget.Startup },
                        focusRequester = rowFocus(SettingsPickerTarget.Startup.key),
                        testTag = "settings-startup",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.source_refresh_schedule),
                        subtitle = stringResource(R.string.source_refresh_schedule_help),
                        value = intervalLabel(appPreferences.playlistEpgRefreshInterval),
                        icon = TvIcons.Refresh,
                        onClick = { openPicker = SettingsPickerTarget.RefreshInterval },
                        focusRequester = rowFocus(SettingsPickerTarget.RefreshInterval.key),
                        testTag = "settings-refresh-interval",
                    )
                    if (reminderOpenAllowed != null) {
                        SettingsValueRow(
                            title = stringResource(R.string.reminders_open_title),
                            subtitle = stringResource(R.string.reminders_open_help),
                            value = stringResource(if (reminderOpenAllowed) R.string.reminders_open_allowed else R.string.reminders_open_not_allowed),
                            icon = TvIcons.Info,
                            onClick = onOpenReminderSettings,
                            focusRequester = rowFocus("reminders-open"),
                            testTag = "settings-reminders-open",
                        )
                    }
                }
                if (timeZonePickerOpen) {
                    TimeZonePickerDialog(
                        currentZoneId = appPreferences.timeZoneId,
                        followsDevice = appPreferences.timeZoneFollowsDevice,
                        onFollowDevice = {
                            timeZonePickerOpen = false
                            scope.launch { rowFocus("time-zone").requestFocusWhenAttached() }
                            scope.launch { preferencesRepository.followDeviceTimeZone() }
                        },
                        onSelect = { id ->
                            timeZonePickerOpen = false
                            scope.launch { rowFocus("time-zone").requestFocusWhenAttached() }
                            scope.launch { preferencesRepository.setTimeZone(id) }
                        },
                        onDismiss = {
                            timeZonePickerOpen = false
                            scope.launch { rowFocus("time-zone").requestFocusWhenAttached() }
                        },
                    )
                }
            }
            // Who is watching. The first viewer exists whether named or not;
            // adding a second one turns on the question at start.
            item {
                val profileCount = Profiles.withDefault(appPreferences.profiles, profileDefaultName).size
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.profiles_title))
                    SettingsValueRow(
                        title = stringResource(R.string.profile_active_title),
                        subtitle = stringResource(R.string.profile_active_help),
                        value = Profiles.displayName(appPreferences.profiles, appPreferences.activeProfileId, profileDefaultName),
                        icon = TvIcons.Star,
                        onClick = { openPicker = SettingsPickerTarget.ProfileChoice },
                        focusRequester = rowFocus(SettingsPickerTarget.ProfileChoice.key),
                        divider = false,
                        testTag = "settings-profile-active",
                    )
                    if (profileCount > 1) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.profile_ask_at_start),
                            subtitle = stringResource(R.string.profile_ask_at_start_help),
                            checked = appPreferences.askProfileAtStart,
                            onCheckedChange = { ask -> scope.launch { preferencesRepository.setAskProfileAtStart(ask) } },
                            testTag = "settings-profile-ask",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SETTINGS_ROW_PADDING, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvUrlField(
                            value = newProfileName,
                            onValueChange = { newProfileName = it.take(Profiles.MAX_NAME_LENGTH) },
                            label = stringResource(R.string.profile_name_hint),
                            keyboardType = KeyboardType.Text,
                            editOnClickOnly = true,
                            compact = true,
                            modifier = Modifier.weight(1f),
                            testTag = "settings-profile-name",
                        )
                        TvActionButton(
                            label = stringResource(R.string.profile_add),
                            icon = TvIcons.Check,
                            compact = true,
                            enabled = newProfileName.isNotBlank() && profileCount < Profiles.MAX_PROFILES,
                            onClick = {
                                val name = newProfileName
                                newProfileName = ""
                                scope.launch { preferencesRepository.addProfile(name, colorIndex = profileCount % Profiles.COLOR_COUNT) }
                            },
                            testTag = "settings-profile-add",
                        )
                    }
                    if (profileCount > 1) {
                        SettingsValueRow(
                            title = stringResource(R.string.profile_remove),
                            subtitle = stringResource(R.string.profile_remove_help),
                            value = "",
                            icon = TvIcons.Delete,
                            onClick = { openPicker = SettingsPickerTarget.ProfileRemove },
                            focusRequester = rowFocus(SettingsPickerTarget.ProfileRemove.key),
                            testTag = "settings-profile-remove",
                        )
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.METADATA) {
            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsGroupHeading(stringResource(R.string.metadata_title))
                        AsyncImage(
                            model = "file:///android_asset/tmdb_attribution.svg",
                            contentDescription = "TMDB",
                            modifier = Modifier.width(137.dp).height(18.dp),
                        )
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.metadata_tmdb_switch),
                        subtitle = stringResource(R.string.metadata_description),
                        checked = tmdbEnabled,
                        enabled = !busy,
                        onCheckedChange = { on ->
                            if (on && tmdbToken.isBlank()) {
                                metadataStatus = resources.getString(R.string.metadata_key_required)
                            } else {
                                tmdbEnabled = on
                                saveMetadataSettings(clearCache = false)
                            }
                        },
                        icon = TvIcons.Star,
                        divider = false,
                        focusRequester = sectionFocusRequesters.getValue(SettingsSection.METADATA),
                        testTag = "settings-metadata-tmdb-enabled",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SETTINGS_ROW_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvUrlField(
                            value = tmdbToken,
                            onValueChange = { tmdbToken = it.take(MAX_METADATA_TOKEN_LENGTH) },
                            label = stringResource(R.string.metadata_tmdb_token),
                            modifier = Modifier.weight(1f),
                            testTag = "settings-metadata-tmdb-token",
                            leadingIconRes = TvIcons.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            editOnClickOnly = true,
                            compact = true,
                        )
                        TvActionButton(
                            label = stringResource(R.string.metadata_save_key),
                            icon = TvIcons.Save,
                            enabled = !busy,
                            compact = true,
                            onClick = { saveMetadataSettings(clearCache = true) },
                            testTag = "settings-metadata-save",
                        )
                        TvActionButton(
                            label = stringResource(R.string.metadata_test_tmdb),
                            enabled = !busy && tmdbToken.isNotBlank(),
                            compact = true,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    val result = runCatching {
                                        metadataRepository.verifyTmdbCredential(tmdbToken)
                                        resources.getString(R.string.metadata_tmdb_test_ok)
                                    }.getOrElse { it.userMessage(context) }
                                    metadataStatus = result
                                    status = result
                                    busy = false
                                }
                            },
                            testTag = "settings-metadata-test-tmdb",
                        )
                    }
                    SettingsSwitchRow(
                        title = stringResource(R.string.metadata_tvmaze_switch),
                        checked = tvmazeEnabled,
                        enabled = !busy,
                        onCheckedChange = { on -> tvmazeEnabled = on; saveMetadataSettings(clearCache = false) },
                        icon = TvIcons.Guide,
                        testTag = "settings-metadata-tvmaze-enabled",
                    )
                    metadataStatus?.let { message ->
                        Text(
                            text = message,
                            color = palette.focus,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = SETTINGS_ROW_PADDING).testTag("settings-metadata-status"),
                        )
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = stringResource(R.string.metadata_language_title),
                        subtitle = stringResource(R.string.metadata_language_help),
                        value = metadataLanguageOptions.firstOrNull { it.first == appPreferences.metadataLanguage }?.second
                            ?: appPreferences.metadataLanguage,
                        icon = TvIcons.Info,
                        onClick = { openPicker = SettingsPickerTarget.MetadataLanguage },
                        focusRequester = rowFocus(SettingsPickerTarget.MetadataLanguage.key),
                        divider = false,
                        testTag = "settings-metadata-language",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.preferred_copy_title),
                        subtitle = stringResource(R.string.preferred_copy_help),
                        value = appPreferences.preferredCatalogueCopy.localizedLabel(),
                        icon = TvIcons.Channels,
                        onClick = { openPicker = SettingsPickerTarget.PreferredCopy },
                        focusRequester = rowFocus(SettingsPickerTarget.PreferredCopy.key),
                        testTag = "settings-preferred-copy",
                    )
                    if (onManageLibrary != null) {
                        SettingsValueRow(
                            title = stringResource(R.string.manager_title),
                            subtitle = stringResource(R.string.manager_row_help),
                            value = "",
                            icon = TvIcons.Settings,
                            onClick = onManageLibrary,
                            testTag = "settings-library-manager",
                        )
                    }
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.custom_group_heading))
                    SettingsOverline(stringResource(R.string.custom_group_help))
                    if (appPreferences.customCatalogueGroups.isEmpty()) {
                        SettingsRow(
                            title = stringResource(R.string.custom_group_none),
                            icon = TvIcons.Info,
                            divider = false,
                        )
                    }
                    appPreferences.customCatalogueGroups.forEach { group ->
                        SettingsValueRow(
                            title = group.name,
                            value = customGroupSummary(group),
                            onClick = { editedGroup = group },
                            testTag = "custom-group-" + group.id,
                        )
                    }
                    SettingsRow(title = stringResource(R.string.custom_group_add)) {
                        TvActionButton(
                            label = stringResource(R.string.custom_group_add),
                            onClick = { editedGroup = CatalogueCustomGroup(id = "", name = "") },
                            compact = true,
                            testTag = "custom-group-add",
                        )
                    }
                }
            }
            item {
                ArtworkCacheGroup(
                    onCleared = { status = resources.getString(R.string.artwork_cache_cleared) },
                )
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.maintenance_title))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton(
                            label = stringResource(R.string.metadata_clear_cache),
                            enabled = !busy,
                            compact = true,
                            onClick = {
                                scope.launch {
                                    metadataRepository.clearCache()
                                    val result = resources.getString(R.string.metadata_cache_cleared)
                                    metadataStatus = result
                                    status = result
                                }
                            },
                            testTag = "settings-metadata-clear-cache",
                        )
                        TvActionButton(
                            label = "TMDB",
                            compact = true,
                            onClick = { runCatching { uriHandler.openUri("https://www.themoviedb.org") } },
                        )
                        TvActionButton(
                            label = "TVmaze",
                            compact = true,
                            onClick = { runCatching { uriHandler.openUri("https://www.tvmaze.com") } },
                        )
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.SPORT) {
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.sports_settings_title))
                    Text(
                        text = stringResource(R.string.sports_settings_description),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = sportStatusText,
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvUrlField(
                            value = sportsApiKey,
                            onValueChange = { sportsApiKey = it.take(MAX_SPORTS_API_KEY_LENGTH) },
                            label = stringResource(R.string.sports_api_key),
                            modifier = Modifier.weight(1f),
                            testTag = "settings-sports-api-key",
                            leadingIconRes = TvIcons.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            editOnClickOnly = true,
                            compact = true,
                        )
                        TvActionButton(
                            label = if (sportsApiKey.isBlank()) {
                                stringResource(R.string.sports_remove_key)
                            } else {
                                stringResource(R.string.sports_save_key)
                            },
                            icon = TvIcons.Save,
                            enabled = !busy,
                            compact = true,
                            focusRequester = sectionFocusRequesters.getValue(SettingsSection.SPORT),
                            onClick = {
                                status = runCatching {
                                    secretSettingsStore.saveSportsApiSettings(
                                        SportsApiSettings(apiKey = sportsApiKey),
                                    )
                                    sportsKeyConfigured = sportsApiKey.isNotBlank()
                                    if (sportsApiKey.isBlank()) {
                                        sportsFollowMenuOpen = false
                                        competitionCatalogues = emptyMap()
                                        resources.getString(R.string.sports_key_removed)
                                    } else {
                                        resources.getString(R.string.sports_key_saved)
                                    }
                                }.getOrElse { it.userMessage(context) }
                            },
                            testTag = "settings-sports-api-save",
                        )
                    }
                    if (!sportsKeyConfigured) {
                        Text(
                            text = stringResource(R.string.sports_follow_requires_key),
                            color = palette.textMuted,
                            fontSize = 12.sp,
                        )
                    } else if (!sportsFollowMenuOpen) {
                        TvActionButton(
                            label = stringResource(R.string.sports_follow_open),
                            icon = TvIcons.Target,
                            compact = true,
                            onClick = { sportsFollowMenuOpen = true },
                            testTag = "settings-sports-follow-open",
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsGroupHeading(stringResource(R.string.sports_follow_title))
                            TvActionButton(
                                label = stringResource(R.string.action_back),
                                compact = true,
                                onClick = { sportsFollowMenuOpen = false },
                                testTag = "settings-sports-follow-close",
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(SportType.entries, key = SportType::name) { sport ->
                                TvActionButton(
                                    label = resources.getString(sport.settingsLabelRes),
                                    selected = sport in appPreferences.followedSports,
                                    compact = true,
                                    focusRequester = if (sport == SportType.entries.first()) {
                                        sportsFollowFocusRequester
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        selectedFollowSport = sport
                                        competitionQuery = ""
                                    },
                                    testTag = "settings-sports-tab-${sport.name.lowercase()}",
                                )
                            }
                        }
                        TvActionButton(
                            label = if (selectedFollowSport in appPreferences.followedSports) {
                                stringResource(R.string.sports_follow_disable_sport)
                            } else {
                                stringResource(R.string.sports_follow_enable_sport)
                            },
                            compact = true,
                            onClick = {
                                scope.launch {
                                    preferencesRepository.setFollowedSport(
                                        selectedFollowSport,
                                        selectedFollowSport !in appPreferences.followedSports,
                                    )
                                }
                            },
                            testTag = "settings-sports-toggle-sport",
                        )
                        when {
                            competitionsLoading -> Text(
                                text = stringResource(R.string.sports_competitions_loading),
                                color = palette.textMuted,
                                fontSize = 12.sp,
                            )
                            competitionLoadError != null -> Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.sports_competitions_error),
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                TvActionButton(
                                    label = stringResource(R.string.action_retry),
                                    compact = true,
                                    onClick = {
                                        competitionLoadError = null
                                        competitionLoadGeneration += 1
                                    },
                                )
                            }
                            else -> {
                                val allCompetitions = competitionCatalogues[selectedFollowSport].orEmpty()
                                val selectedCount = allCompetitions.count {
                                    it.preferenceKey in appPreferences.followedCompetitionKeys
                                }
                                Text(
                                    text = stringResource(
                                        R.string.sports_competitions_count,
                                        selectedCount,
                                        allCompetitions.size,
                                    ),
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                )
                                TvUrlField(
                                    value = competitionQuery,
                                    onValueChange = { competitionQuery = it.take(MAX_COMPETITION_QUERY_LENGTH) },
                                    label = stringResource(R.string.sports_competitions_search),
                                    modifier = Modifier.fillMaxWidth(0.62f),
                                    testTag = "settings-sports-competition-search",
                                    leadingIconRes = TvIcons.Search,
                                    keyboardType = KeyboardType.Text,
                                    editOnClickOnly = true,
                                    compact = true,
                                )
                                val query = competitionQuery.trim()
                                val competitions = allCompetitions
                                    .asSequence()
                                    .filter { competition ->
                                        query.isBlank() ||
                                            competition.name.contains(query, ignoreCase = true) ||
                                            competition.country?.contains(query, ignoreCase = true) == true
                                    }
                                    .sortedWith(
                                        compareBy<SportsCompetition>(
                                            { if (it.preferenceKey in appPreferences.followedCompetitionKeys) 0 else 1 },
                                            { it.country.orEmpty() },
                                            SportsCompetition::name,
                                        ),
                                    )
                                    .toList()
                                if (query.isNotBlank()) {
                                    Text(
                                        text = stringResource(
                                            R.string.sports_competitions_results,
                                            competitions.size,
                                        ),
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                    )
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(245.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(competitions, key = SportsCompetition::preferenceKey) { competition ->
                                        TvActionButton(
                                            label = buildString {
                                                append(competition.name)
                                                competition.country?.let { append(" · $it") }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            selected = competition.preferenceKey in
                                                appPreferences.followedCompetitionKeys,
                                            compact = true,
                                            onClick = {
                                                scope.launch {
                                                    preferencesRepository.setFollowedCompetition(
                                                        competition.preferenceKey,
                                                        competition.preferenceKey !in
                                                            appPreferences.followedCompetitionKeys,
                                                    )
                                                }
                                            },
                                            testTag = "settings-sports-competition-${competition.preferenceKey}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.PLAYBACK) {
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = stringResource(R.string.playback_buffer_title),
                        subtitle = stringResource(R.string.playback_buffer_help),
                        value = appPreferences.playbackBufferProfile.localizedLabel(),
                        icon = TvIcons.Play,
                        onClick = { openPicker = SettingsPickerTarget.Buffer },
                        focusRequester = rowFocus(SettingsPickerTarget.Buffer.key),
                        divider = false,
                        testTag = "settings-buffer",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.playback_reconnect_title),
                        subtitle = stringResource(R.string.playback_reconnect_help),
                        value = appPreferences.playbackReconnectPolicy.localizedLabel(),
                        icon = TvIcons.Refresh,
                        onClick = { openPicker = SettingsPickerTarget.Reconnect },
                        focusRequester = rowFocus(SettingsPickerTarget.Reconnect.key),
                        testTag = "settings-reconnect",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.playback_seek_step_title),
                        subtitle = stringResource(R.string.playback_seek_step_help),
                        value = appPreferences.playbackSeekStep.localizedLabel(),
                        icon = TvIcons.Replay,
                        onClick = { openPicker = SettingsPickerTarget.SeekStep },
                        focusRequester = rowFocus(SettingsPickerTarget.SeekStep.key),
                        testTag = "settings-seek-step",
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.auto_frame_rate_title),
                        subtitle = stringResource(R.string.auto_frame_rate_help),
                        checked = appPreferences.autoFrameRateEnabled,
                        onCheckedChange = { on -> scope.launch { preferencesRepository.setAutoFrameRateEnabled(on) } },
                        icon = TvIcons.Aspect,
                        testTag = "settings-auto-frame-rate",
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.auto_play_next_episode_title),
                        subtitle = stringResource(R.string.auto_play_next_episode_help),
                        checked = appPreferences.autoPlayNextEpisodeEnabled,
                        onCheckedChange = { on -> scope.launch { preferencesRepository.setAutoPlayNextEpisodeEnabled(on) } },
                        icon = TvIcons.Forward,
                        testTag = "settings-auto-next-episode",
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.picture_in_picture_title),
                        subtitle = stringResource(R.string.picture_in_picture_help),
                        checked = appPreferences.pictureInPictureEnabled,
                        onCheckedChange = { on -> scope.launch { preferencesRepository.setPictureInPictureEnabled(on) } },
                        icon = TvIcons.Aspect,
                        testTag = "settings-picture-in-picture",
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsValueRow(
                        title = stringResource(R.string.subtitle_size_title),
                        subtitle = stringResource(R.string.subtitle_style_help),
                        value = appPreferences.subtitleTextSize.localizedLabel(),
                        icon = TvIcons.Subtitles,
                        onClick = { openPicker = SettingsPickerTarget.SubtitleSize },
                        focusRequester = rowFocus(SettingsPickerTarget.SubtitleSize.key),
                        divider = false,
                        testTag = "settings-subtitle-size",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.subtitle_color_title),
                        value = appPreferences.subtitleTextColor.localizedLabel(),
                        icon = TvIcons.Subtitles,
                        onClick = { openPicker = SettingsPickerTarget.SubtitleColor },
                        focusRequester = rowFocus(SettingsPickerTarget.SubtitleColor.key),
                        testTag = "settings-subtitle-color",
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.subtitle_background_title),
                        value = appPreferences.subtitleBackground.localizedLabel(),
                        icon = TvIcons.Subtitles,
                        onClick = { openPicker = SettingsPickerTarget.SubtitleBackgroundChoice },
                        focusRequester = rowFocus(SettingsPickerTarget.SubtitleBackgroundChoice.key),
                        testTag = "settings-subtitle-background",
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.preferred_languages_title))
                    PreferredLanguageSlot.entries.forEachIndexed { index, slot ->
                        val target = SettingsPickerTarget.Language(slot)
                        val code = appPreferences.languageFor(slot)
                        SettingsValueRow(
                            title = stringResource(slot.labelResource()),
                            subtitle = if (index == 0) stringResource(R.string.preferred_languages_help) else null,
                            value = languageOptions.firstOrNull { it.first == code }?.second ?: languageOptions.first().second,
                            icon = if (slot == PreferredLanguageSlot.PRIMARY_AUDIO || slot == PreferredLanguageSlot.SECONDARY_AUDIO) TvIcons.Audio else TvIcons.Subtitles,
                            onClick = { openPicker = target },
                            focusRequester = rowFocus(target.key),
                            divider = index > 0,
                            testTag = "settings-${target.key}",
                        )
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.REMOTE) {
            item {
                RemoteMappingSection(
                    mappings = appPreferences.remoteMappings,
                    firstCellFocusRequester = sectionFocusRequesters.getValue(SettingsSection.REMOTE),
                    onAssign = { slot, action ->
                        scope.launch { preferencesRepository.setRemoteMapping(slot, action) }
                    },
                    onReset = { scope.launch { preferencesRepository.resetRemoteMappings() } },
                )
            }
            }
            if (selectedSection == SettingsSection.PARENTAL) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.parental_title),
                        color = palette.textMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    TvUrlField(
                        value = parentalPin,
                        onValueChange = { value -> parentalPin = value.filter(Char::isDigit).take(8) },
                        label = if (appPreferences.parentalPinConfigured) {
                            stringResource(R.string.parental_current_pin)
                        } else {
                            stringResource(R.string.parental_new_pin)
                        },
                        modifier = Modifier
                            .width(330.dp)
                            .focusRequester(sectionFocusRequesters.getValue(SettingsSection.PARENTAL)),
                        testTag = "settings-parental-pin",
                        leadingIconRes = TvIcons.Key,
                        keyboardType = KeyboardType.NumberPassword,
                        visualTransformation = PasswordVisualTransformation(),
                        editOnClickOnly = true,
                        compact = true,
                    )
                    if (!appPreferences.parentalPinConfigured) {
                        TvActionButton(
                            label = stringResource(R.string.parental_enable),
                            enabled = parentalPin.length in 4..8,
                            onClick = {
                                scope.launch {
                                    status = runCatching {
                                        secretSettingsStore.saveParentalPin(parentalPin)
                                        preferencesRepository.setParentalPinConfigured(true)
                                        parentalPin = ""
                                        resources.getString(R.string.parental_saved)
                                    }.getOrElse { it.userMessage(context) }
                                }
                            },
                            compact = true,
                            testTag = "settings-parental-save",
                        )
                    } else {
                        TvActionButton(
                            label = stringResource(R.string.parental_remove_change),
                            danger = true,
                            enabled = parentalPin.length in 4..8,
                            onClick = {
                                scope.launch {
                                    status = if (secretSettingsStore.verifyParentalPin(parentalPin)) {
                                        preferencesRepository.setParentalPinConfigured(false)
                                        secretSettingsStore.clearParentalPin()
                                        parentalPin = ""
                                        resources.getString(R.string.parental_removed)
                                    } else {
                                        parentalPin = ""
                                        resources.getString(R.string.pin_wrong)
                                    }
                                }
                            },
                            compact = true,
                            testTag = "settings-parental-clear",
                        )
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.BACKUP) {
            item {
                SettingsGroup {
                    Text(text = stringResource(R.string.backup_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = stringResource(R.string.backup_description),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvUrlField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it.take(128) },
                            label = stringResource(R.string.backup_passphrase),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(sectionFocusRequesters.getValue(SettingsSection.BACKUP)),
                            testTag = "settings-backup-passphrase",
                            leadingIconRes = TvIcons.Info,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            editOnClickOnly = true,
                            compact = true,
                        )
                        TvActionButton(
                            label = stringResource(R.string.backup_save),
                            enabled = backupPassphrase.length >= MIN_BACKUP_PASSPHRASE_LENGTH && !busy,
                            onClick = { exportBackupLauncher.launch(DEFAULT_BACKUP_FILE_NAME) },
                            compact = true,
                            testTag = "settings-backup-export",
                        )
                        TvActionButton(
                            label = stringResource(R.string.backup_restore),
                            enabled = backupPassphrase.length >= MIN_BACKUP_PASSPHRASE_LENGTH && !busy,
                            onClick = { restoreBackupLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream")) },
                            compact = true,
                            testTag = "settings-backup-restore",
                        )
                    }
                    Text(
                        text = stringResource(R.string.backup_warning),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.maintenance_title))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.guide_clear_all_help),
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TvActionButton(
                            label = stringResource(R.string.guide_clear_all),
                            icon = TvIcons.Delete,
                            danger = true,
                            enabled = !busy,
                            compact = true,
                            onClick = {
                                scope.launch {
                                    guideRepository.clear()
                                    maintenanceStatus = resources.getString(R.string.guide_cache_cleared)
                                }
                            },
                            testTag = "settings-clear-guide",
                        )
                    }
                    if (maintenanceStatus.isNotBlank()) {
                        Text(text = maintenanceStatus, color = palette.focus, fontSize = 12.sp, modifier = Modifier.testTag("settings-maintenance-status"))
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.SOURCES) {
            if (!sourcePageOpen) {
            item { SettingsOverline(stringResource(R.string.settings_overline_sources)) }
            item {
                SettingsGroup {
                    if (sources.isEmpty()) {
                        SettingsRow(title = stringResource(R.string.source_none), icon = TvIcons.Info, divider = false)
                    }
                    sources.forEachIndexed { index, source ->
                        val health = sourceChipStatus(source, sourceHealth)
                        // Only the name the viewer gave the source. Its address
                        // and credentials never leave its page.
                        SettingsValueRow(
                            title = source.name,
                            subtitle = sourceRowSubtitle(source, health, sourceHealth, resources),
                            value = stringResource(if (source.enabled) R.string.source_row_on else R.string.source_row_off),
                            icon = if (source.type == IptvSourceType.M3U) TvIcons.Channels else TvIcons.Link,
                            onClick = {
                                selectedSourceId = source.id
                                sourcePageOpen = true
                            },
                            focusRequester = if (index == 0) {
                                sectionFocusRequesters.getValue(SettingsSection.SOURCES)
                            } else {
                                sourceRowFocus.getOrPut(source.id) { FocusRequester() }
                            },
                            divider = index > 0,
                            testTag = "source-${source.id}",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SETTINGS_ROW_PADDING, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.source_add_m3u),
                            onClick = {
                                selectedSourceId = newM3uSourceId()
                                sourcePageOpen = true
                                status = resources.getString(R.string.source_new_m3u)
                            },
                            focusRequester = if (sources.isEmpty()) {
                                sectionFocusRequesters.getValue(SettingsSection.SOURCES)
                            } else {
                                null
                            },
                            compact = true,
                            testTag = "source-add-m3u",
                        )
                        TvActionButton(
                            label = stringResource(R.string.source_add_xtream),
                            onClick = {
                                selectedSourceId = newXtreamSourceId()
                                sourcePageOpen = true
                                status = resources.getString(R.string.source_new_xtream)
                            },
                            compact = true,
                            testTag = "source-add-xtream",
                        )
                        TvActionButton(
                            label = stringResource(
                                if (phoneSetup.running) R.string.phone_setup_stop else R.string.phone_setup_start,
                            ),
                            icon = TvIcons.Link,
                            onClick = if (phoneSetup.running) phoneSetupActions.onStop else phoneSetupActions.onStart,
                            compact = true,
                            testTag = "source-add-phone",
                        )
                    }
                }
            }
            if (phoneSetup.running || phoneSetup.noNetwork) {
            item {
                PhoneSetupPanel(state = phoneSetup)
            }
            }
            }
            if (sourcePageOpen) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsOverline(
                        text = if (sourceType == IptvSourceType.M3U) {
                            stringResource(R.string.source_type_m3u_overline, sourceName)
                        } else {
                            stringResource(R.string.source_type_xtream_overline, sourceName)
                        },
                    )
                    TvActionButton(
                        label = stringResource(R.string.source_back_to_list),
                        icon = TvIcons.Back,
                        onClick = ::closeSourcePage,
                        focusRequester = sourcePageFocus,
                        compact = true,
                        testTag = "source-page-back",
                    )
                }
            }
// Where a source is actually configured, so it sits directly under the
            // source's own overline: someone who has just pressed "Add Xtream
            // source" needs the address and the credentials, not the refresh
            // schedule.
            if (sourceType == IptvSourceType.M3U) {
                item {
                    Text(text = stringResource(R.string.source_m3u_address), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    TvUrlField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = "http(s)://provider/playlist.m3u",
                        modifier = Modifier.fillMaxWidth(SETTINGS_WIDE_FIELD_FRACTION),
                        testTag = "settings-m3u",
                        leadingIconRes = TvIcons.Link,
                        editOnClickOnly = true,
                        compact = true,
                    )
                }
                if (importScope.importsLiveTv) {
                    item {
                        Text(text = stringResource(R.string.source_xmltv_address), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    item {
                        TvUrlField(
                            value = xmlTvUrl,
                            onValueChange = { xmlTvUrl = it },
                            label = "http(s)://provider/epg.xml",
                            modifier = Modifier.fillMaxWidth(SETTINGS_WIDE_FIELD_FRACTION),
                            testTag = "settings-xmltv",
                            leadingIconRes = TvIcons.Link,
                            editOnClickOnly = true,
                            compact = true,
                        )
                    }
                }
            } else {
                item {
                    Text(text = stringResource(R.string.source_xtream_server), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    TvUrlField(
                        value = xtreamBaseUrl,
                        onValueChange = { xtreamBaseUrl = it },
                        label = "http(s)://provider:port",
                        modifier = Modifier.fillMaxWidth(SETTINGS_WIDE_FIELD_FRACTION),
                        testTag = "settings-xtream-base-url",
                        leadingIconRes = TvIcons.Link,
                        editOnClickOnly = true,
                        compact = true,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TvUrlField(
                            value = xtreamUsername,
                            onValueChange = { xtreamUsername = it },
                            label = stringResource(R.string.source_username),
                            modifier = Modifier.weight(1f),
                            testTag = "settings-xtream-username",
                            leadingIconRes = TvIcons.Key,
                            keyboardType = KeyboardType.Text,
                            editOnClickOnly = true,
                            compact = true,
                        )
                        TvUrlField(
                            value = xtreamPassword,
                            onValueChange = { xtreamPassword = it },
                            label = stringResource(R.string.source_password),
                            modifier = Modifier.weight(1f),
                            testTag = "settings-xtream-password",
                            leadingIconRes = TvIcons.Key,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            editOnClickOnly = true,
                            compact = true,
                        )
                    }
                }
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.source_name),
                    subtitle = stringResource(R.string.source_name_help),
                    icon = TvIcons.Info,
                ) {
                    TvUrlField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = stringResource(R.string.source_name),
                        modifier = Modifier.width(SETTINGS_FIELD_WIDTH),
                        testTag = "settings-source-name",
                        keyboardType = KeyboardType.Text,
                        editOnClickOnly = true,
                        compact = true,
                    )
                }
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.source_enabled_title),
                    subtitle = stringResource(R.string.source_enabled_help),
                    icon = TvIcons.Check,
                ) {
                    SettingsSwitch(
                        checked = sourceEnabled,
                        onCheckedChange = { sourceEnabled = it },
                        testTag = "settings-source-enabled",
                    )
                }
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.source_connection_limit_title),
                    subtitle = stringResource(R.string.source_connection_limit_help),
                    icon = TvIcons.Link,
                ) {
                    TvActionButton(
                        label = "−",
                        enabled = connectionLimit > 1,
                        onClick = { connectionLimit -= 1 },
                        compact = true,
                        testTag = "settings-limit-down",
                    )
                    Text(
                        text = stringResource(R.string.source_connection_limit, connectionLimit),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = palette.textPrimary,
                        fontSize = StreamMateThemeTokens.typography.body.fontSize,
                        lineHeight = StreamMateThemeTokens.typography.body.lineHeight,
                        fontWeight = FontWeight.Bold,
                    )
                    TvActionButton(
                        label = "+",
                        enabled = connectionLimit < IptvSourceConfiguration.MAX_CONNECTION_LIMIT,
                        onClick = { connectionLimit += 1 },
                        compact = true,
                        testTag = "settings-limit-up",
                    )
                }
            }
            if (selectedSource != null) {
                item {
                    SettingsRow(
                        title = stringResource(R.string.source_delete_title),
                        subtitle = stringResource(R.string.source_delete_help),
                        icon = TvIcons.Delete,
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.action_delete),
                            icon = TvIcons.Delete,
                            danger = true,
                            enabled = !busy,
                            compact = true,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = runCatching {
                                        guideRepository.clearSource(selectedSourceId)
                                        secretSettingsStore.deleteSource(selectedSourceId)
                                        sources = sources.filterNot { it.id == selectedSourceId }
                                        selectedSourceId = sources.firstOrNull { it.type == IptvSourceType.M3U }?.id
                                            ?: newM3uSourceId()
                                        sourcePageOpen = false
                                        resources.getString(R.string.source_deleted)
                                    }.getOrElse { it.userMessage(context) }
                                    busy = false
                                }
                            },
                            testTag = "settings-source-delete",
                        )
                    }
                }
            }
            item {
                SettingsRow(
                    title = stringResource(R.string.source_import_scope),
                    subtitle = stringResource(R.string.source_import_scope_help),
                    icon = TvIcons.Channels,
                ) {
                    IptvImportScope.entries.forEach { scopeOption ->
                        val label = when (scopeOption) {
                            IptvImportScope.LIVE_TV -> stringResource(R.string.source_import_live)
                            IptvImportScope.VOD -> stringResource(R.string.source_import_vod)
                            IptvImportScope.BOTH -> stringResource(R.string.source_import_both)
                        }
                        TvActionButton(
                            label = label,
                            selected = importScope == scopeOption,
                            onClick = { importScope = scopeOption },
                            compact = true,
                            modifier = Modifier.padding(start = 8.dp),
                            testTag = "settings-import-${scopeOption.name.lowercase()}",
                        )
                    }
                }
            }
            if (importScope.importsLiveTv) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.source_epg_offset),
                            color = palette.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        TvActionButton(
                            label = "−30 min",
                            enabled = epgOffsetMinutes > IptvSourceConfiguration.MIN_EPG_OFFSET_MINUTES,
                            onClick = {
                                epgOffsetMinutes -= IptvSourceConfiguration.EPG_OFFSET_STEP_MINUTES
                            },
                            compact = true,
                            testTag = "settings-epg-offset-down",
                        )
                        Text(
                            text = formatEpgOffset(epgOffsetMinutes),
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .width(74.dp)
                                .testTag("settings-epg-offset-value"),
                        )
                        TvActionButton(
                            label = "+30 min",
                            enabled = epgOffsetMinutes < IptvSourceConfiguration.MAX_EPG_OFFSET_MINUTES,
                            onClick = {
                                epgOffsetMinutes += IptvSourceConfiguration.EPG_OFFSET_STEP_MINUTES
                            },
                            compact = true,
                            testTag = "settings-epg-offset-up",
                        )
                        Text(
                            text = stringResource(R.string.source_epg_offset_help),
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 3,
                ) {
                    TvActionButton(
                        label = stringResource(R.string.source_save_securely),
                        icon = TvIcons.Save,
                        enabled = !busy,
                        onClick = {
                            validatedSource().fold(
                                onSuccess = { source ->
                                    scope.launch {
                                        busy = true
                                        status = runCatching {
                                            // A new source is synced straight away, in the
                                            // background: nobody should have to find three
                                            // refresh buttons to see their first channel.
                                            if (persistSource(source)) {
                                                onSyncNow(source.id)
                                                resources.getString(R.string.source_saved_syncing)
                                            } else {
                                                resources.getString(R.string.source_saved)
                                            }
                                        }.getOrElse { it.userMessage(context) }
                                        busy = false
                                    }
                                },
                                onFailure = {
                                    status = it.userMessage(context)
                                },
                            )
                        },
                        testTag = "settings-save",
                    )
                    if (sourceType == IptvSourceType.XTREAM) {
                        TvActionButton(
                            label = stringResource(R.string.source_test_connection),
                            icon = TvIcons.Check,
                            enabled = !busy,
                            onClick = {
                                validatedSource().fold(
                                    onSuccess = { source ->
                                        scope.launch {
                                            busy = true
                                            status = resources.getString(R.string.source_testing_xtream)
                                            status = runCatching {
                                                persistSource(source)
                                                val account = xtreamImportService.authenticate(source)
                                                val limit = account.maxConnections?.let {
                                                    resources.getString(R.string.source_server_limit, it)
                                                }.orEmpty()
                                                resources.getString(R.string.source_connection_ok, limit)
                                            }.getOrElse { it.userMessage(context) }
                                            busy = false
                                        }
                                    },
                                    onFailure = {
                                        status = it.userMessage(context)
                                    },
                                )
                            },
                            testTag = "settings-test-xtream",
                        )
                    }
                    TvActionButton(
                        label = stringResource(R.string.source_sync_everything),
                        icon = TvIcons.Refresh,
                        enabled = !busy,
                        onClick = {
                            validatedSource().fold(
                                onSuccess = { source ->
                                    scope.launch {
                                        busy = true
                                        status = runCatching {
                                            persistSource(source)
                                            onSyncNow(source.id)
                                            resources.getString(R.string.source_sync_started)
                                        }.getOrElse { it.userMessage(context) }
                                        busy = false
                                    }
                                },
                                onFailure = { status = it.userMessage(context) },
                            )
                        },
                        testTag = "settings-sync-everything",
                    )
                    if (importScope.importsLiveTv) {
                        TvActionButton(
                            label = if (sourceType == IptvSourceType.M3U) {
                                stringResource(R.string.source_refresh_playlist)
                            } else {
                                stringResource(R.string.source_refresh_channels)
                            },
                            icon = TvIcons.Refresh,
                            enabled = !busy,
                            onClick = {
                                validatedSource().fold(
                                    onSuccess = { source ->
                                        scope.launch {
                                            busy = true
                                            status = resources.getString(R.string.source_refreshing_playlist)
                                            status = runCatching {
                                                persistSource(source)
                                                val result = when (source.type) {
                                                    IptvSourceType.M3U -> source.derivedXtreamSourceOrNull()
                                                        ?.let { xtreamImportService.refreshPlaylist(it) }
                                                        ?: guideImportService.refreshPlaylist(source)
                                                    IptvSourceType.XTREAM ->
                                                        xtreamImportService.refreshPlaylist(source)
                                                }
                                                resources.getQuantityString(
                                                    R.plurals.source_imported_channels,
                                                    result.channels,
                                                    result.channels,
                                                )
                                            }.getOrElse { it.userMessage(context) }
                                            busy = false
                                        }
                                    },
                                    onFailure = {
                                        status = it.userMessage(context)
                                    },
                                )
                            },
                            testTag = "settings-refresh-playlist",
                        )
                    }
                    if (importScope.importsVod) {
                        TvActionButton(
                            label = stringResource(R.string.source_refresh_catalogue),
                            icon = TvIcons.Play,
                            enabled = !busy,
                            onClick = {
                                validatedSource().fold(
                                    onSuccess = { source ->
                                        scope.launch {
                                            busy = true
                                            status = resources.getString(R.string.source_refreshing_catalogue)
                                            status = runCatching {
                                                persistSource(source)
                                                val result = when (source.type) {
                                                    IptvSourceType.M3U -> source.derivedXtreamSourceOrNull()
                                                        ?.let { xtreamCatalogueImportService.refresh(it) }
                                                        ?: m3uCatalogueImportService.refresh(source)
                                                    IptvSourceType.XTREAM -> xtreamCatalogueImportService.refresh(source)
                                                }
                                                val moviesLabel = resources.getQuantityString(
                                                    R.plurals.source_imported_movies,
                                                    result.movies,
                                                    result.movies,
                                                )
                                                val seriesLabel = resources.getQuantityString(
                                                    R.plurals.source_imported_series,
                                                    result.series,
                                                    result.series,
                                                )
                                                resources.getString(
                                                    R.string.source_imported_catalogue,
                                                    moviesLabel,
                                                    seriesLabel,
                                                )
                                            }.getOrElse { it.userMessage(context) }
                                            busy = false
                                        }
                                    },
                                    onFailure = {
                                        status = it.userMessage(context)
                                    },
                                )
                            },
                            testTag = "settings-refresh-catalogue",
                        )
                    }
                    if (
                        importScope.importsLiveTv &&
                        (
                            sourceType == IptvSourceType.XTREAM ||
                                xmlTvUrl.isNotBlank() ||
                                m3uUrl.contains("get.php", ignoreCase = true)
                        )
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.source_refresh_epg),
                            icon = TvIcons.Epg,
                            enabled = !busy,
                            onClick = {
                                validatedSource().fold(
                                    onSuccess = { source ->
                                        scope.launch {
                                            busy = true
                                            status = resources.getString(R.string.source_refreshing_epg)
                                            status = runCatching {
                                                persistSource(source)
                                                val result = when (source.type) {
                                                    IptvSourceType.M3U -> source.xmlTvUrl
                                                        ?.let { guideImportService.refreshEpg(source.id, it) }
                                                        ?: source.derivedXtreamSourceOrNull()
                                                            ?.let { xtreamImportService.refreshEpg(it) }
                                                        ?: throw GuideImportException(CoreR.string.error_source_url_malformed)
                                                    IptvSourceType.XTREAM -> xtreamImportService.refreshEpg(source)
                                                }
                                                resources.getQuantityString(
                                                    R.plurals.source_imported_programmes,
                                                    result.programmes,
                                                    result.programmes,
                                                )
                                            }.getOrElse { it.userMessage(context) }
                                            busy = false
                                        }
                                    },
                                    onFailure = {
                                        status = it.userMessage(context)
                                    },
                                )
                            },
                            testTag = "settings-refresh-epg",
                        )
                    }
                }
            }
            }
            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = listOf(status, healthSummary)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                color = palette.focus,
                                fontSize = StreamMateThemeTokens.typography.label.fontSize,
                                lineHeight = StreamMateThemeTokens.typography.label.lineHeight,
                                modifier = Modifier.testTag("settings-status"),
                            )
                            // The note that used to sit under the page title,
                            // put where it is actually about something: beside
                            // the addresses and credentials just entered.
                            Text(
                                text = stringResource(R.string.settings_security_subtitle),
                                modifier = Modifier.padding(top = 4.dp),
                                color = palette.textDim,
                                fontSize = StreamMateThemeTokens.typography.caption.fontSize,
                                lineHeight = StreamMateThemeTokens.typography.caption.lineHeight,
                            )
                        }
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.ABOUT) {
            item {
                AppUpdateSection(
                    state = appUpdate,
                    actions = appUpdateActions,
                    focusRequester = sectionFocusRequesters.getValue(SettingsSection.ABOUT),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TvActionButton(
                        label = stringResource(R.string.settings_about_licenses),
                        icon = TvIcons.Info,
                        onClick = onLegalInformation,
                        testTag = "settings-about-licenses",
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.translate_title))
                    SettingsValueRow(
                        title = stringResource(R.string.translate_help_title),
                        subtitle = stringResource(R.string.translate_help),
                        value = "",
                        icon = TvIcons.Info,
                        onClick = { runCatching { uriHandler.openUri(TRANSLATIONS_URL) } },
                        divider = false,
                        testTag = "settings-translate-help",
                    )
                }
            }
            if (onSaveDiagnostics != null) {
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.diagnostics_title))
                    SettingsRow(
                        title = stringResource(R.string.diagnostics_save),
                        subtitle = stringResource(R.string.diagnostics_help),
                        icon = TvIcons.Save,
                        divider = false,
                    ) {
                        TvActionButton(
                            label = stringResource(R.string.diagnostics_save),
                            icon = TvIcons.Save,
                            compact = true,
                            onClick = { saveDiagnosticsLauncher.launch(diagnosticsFileName()) },
                            testTag = "settings-diagnostics-save",
                        )
                    }
                    diagnosticsStatus?.let { message ->
                        Text(
                            text = message,
                            color = palette.focus,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = SETTINGS_ROW_PADDING).testTag("settings-diagnostics-status"),
                        )
                    }
                }
            }
            }
            }
                }
            }
        }
    }
}

private const val BACKUP_MIME_TYPE = "application/vnd.streammate.backup"
private const val DEFAULT_BACKUP_FILE_NAME = "sohva-tv-backup.smbak"
private const val MIN_BACKUP_PASSPHRASE_LENGTH = 8
private const val MAX_METADATA_TOKEN_LENGTH = 2_048
private const val MAX_SPORTS_API_KEY_LENGTH = 512
private const val MAX_COMPETITION_QUERY_LENGTH = 100
private const val SETTINGS_WIDE_FIELD_FRACTION = 0.82f

/** How wide a text field sits in the trailing slot of a settings row. */
private val SETTINGS_FIELD_WIDTH = 320.dp

/**
 * The section rail, and the gap to the pane beside it.
 *
 * The reference gives the rail roughly a seventh of the frame; the pane takes
 * the rest, so a setting's title, its explanation and its control fit one line
 * instead of stacking.
 */
private val SETTINGS_SIDEBAR_WIDTH = 214.dp
private val SETTINGS_CONTENT_GAP = 26.dp
private val SETTINGS_HEADER_GAP = 14.dp
private const val SETTINGS_BREADCRUMB_SEPARATOR = "\u203a  "

/** "sohva-tv-diagnostics-20260907-1130.txt": the day and minute, so two files do not collide. */
private fun diagnosticsFileName(): String =
    "sohva-tv-diagnostics-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        .format(java.time.LocalDateTime.now()) + ".txt"
