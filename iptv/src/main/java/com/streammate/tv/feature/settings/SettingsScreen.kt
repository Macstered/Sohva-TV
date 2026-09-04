package com.streammate.tv.feature.settings

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
import com.streammate.tv.app.PreferredLanguageSlot
import com.streammate.tv.app.RemoteChannelKeyMode
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

private enum class SettingsSection {
    SOURCES,
    PLAYBACK,
    METADATA,
    SPORT,
    PARENTAL,
    BACKUP,
    ABOUT,
}

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
    onBack: () -> Unit,
    onManageLibrary: (() -> Unit)? = null,
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
    var refreshIntervalMenuOpen by remember { mutableStateOf(false) }
    var languageMenuSlot by remember { mutableStateOf<PreferredLanguageSlot?>(null) }
    var sectionFocusGeneration by remember { mutableIntStateOf(0) }
    val sectionFocusRequesters = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    val sportsFollowFocusRequester = remember { FocusRequester() }
    val refreshIntervalButtonFocusRequester = remember { FocusRequester() }
    val refreshIntervalOptionFocusRequester = remember { FocusRequester() }
    val languageButtonFocusRequesters = remember {
        PreferredLanguageSlot.entries.associateWith { FocusRequester() }
    }
    val languageOptionFocusRequester = remember { FocusRequester() }
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
    LaunchedEffect(selectedSection, sectionFocusGeneration, sportsFollowMenuOpen) {
        val requester = if (selectedSection == SettingsSection.SPORT && sportsFollowMenuOpen) {
            sportsFollowFocusRequester
        } else {
            sectionFocusRequesters.getValue(selectedSection)
        }
        requester.requestFocus()
    }
    LaunchedEffect(refreshIntervalMenuOpen) {
        if (refreshIntervalMenuOpen) refreshIntervalOptionFocusRequester.requestFocus()
    }
    BackHandler(enabled = refreshIntervalMenuOpen) {
        refreshIntervalMenuOpen = false
        refreshIntervalButtonFocusRequester.requestFocus()
    }
    LaunchedEffect(languageMenuSlot) {
        if (languageMenuSlot != null) languageOptionFocusRequester.requestFocus()
    }
    BackHandler(enabled = languageMenuSlot != null) {
        val slot = languageMenuSlot
        languageMenuSlot = null
        slot?.let { languageButtonFocusRequesters.getValue(it).requestFocus() }
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

    suspend fun persistSource(source: IptvSourceConfiguration) {
        secretSettingsStore.upsertSource(source)
        guideRepository.upsertSourceState(source)
        sources = if (sources.any { it.id == source.id }) {
            sources.map { if (it.id == source.id) source else it }
        } else {
            sources + source
        }
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
            "failed" -> resources.getString(R.string.health_failed, kind, health.consecutiveFailures)
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
                if (onManageLibrary != null) TvActionButton(
                    label = stringResource(R.string.manager_title),
                    onClick = onManageLibrary,
                    compact = true,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    testTag = "settings-library-manager",
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
                        refreshIntervalMenuOpen = false
                        languageMenuSlot = null
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
            if (selectedSection == SettingsSection.METADATA) {
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.preferred_copy_title))
                    FlowRow(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CataloguePreferredCopy.entries.forEach { preferred ->
                            TvActionButton(
                                label = preferred.localizedLabel(),
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setPreferredCatalogueCopy(preferred)
                                    }
                                },
                                compact = true,
                                selected = appPreferences.preferredCatalogueCopy == preferred,
                                testTag = "settings-preferred-copy-${preferred.name.lowercase()}",
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.preferred_copy_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
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
                    Text(
                        text = stringResource(R.string.metadata_description),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            label = stringResource(
                                if (tmdbEnabled) {
                                    R.string.metadata_tmdb_enabled
                                } else {
                                    R.string.metadata_tmdb_disabled
                                },
                            ),
                            onClick = { tmdbEnabled = !tmdbEnabled },
                            focusRequester = sectionFocusRequesters.getValue(SettingsSection.METADATA),
                            compact = true,
                            testTag = "settings-metadata-tmdb-enabled",
                        )
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
                            label = stringResource(
                                if (tvmazeEnabled) {
                                    R.string.metadata_tvmaze_enabled
                                } else {
                                    R.string.metadata_tvmaze_disabled
                                },
                            ),
                            onClick = { tvmazeEnabled = !tvmazeEnabled },
                            compact = true,
                            testTag = "settings-metadata-tvmaze-enabled",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton(
                            label = stringResource(R.string.metadata_save),
                            icon = TvIcons.Save,
                            enabled = !busy && (!tmdbEnabled || tmdbToken.isNotBlank()),
                            compact = true,
                            onClick = {
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
                                        metadataRepository.clearCache()
                                        resources.getString(R.string.metadata_saved)
                                    }.getOrElse { it.userMessage(context) }
                                    metadataStatus = result
                                    status = result
                                    busy = false
                                }
                            },
                            testTag = "settings-metadata-save",
                        )
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
                    metadataStatus?.let { message ->
                        Text(
                            text = message,
                            color = palette.focus,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("settings-metadata-status"),
                        )
                    }
                    Text(
                        text = stringResource(R.string.metadata_disclosure),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                ArtworkCacheGroup(
                    onCleared = { status = resources.getString(R.string.artwork_cache_cleared) },
                )
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
                    Text(
                        text = stringResource(R.string.sports_timezone_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(SPORTS_TIME_ZONES, key = { it.id }) { timeZone ->
                            TvActionButton(
                                label = timeZone.label,
                                selected = appPreferences.timeZoneId == timeZone.id,
                                compact = true,
                                focusRequester = if (timeZone == SPORTS_TIME_ZONES.first()) {
                                    sectionFocusRequesters.getValue(SettingsSection.SPORT)
                                } else {
                                    null
                                },
                                onClick = {
                                    scope.launch { preferencesRepository.setTimeZone(timeZone.id) }
                                },
                                testTag = "settings-sports-timezone-${timeZone.id}",
                            )
                        }
                    }
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
                    Text(
                        text = stringResource(R.string.sports_disclosure),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            }
            if (selectedSection == SettingsSection.PLAYBACK) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.remote_channel_change),
                        color = palette.textMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    RemoteChannelKeyMode.entries.forEach { mode ->
                        TvActionButton(
                            label = when (mode) {
                                RemoteChannelKeyMode.DPAD_AND_CHANNEL_KEYS -> {
                                    stringResource(R.string.remote_dpad_and_channel)
                                }
                                RemoteChannelKeyMode.CHANNEL_KEYS_ONLY -> {
                                    stringResource(R.string.remote_channel_only)
                                }
                            },
                            selected = appPreferences.remoteChannelKeyMode == mode,
                            onClick = {
                                scope.launch { preferencesRepository.setRemoteChannelKeyMode(mode) }
                            },
                            focusRequester = if (mode == RemoteChannelKeyMode.entries.first()) {
                                sectionFocusRequesters.getValue(SettingsSection.PLAYBACK)
                            } else {
                                null
                            },
                            compact = true,
                            testTag = "settings-remote-${mode.name.lowercase()}",
                        )
                    }
                    Text(
                        text = stringResource(R.string.remote_track_help),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.playback_buffer_title))
                    FlowRow(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaybackBufferProfile.entries.forEach { profile ->
                            TvActionButton(
                                label = profile.localizedLabel(),
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setPlaybackBufferProfile(profile)
                                    }
                                },
                                compact = true,
                                selected = appPreferences.playbackBufferProfile == profile,
                                testTag = "settings-buffer-${profile.name.lowercase()}",
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.playback_buffer_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(R.string.auto_frame_rate_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true, false).forEach { on ->
                            TvActionButton(
                                label = stringResource(
                                    if (on) R.string.auto_frame_rate_on else R.string.auto_frame_rate_off,
                                ),
                                onClick = {
                                    scope.launch { preferencesRepository.setAutoFrameRateEnabled(on) }
                                },
                                compact = true,
                                selected = appPreferences.autoFrameRateEnabled == on,
                                testTag = "settings-auto-frame-rate-${if (on) "on" else "off"}",
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.auto_frame_rate_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(R.string.auto_play_next_episode_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true, false).forEach { on ->
                            TvActionButton(
                                label = stringResource(
                                    if (on) {
                                        R.string.auto_play_next_episode_on
                                    } else {
                                        R.string.auto_play_next_episode_off
                                    },
                                ),
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setAutoPlayNextEpisodeEnabled(on)
                                    }
                                },
                                compact = true,
                                selected = appPreferences.autoPlayNextEpisodeEnabled == on,
                                testTag = "settings-auto-next-episode-${if (on) "on" else "off"}",
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.auto_play_next_episode_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(R.string.playback_decoder_fallback_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.playback_reconnect_title))
                    FlowRow(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaybackReconnectPolicy.entries.forEach { policy ->
                            TvActionButton(
                                label = policy.localizedLabel(),
                                onClick = {
                                    scope.launch {
                                        preferencesRepository.setPlaybackReconnectPolicy(policy)
                                    }
                                },
                                compact = true,
                                selected = appPreferences.playbackReconnectPolicy == policy,
                                testTag = "settings-reconnect-${policy.name.lowercase()}",
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.playback_reconnect_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.startup_title),
                        color = palette.textMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    StartupScreen.entries.forEach { startupScreen ->
                        TvActionButton(
                            label = startupScreen.localizedLabel(),
                            selected = appPreferences.startupScreen == startupScreen,
                            onClick = {
                                scope.launch { preferencesRepository.setStartupScreen(startupScreen) }
                            },
                            compact = true,
                            testTag = "settings-startup-${startupScreen.name.lowercase()}",
                        )
                    }
                    Text(
                        text = stringResource(R.string.startup_last_channel_help),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                var interfaceLanguage by remember { mutableStateOf(AppLocale.stored(context)) }
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.interface_language_title))
                    Text(
                        text = stringResource(R.string.interface_language_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        interfaceLanguageOptions().forEach { (tag, label) ->
                            TvActionButton(
                                label = label,
                                selected = interfaceLanguage == tag,
                                onClick = {
                                    interfaceLanguage = tag
                                    // Only the pre-33 path needs this; from
                                    // Tiramisu the framework restarts us.
                                    if (AppLocale.apply(context, tag)) {
                                        context.findActivity()?.recreate()
                                    }
                                },
                                compact = true,
                                testTag = "settings-interface-language-${tag ?: "system"}",
                            )
                        }
                    }
                }
            }
            item {
                val languageOptions = preferredLanguageOptions()
                SettingsGroup {
                    SettingsGroupHeading(stringResource(R.string.preferred_languages_title))
                    Text(
                        text = stringResource(R.string.preferred_languages_help),
                        color = palette.textMuted,
                        fontSize = 12.sp,
                    )
                    PreferredLanguageSlot.entries.forEach { slot ->
                        val selectedCode = appPreferences.languageFor(slot)
                        PreferredLanguageRow(
                            label = stringResource(slot.labelResource()),
                            selectedCode = selectedCode,
                            options = languageOptions,
                            menuOpen = languageMenuSlot == slot,
                            onToggleMenu = {
                                languageMenuSlot = if (languageMenuSlot == slot) null else slot
                            },
                            onSelect = { code ->
                                scope.launch {
                                    preferencesRepository.setPreferredLanguage(slot, code)
                                    val duplicateSlot = slot.pairedSlot()
                                    if (code != null && code == appPreferences.languageFor(duplicateSlot)) {
                                        preferencesRepository.setPreferredLanguage(duplicateSlot, null)
                                    }
                                }
                                languageMenuSlot = null
                                languageButtonFocusRequesters.getValue(slot).requestFocus()
                            },
                            buttonFocusRequester = languageButtonFocusRequesters.getValue(slot),
                            optionFocusRequester = languageOptionFocusRequester,
                            testTag = "settings-language-${slot.name.lowercase()}",
                        )
                    }
                }
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
            }
            if (selectedSection == SettingsSection.SOURCES) {
            item { SettingsOverline(stringResource(R.string.settings_overline_sources)) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = SETTINGS_ROW_PADDING),
                ) {
                    items(sources, key = { it.id }) { source ->
                        // Only the name the viewer gave the source. Its address
                        // and credentials never leave the editor, and the dot
                        // carries the state instead of a word that would.
                        SettingsSourceChip(
                            label = source.name,
                            status = sourceChipStatus(source, sourceHealth),
                            selected = source.id == selectedSourceId,
                            onClick = { selectedSourceId = source.id },
                            focusRequester = if (source == sources.firstOrNull()) {
                                sectionFocusRequesters.getValue(SettingsSection.SOURCES)
                            } else {
                                null
                            },
                            testTag = "source-${source.id}",
                        )
                    }
                    item {
                        TvActionButton(
                            label = stringResource(R.string.source_add_m3u),
                            onClick = {
                                selectedSourceId = newM3uSourceId()
                                status = resources.getString(R.string.source_new_m3u)
                            },
                            focusRequester = if (sources.isEmpty()) {
                                sectionFocusRequesters.getValue(SettingsSection.SOURCES)
                            } else {
                                null
                            },
                            testTag = "source-add-m3u",
                        )
                    }
                    item {
                        TvActionButton(
                            label = stringResource(R.string.source_add_xtream),
                            onClick = {
                                selectedSourceId = newXtreamSourceId()
                                status = resources.getString(R.string.source_new_xtream)
                            },
                            testTag = "source-add-xtream",
                        )
                    }
                }
            }
            item {
                SettingsOverline(
                    text = if (sourceType == IptvSourceType.M3U) {
                        stringResource(R.string.source_type_m3u_overline, sourceName)
                    } else {
                        stringResource(R.string.source_type_xtream_overline, sourceName)
                    },
                )
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
                val selectedInterval = appPreferences.playlistEpgRefreshInterval
                val selectedIntervalLabel = pluralStringResource(
                    R.plurals.source_refresh_interval_hours,
                    selectedInterval.hours.toInt(),
                    selectedInterval.hours.toInt(),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsValueRow(
                        title = stringResource(R.string.source_refresh_schedule),
                        subtitle = stringResource(R.string.source_refresh_schedule_help),
                        value = selectedIntervalLabel,
                        icon = TvIcons.Refresh,
                        chevron = TvIcons.ChevronDown,
                        onClick = { refreshIntervalMenuOpen = !refreshIntervalMenuOpen },
                        focusRequester = refreshIntervalButtonFocusRequester,
                        divider = false,
                        testTag = "settings-refresh-interval",
                    )
                    if (refreshIntervalMenuOpen) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.End)
                                .width(220.dp)
                                .background(palette.panel, StreamMateThemeTokens.shapes.medium)
                                .padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlaylistEpgRefreshInterval.entries.forEach { interval ->
                                val intervalLabel = pluralStringResource(
                                    R.plurals.source_refresh_interval_hours,
                                    interval.hours.toInt(),
                                    interval.hours.toInt(),
                                )
                                TvActionButton(
                                    label = intervalLabel,
                                    selected = interval == selectedInterval,
                                    onClick = {
                                        refreshIntervalMenuOpen = false
                                        scope.launch {
                                            preferencesRepository.setPlaylistEpgRefreshInterval(interval)
                                            status = resources.getString(
                                                R.string.source_refresh_schedule_saved,
                                                resources.getQuantityString(
                                                    R.plurals.source_refresh_interval_hours,
                                                    interval.hours.toInt(),
                                                    interval.hours.toInt(),
                                                ),
                                            )
                                            refreshIntervalButtonFocusRequester.requestFocus()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    focusRequester = if (interval == selectedInterval) {
                                        refreshIntervalOptionFocusRequester
                                    } else {
                                        null
                                    },
                                    compact = true,
                                    testTag = "settings-refresh-interval-${interval.hours}",
                                )
                            }
                        }
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
                                            persistSource(source)
                                            resources.getString(R.string.source_saved)
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
                        Spacer(Modifier.width(20.dp))
                        TvActionButton(
                            label = stringResource(R.string.guide_clear_all),
                            icon = TvIcons.Delete,
                            danger = true,
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    guideRepository.clear()
                                    status = resources.getString(R.string.guide_cache_cleared)
                                }
                            },
                            testTag = "settings-clear-guide",
                        )
                    }
                }
            }
            }
            if (selectedSection == SettingsSection.ABOUT) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TvActionButton(
                        label = stringResource(R.string.settings_about_licenses),
                        icon = TvIcons.Info,
                        onClick = onLegalInformation,
                        focusRequester = sectionFocusRequesters.getValue(SettingsSection.ABOUT),
                        testTag = "settings-about-licenses",
                    )
                }
            }
            item {
                SettingsGroup {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(TvIcons.Info),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(palette.focus),
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.settings_http_disclosure),
                            color = palette.textMuted,
                            fontSize = 13.sp,
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

private fun formatEpgOffset(minutes: Int): String {
    if (minutes == 0) return "0 min"
    val sign = if (minutes > 0) "+" else "−"
    val absoluteMinutes = kotlin.math.abs(minutes)
    val hours = absoluteMinutes / 60
    val remainingMinutes = absoluteMinutes % 60
    return when {
        remainingMinutes == 0 -> "$sign$hours h"
        hours == 0 -> "$sign$remainingMinutes min"
        else -> "$sign$hours h $remainingMinutes min"
    }
}

@Composable
private fun PreferredLanguageRow(
    label: String,
    selectedCode: String?,
    options: List<Pair<String?, String>>,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onSelect: (String?) -> Unit,
    buttonFocusRequester: FocusRequester,
    optionFocusRequester: FocusRequester,
    testTag: String,
) {
    val palette = StreamMateThemeTokens.palette
    val selectedIndex = options.indexOfFirst { it.first == selectedCode }.coerceAtLeast(0)
    val selectedLabel = options[selectedIndex].second
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = palette.textMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TvActionButton(
                label = "$selectedLabel  ▾",
                onClick = onToggleMenu,
                focusRequester = buttonFocusRequester,
                compact = true,
                testTag = testTag,
            )
        }
        if (menuOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.End)
                    .width(260.dp)
                    .background(palette.panel, StreamMateThemeTokens.shapes.medium)
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEachIndexed { index, (code, optionLabel) ->
                    TvActionButton(
                        label = optionLabel,
                        selected = code == selectedCode,
                        onClick = { onSelect(code) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (index == selectedIndex) optionFocusRequester else null,
                        compact = true,
                        testTag = "$testTag-option-$index",
                    )
                }
            }
        }
    }
}

