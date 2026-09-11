package com.streammate.tv.addons

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.sohva.tv.addons.AddonException
import com.sohva.tv.addons.AddonCatalog
import com.sohva.tv.addons.AddonFailure
import com.sohva.tv.addons.InstalledAddon
import com.streammate.tv.R
import com.streammate.tv.app.AppPreferences
import com.streammate.tv.app.AddonFeature
import com.streammate.tv.app.StreamMateContainer
import com.streammate.tv.app.StreamMateThemeTokens
import com.streammate.tv.app.activeRestriction
import com.streammate.tv.feature.common.StreamMateScreenBackground
import com.streammate.tv.feature.common.TvActionButton
import com.streammate.tv.feature.common.TvUrlField
import com.streammate.tv.feature.common.requestFocusWhenAttached
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Invoked behind the existing startup/profile PIN gate, never through an exported activity. */
class DiscoverAddonFeature(private val startInManager: Boolean = false) : AddonFeature {
    @Composable override fun Screen(container: StreamMateContainer, onBack: () -> Unit) {
        val preferences by container.preferencesRepository.preferences
            .collectAsStateWithLifecycle(initialValue = null)
        val current = preferences
        BackHandler(onBack = onBack)
        StreamMateScreenBackground(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { modifier ->
            if (current == null) {
                Text(stringResource(R.string.addon_loading), modifier)
            } else if (!container.runtimePolicy.addonsAllowed || current.activeRestriction.restricted) {
                Column(modifier) {
                    Text(stringResource(R.string.addon_access_denied))
                    TvActionButton(stringResource(R.string.addon_back), onBack)
                }
            } else {
                val context = LocalContext.current
                val host = remember(container) { AddonHost.get(context, container) }
                // Disposes/cancels pending UI work and clears secret input on profile change.
                key(current.activeProfileId) {
                    AddonDiscoverScreen(host, current, onBack, modifier, initiallyManage = startInManager)
                }
            }
        }
    }
}

@Composable
internal fun AddonManagerScreen(host: AddonHost, preferences: AppPreferences, onBack: () -> Unit, modifier: Modifier,
    loadInstallations: suspend () -> List<InstalledAddon> = { host.manager.list(preferences.activeProfileId) }) {
    val labels = addonStrings()
    val profileId = preferences.activeProfileId
    val scope = rememberCoroutineScope()
    var installations by remember { mutableStateOf<List<InstalledAddon>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<AddonFailure?>(null) }
    var showOrder by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf("addons") }
    var selectedCatalog by remember { mutableStateOf<Pair<InstalledAddon, AddonCatalog>?>(null) }
    var catalogInstallation by remember { mutableStateOf<InstalledAddon?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<com.sohva.tv.addons.AddonWatchProgress>>(emptyList()) }
    var selectedHistory by remember { mutableStateOf<com.sohva.tv.addons.AddonWatchProgress?>(null) }
    val backFocus = remember { FocusRequester() }
    fun run(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        failure = null
        scope.launch {
            try {
                operation()
                installations = loadInstallations()
                loaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AddonException) {
                failure = error.failure
            } catch (_: Exception) {
                failure = AddonFailure.STORAGE
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { run { } }
    LaunchedEffect(selectedCatalog, catalogInstallation, showImport, showOrder, showHistory) {
        if (selectedCatalog == null) backFocus.requestFocusWhenAttached()
    }
    if (showImport) {
        AddonImportScreen({ host.batchImport.preview(profileId, it) }, { host.batchImport.commit(it) }, {
            showImport = false
            run { }
        }, modifier, stremioImport = { host.stremioImport.copy(profileId) })
        return
    }
    if (showOrder) { AddonCatalogOrderScreen(host, profileId, installations, { showOrder = false }, modifier); return }
    selectedHistory?.let { item ->
        AddonSourcesScreen(host, profileId, item.identity.video, item.title, {
            selectedHistory = null
            run { history = host.progress.recent(profileId) }
        }, modifier, item.identity)
        return
    }
    if (showHistory) {
        BackHandler { showHistory = false }
        LazyColumn(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TvActionButton(labels(R.string.addon_back_catalogs), { showHistory = false }); Text(labels(R.string.addon_ui_addon_watch_history)) }
            itemsIndexed(history.filter { item -> installations.any { it.installationId == item.identity.metadataInstallationId && it.enabled } }) { _, item ->
                Column {
                    TvActionButton(item.title, { selectedHistory = item }, testTag = "addon-history-item")
                    Text(if (item.completed) labels(com.streammate.tv.iptv.R.string.catalogue_watched) else labels(R.string.addon_ui_watch_position, item.positionMillis / 60_000, item.positionMillis / 1000 % 60))
                    TvActionButton(labels(R.string.addon_ui_forget_progress), {
                        run { host.progress.remove(profileId, item.identity); history = host.progress.recent(profileId) }
                    }, enabled = !busy, compact = true)
                }
            }
            item { if (history.isEmpty()) Text(labels(R.string.addon_ui_no_addon_watch_history_yet)) }
        }
        return
    }
    selectedCatalog?.let { (installation, catalog) ->
        AddonCatalogScreen(host, profileId, installation, catalog, { selectedCatalog = null }, modifier)
        return
    }
    catalogInstallation?.let { installation ->
        BackHandler { catalogInstallation = null }
        LazyColumn(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TvActionButton(stringResource(R.string.addon_back_catalogs), { catalogInstallation = null }, focusRequester = backFocus)
                Text(installation.manifest.name)
            }
            itemsIndexed(installation.manifest.catalogs, key = { _, catalog -> "${catalog.type.length}:${catalog.type}${catalog.id}" }) { _, catalog ->
                TvActionButton("${catalog.name} · ${labels.mediaType(catalog.type)}", { selectedCatalog = installation to catalog })
            }
        }
        return
    }
    AddonManagerSettings(host, preferences, installations, loaded, busy, failure, backFocus, onBack, modifier,
        onOperation = ::run, onBrowse = { catalogInstallation = it },
        onImport = { showImport = true }, onHistory = { run { history = host.progress.recent(profileId); showHistory = true } },
        onCatalogOrder = { showOrder = true }, section = settingsSection, onSection = { settingsSection = it })
}

internal fun AddonFailure.messageResource(): Int = when (this) {
    AddonFailure.INVALID_URL, AddonFailure.INSECURE_URL -> R.string.addon_error_url
    AddonFailure.CONFIGURATION_REQUIRED -> R.string.addon_error_configuration
    AddonFailure.INVALID_MANIFEST, AddonFailure.INVALID_RESPONSE, AddonFailure.RESPONSE_TOO_LARGE -> R.string.addon_error_manifest
    AddonFailure.NETWORK, AddonFailure.TIMEOUT, AddonFailure.HTTP_ERROR -> R.string.addon_error_network
    AddonFailure.REDIRECT -> R.string.addon_error_redirect
    AddonFailure.ACCESS_DENIED -> R.string.addon_access_denied
    else -> R.string.addon_error_operation
}
