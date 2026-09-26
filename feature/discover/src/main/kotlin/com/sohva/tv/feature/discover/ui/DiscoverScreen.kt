package com.sohva.tv.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.MetaPreview
import com.sohva.tv.feature.discover.store.CatalogEntry
import com.sohva.tv.feature.discover.ui.components.RailTarget
import com.sohva.tv.feature.discover.ui.grid.GridModel
import com.sohva.tv.feature.discover.ui.grid.GridScreen
import com.sohva.tv.feature.discover.ui.landing.LandingActions
import com.sohva.tv.feature.discover.ui.landing.LandingModel
import com.sohva.tv.feature.discover.ui.landing.LandingScreen
import com.sohva.tv.feature.discover.ui.library.LibraryModel
import com.sohva.tv.feature.discover.ui.library.LibraryScreen
import com.sohva.tv.feature.discover.ui.search.SearchModel
import com.sohva.tv.feature.discover.ui.search.SearchScreen
import com.sohva.tv.feature.discover.ui.setup.OrganiseModel
import com.sohva.tv.feature.discover.ui.setup.OrganiseScreen
import com.sohva.tv.feature.discover.ui.setup.SetupActions
import com.sohva.tv.feature.discover.ui.setup.SetupModel
import com.sohva.tv.feature.discover.ui.setup.SetupScreen
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a title page opens on (spec 50 §4.10): the catalog's addon, the title as the catalog showed
 * it, and, from Continue watching or history, the video that was playing (FR-66, -75).
 */
data class TitleRequest(val owner: String?, val preview: MetaPreview, val videoId: String? = null) {
    override fun toString(): String = "TitleRequest(${preview.type})"
}

/** Where Discover hands the viewer to the app: out to Home, or to a title page. */
class DiscoverNavigation(val leave: () -> Unit, val openTitle: (TitleRequest) -> Unit)

/** Discover's internal screens (spec 50 §3): each replaces the landing. */
sealed interface DiscoverPage {
    data object Landing : DiscoverPage

    data object Library : DiscoverPage

    data object Search : DiscoverPage

    data object Filter : DiscoverPage

    data object Setup : DiscoverPage

    /** A catalog's Show all grid, opened from a shelf or from one addon's catalog list. */
    data class Grid(val entry: CatalogEntry, val fromSetup: Boolean) : DiscoverPage
}

/** Discover's page, kept for the whole stay (a title page and the player sit on top of it). */
class DiscoverShellModel : ViewModel() {
    private val _page = MutableStateFlow<DiscoverPage>(DiscoverPage.Landing)
    val page: StateFlow<DiscoverPage> = _page.asStateFlow()

    /** The rail item the landing focuses when a rail page returns to it (§3 table). */
    var railFocus: RailTarget? = null

    fun go(page: DiscoverPage) {
        _page.value = page
    }

    fun backToLanding(from: RailTarget?) {
        railFocus = from
        _page.value = DiscoverPage.Landing
    }
}

/**
 * Discover (spec 50): "Working…" until access is known, the restricted-profile text with Back to
 * home when denied (FR-05), else the current internal page. Every model is keyed by the profile, so
 * a switch disposes all Discover state (§3).
 */
@Composable
fun DiscoverScreen(host: DiscoverHost, profile: String, nav: DiscoverNavigation) {
    val allowed by produceState<Boolean?>(null, profile) { value = host.access.allowed(profile) }
    when (allowed) {
        null -> Box(Modifier.fillMaxSize().testTag("discover-loading"), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.addon_loading), style = Sohva.typography.body, color = Sohva.palette.textMuted)
        }
        false -> Denied(nav.leave)
        true -> Pages(host, profile, nav)
    }
}

@Composable
private fun Denied(leave: () -> Unit) {
    val back = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().testTag("discover-denied"), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.addon_access_denied), style = Sohva.typography.bodyLarge, color = Sohva.palette.textPrimary)
        TvActionButton(stringResource(R.string.addon_back), leave, Modifier.focusRequester(back).testTag("discover-denied-back"), TvIcons.Back)
    }
    LaunchedEffect(Unit) { back.requestFocusWhenAttached() }
}