@Composable
private fun interfaceLanguageOptions(): List<Pair<String?, String>> = listOf(
    null to stringResource(R.string.interface_language_system),
    "en" to stringResource(R.string.interface_language_en),
    "fi" to stringResource(R.string.interface_language_fi),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun preferredLanguageOptions(): List<Pair<String?, String>> = listOf(
    null to stringResource(R.string.language_automatic),
    "fi" to stringResource(R.string.language_finnish),
    "en" to stringResource(R.string.language_english),
    "sv" to stringResource(R.string.language_swedish),
    "da" to stringResource(R.string.language_danish),
    "no" to stringResource(R.string.language_norwegian),
    "et" to stringResource(R.string.language_estonian),
    "de" to stringResource(R.string.language_german),
    "fr" to stringResource(R.string.language_french),
    "es" to stringResource(R.string.language_spanish),
    "it" to stringResource(R.string.language_italian),
    "nl" to stringResource(R.string.language_dutch),
)

private fun AppPreferences.languageFor(slot: PreferredLanguageSlot): String? = when (slot) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> preferredAudioLanguage
    PreferredLanguageSlot.SECONDARY_AUDIO -> secondaryAudioLanguage
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> preferredSubtitleLanguage
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> secondarySubtitleLanguage
}

private fun PreferredLanguageSlot.pairedSlot(): PreferredLanguageSlot = when (this) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> PreferredLanguageSlot.SECONDARY_AUDIO
    PreferredLanguageSlot.SECONDARY_AUDIO -> PreferredLanguageSlot.PRIMARY_AUDIO
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> PreferredLanguageSlot.SECONDARY_SUBTITLE
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> PreferredLanguageSlot.PRIMARY_SUBTITLE
}

private fun PreferredLanguageSlot.labelResource(): Int = when (this) {
    PreferredLanguageSlot.PRIMARY_AUDIO -> R.string.preferred_audio_primary
    PreferredLanguageSlot.SECONDARY_AUDIO -> R.string.preferred_audio_secondary
    PreferredLanguageSlot.PRIMARY_SUBTITLE -> R.string.preferred_subtitle_primary
    PreferredLanguageSlot.SECONDARY_SUBTITLE -> R.string.preferred_subtitle_secondary
}

@Composable
private fun SettingsSectionRail(
    modifier: Modifier = Modifier,
    selected: SettingsSection,
    onSelected: (SettingsSection) -> Unit,
) {
    LazyColumn(
        modifier = modifier.testTag("settings-sections"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(SettingsSection.entries) { section ->
            TvListRow(
                label = section.localizedLabel(),
                icon = section.icon,
                onClick = { onSelected(section) },
                modifier = Modifier.height(SETTINGS_RAIL_ROW_HEIGHT),
                selected = selected == section,
                testTag = "settings-section-${section.name.lowercase()}",
            )
        }
    }
}

@Composable
private fun SettingsSection.localizedLabel(): String = stringResource(
    when (this) {
        SettingsSection.SOURCES -> R.string.settings_section_sources
        SettingsSection.PLAYBACK -> R.string.settings_section_playback
        SettingsSection.METADATA -> R.string.settings_section_metadata
        SettingsSection.SPORT -> R.string.settings_section_sport
        SettingsSection.PARENTAL -> R.string.settings_section_parental
        SettingsSection.BACKUP -> R.string.settings_section_backup
        SettingsSection.ABOUT -> R.string.settings_section_about
    },
)

private val SettingsSection.icon: Int
    get() = when (this) {
        SettingsSection.SOURCES -> TvIcons.Channels
        SettingsSection.PLAYBACK -> TvIcons.Play
        SettingsSection.METADATA -> TvIcons.Info
        SettingsSection.SPORT -> TvIcons.Target
        SettingsSection.PARENTAL -> TvIcons.Lock
        SettingsSection.BACKUP -> TvIcons.Save
        SettingsSection.ABOUT -> TvIcons.Guide
    }

/**
 * Settings groups read as sections of one list, separated by a hairline, rather
 * than as a stack of filled panels. The only filled surface on the screen is
 * whatever currently has focus.
 */
/**
 * The artwork cache: how much it may take, how much it has taken, and a way to
 * take it back.
 *
 * A library of a few thousand titles will fill whatever ceiling it is given, and
 * on a set-top box that disk is shared with recordings and every other app. It
 * was a flat gigabyte before, chosen by nobody.
 */
@Composable
private fun ArtworkCacheGroup(onCleared: () -> Unit) {
    val palette = StreamMateThemeTokens.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var limit by remember { mutableStateOf(ArtworkCacheSettings.limit(context)) }
    var usageBytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }

    suspend fun refreshUsage() {
        usageBytes = ArtworkCache.usageBytes(context)
    }
    LaunchedEffect(Unit) { refreshUsage() }

    SettingsGroup {
        SettingsGroupHeading(stringResource(R.string.artwork_cache_title))
        Text(
            text = stringResource(R.string.artwork_cache_help),
            color = palette.textMuted,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtworkCacheLimit.entries.forEach { option ->
                TvActionButton(
                    label = stringResource(R.string.artwork_cache_limit, option.megabytes),
                    selected = limit == option,
                    onClick = {
                        limit = option
                        ArtworkCacheSettings.setLimit(context, option)
                    },
                    compact = true,
                    testTag = "settings-artwork-cache-${option.name.lowercase()}",
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = usageBytes?.let {
                    stringResource(R.string.artwork_cache_usage, ArtworkCache.formatBytes(it))
                }.orEmpty(),
                color = palette.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).testTag("settings-artwork-cache-usage"),
            )
            TvActionButton(
                label = stringResource(R.string.artwork_cache_clear),
                icon = TvIcons.Delete,
                enabled = !clearing && (usageBytes ?: 0L) > 0L,
                onClick = {
                    scope.launch {
                        clearing = true
                        ArtworkCache.clear(context)
                        refreshUsage()
                        clearing = false
                        onCleared()
                    }
                },
                compact = true,
                testTag = "settings-artwork-cache-clear",
            )
        }
    }
}