@Composable
private fun Pages(host: DiscoverHost, profile: String, nav: DiscoverNavigation) {
    val shell = viewModel(key = "discover-shell:$profile") { DiscoverShellModel() }
    val page by shell.page.collectAsStateWithLifecycle()
    val landing = viewModel(key = "discover-landing:$profile") { LandingModel(host, profile) }
    val denied by landing.denied.collectAsStateWithLifecycle()
    if (denied) {
        Denied(nav.leave)
        return
    }
    val openTitle = { owner: String, item: MetaPreview -> nav.openTitle(TitleRequest(owner, item)) }
    when (val p = page) {
        DiscoverPage.Landing -> {
            val railFocus = shell.railFocus
            shell.railFocus = null
            LandingScreen(
                landing,
                LandingActions(
                    openTitle = openTitle,
                    openContinue = { card -> nav.openTitle(TitleRequest(card.entry.identity.installation, card.preview, card.entry.identity.videoId)) },
                    openGrid = { entry -> shell.go(DiscoverPage.Grid(entry, fromSetup = false)) },
                    openPage = { target ->
                        shell.go(
                            when (target) {
                                RailTarget.LIBRARY -> DiscoverPage.Library
                                RailTarget.SEARCH -> DiscoverPage.Search
                                RailTarget.DISCOVER -> DiscoverPage.Filter
                                else -> DiscoverPage.Setup
                            },
                        )
                    },
                    leave = nav.leave,
                ),
                railFocus,
            )
        }
        DiscoverPage.Library -> {
            val model = viewModel(key = "discover-library:$profile") { LibraryModel(host, profile) }
            LibraryScreen(model, { shell.backToLanding(RailTarget.LIBRARY) }) { t ->
                nav.openTitle(TitleRequest(t.installation, MetaPreview(t.type, t.id, t.name, t.poster, t.background, releaseInfo = t.year)))
            }
        }
        DiscoverPage.Search -> {
            val model = viewModel(key = "discover-search:$profile") { SearchModel(host, profile) }
            SearchScreen(model, { shell.backToLanding(RailTarget.SEARCH) }, openTitle)
        }
        DiscoverPage.Filter -> {
            val model = viewModel(key = "discover-filter:$profile") { GridModel(host, profile, null, filterPage = true) }
            GridScreen(model, R.string.addon_ui_back_to_catalogs, { shell.backToLanding(RailTarget.DISCOVER) }, openTitle)
        }
        is DiscoverPage.Grid -> {
            val model = viewModel(key = "discover-grid:$profile:${p.entry.key}") { GridModel(host, profile, p.entry, filterPage = false) }
            val back = { if (p.fromSetup) shell.go(DiscoverPage.Setup) else shell.go(DiscoverPage.Landing) }
            GridScreen(model, if (p.fromSetup) R.string.addon_back_catalogs else R.string.addon_ui_back_to_catalogs, back, openTitle)
        }
        DiscoverPage.Setup -> {
            val model = viewModel(key = "discover-setup:$profile") { SetupModel(host, profile) }
            SetupScreen(
                model,
                SetupActions(
                    back = { shell.backToLanding(RailTarget.SETUP) },
                    openGrid = { entry -> shell.go(DiscoverPage.Grid(entry, fromSetup = true)) },
                    openHistory = { e ->
                        nav.openTitle(TitleRequest(e.identity.installation, MetaPreview(e.identity.mediaType, e.identity.mediaId, e.artwork.name.ifBlank { e.title }, e.artwork.poster, e.artwork.background), e.identity.videoId))
                    },
                    organise = { setup ->
                        val organise = viewModel(key = "discover-organise:$profile") { OrganiseModel(host, profile) }
                        OrganiseScreen(organise) { setup.back() }
                    },
                    import = { setup -> ImportPlaceholder { setup.back() } },
                ),
            )
        }
    }
}

/** The import page arrives with the import work (M9 part 5); until then it only returns. */
@Composable
private fun ImportPlaceholder(back: () -> Unit) {
    val button = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().testTag("discover-import"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        TvActionButton(stringResource(R.string.addon_ui_back_to_import), back, Modifier.focusRequester(button), TvIcons.Back)
    }
    LaunchedEffect(Unit) { button.requestFocusWhenAttached() }
}