/** The uppercase label naming a group, inside the group's own margin. */
@Composable
private fun SettingsGroupHeading(text: String) {
    val palette = StreamMateThemeTokens.palette
    val typography = StreamMateThemeTokens.typography
    Text(
        text = text.uppercase(),
        color = palette.textDim,
        fontSize = typography.overline.fontSize,
        lineHeight = typography.overline.lineHeight,
        fontWeight = FontWeight.Bold,
        letterSpacing = typography.overline.letterSpacing,
    )
}

/**
 * One group of settings.
 *
 * A hairline above it and the pane's own left margin down the side: no panel,
 * no outline, no second background. The screen reads as one list whose only
 * filled surface is whatever currently has focus.
 */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val palette = StreamMateThemeTokens.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SETTINGS_ROW_PADDING)
                .height(1.dp)
                .background(palette.divider),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SETTINGS_ROW_PADDING, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * What the dot on a source chip should say.
 *
 * A source switched off is dim whatever its history; one whose last refresh
 * failed is red; everything else is fine. The message never carries the error
 * text, which can contain a URL with credentials in it.
 */
private fun sourceChipStatus(
    source: IptvSourceConfiguration,
    health: List<SourceRefreshHealth>,
): SettingsSourceStatus = when {
    !source.enabled -> SettingsSourceStatus.DISABLED
    health.any { it.sourceId == source.id && it.status == "failed" } -> SettingsSourceStatus.FAILING
    else -> SettingsSourceStatus.HEALTHY
}

private fun newM3uSourceId(): String = "m3u-${UUID.randomUUID()}"

@Composable
private fun StartupScreen.localizedLabel(): String = when (this) {
    StartupScreen.HOME -> stringResource(R.string.startup_home)
    StartupScreen.GUIDE -> stringResource(R.string.startup_guide)
    StartupScreen.LAST_CHANNEL -> stringResource(R.string.startup_last_channel)
}

/**
 * Named for what the viewer wants rather than for what the app measures: the
 * ranking behind these reads a provider's claim, and saying so in a button
 * would be a paragraph.
 */
@Composable
private fun CataloguePreferredCopy.localizedLabel(): String = when (this) {
    CataloguePreferredCopy.NONE -> stringResource(R.string.preferred_copy_none)
    CataloguePreferredCopy.FINNISH_AUDIO -> stringResource(R.string.preferred_copy_finnish_audio)
    CataloguePreferredCopy.FINNISH_SUBTITLES -> stringResource(R.string.preferred_copy_finnish_subtitles)
    CataloguePreferredCopy.LARGEST_PICTURE -> stringResource(R.string.preferred_copy_largest_picture)
}

@Composable
private fun PlaybackBufferProfile.localizedLabel(): String = when (this) {
    PlaybackBufferProfile.DEFAULT -> stringResource(R.string.playback_buffer_default)
    PlaybackBufferProfile.LOW_LATENCY -> stringResource(R.string.playback_buffer_low_latency)
    PlaybackBufferProfile.STABILITY -> stringResource(R.string.playback_buffer_stability)
}

@Composable
private fun PlaybackReconnectPolicy.localizedLabel(): String = when (this) {
    PlaybackReconnectPolicy.STANDARD -> stringResource(R.string.playback_reconnect_standard)
    PlaybackReconnectPolicy.PERSISTENT -> stringResource(R.string.playback_reconnect_persistent)
}

private val SportType.settingsLabelRes: Int
    get() = when (this) {
        SportType.FOOTBALL -> R.string.sports_follow_football
        SportType.ICE_HOCKEY -> R.string.sports_follow_hockey
        SportType.AUSTRALIAN_FOOTBALL -> R.string.sports_follow_afl
        SportType.BASKETBALL -> R.string.sports_follow_basketball
        SportType.BASEBALL -> R.string.sports_follow_baseball
        SportType.HANDBALL -> R.string.sports_follow_handball
        SportType.RUGBY -> R.string.sports_follow_rugby
        SportType.VOLLEYBALL -> R.string.sports_follow_volleyball
    }

private data class SportsTimeZone(val id: String, val label: String)

private val SPORTS_TIME_ZONES = listOf(
    SportsTimeZone("Europe/Helsinki", "Helsinki"),
    SportsTimeZone("Europe/Stockholm", "Stockholm"),
    SportsTimeZone("Europe/Berlin", "Berlin"),
    SportsTimeZone("Europe/London", "London"),
    SportsTimeZone("America/New_York", "New York"),
    SportsTimeZone("UTC", "UTC"),
)

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

private fun newXtreamSourceId(): String = "xtream-${UUID.randomUUID()}"
