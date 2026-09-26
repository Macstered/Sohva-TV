package com.sohva.tv.feature.discover.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.store.LibraryTitle
import com.sohva.tv.feature.discover.ui.components.PosterCard
import com.sohva.tv.feature.discover.ui.components.PosterContent
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.DialogCard
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryFilter { ALL, MOVIES, SERIES }

/** The Library's titles and which addons can still open them (FR-111, -112). No addon is contacted. */
data class LibraryState(val titles: List<LibraryTitle>? = null, val enabled: Set<String> = emptySet(), val filter: LibraryFilter = LibraryFilter.ALL) {
    val shown: List<LibraryTitle>
        get() = titles.orEmpty().filter {
            when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.MOVIES -> it.type == "movie"
                LibraryFilter.SERIES -> it.type == "series"
            }
        }
}

class LibraryModel(private val host: DiscoverHost, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    var lastFocused: String? = null

    fun load() {
        viewModelScope.launch {
            val (titles, enabled) = withContext(host.dispatchers.io) {
                host.library.titles(profile).take(CAPACITY) to host.installations.list(profile).filter { it.enabled }.map { it.id }.toSet()
            }
            _state.value = _state.value.copy(titles = titles, enabled = enabled)
        }
    }

    fun filter(filter: LibraryFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun remove(title: LibraryTitle) {
        viewModelScope.launch {
            withContext(host.dispatchers.io) { host.library.remove(profile, title.installation, title.type, title.id) }
            load()
        }
    }

    private companion object {
        const val CAPACITY = 1_000
    }
}

/**
 * The Discover Library (spec 50 FR-111, §5.9): All / Movies / Series, newest first. A title whose
 * addon is gone or disabled explains itself and offers removal; nothing is ever requested here.
 */
@Composable
fun LibraryScreen(model: LibraryModel, back: () -> Unit, open: (LibraryTitle) -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    var orphan by remember { mutableStateOf<LibraryTitle?>(null) }
    val backButton = remember { FocusRequester() }
    val requesters = remember { HashMap<String, FocusRequester>() }
    BackHandler { back() }
    LaunchedEffect(Unit) { model.load() }
    Column(Modifier.fillMaxSize().padding(28.dp).testTag("discover-library"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_section_metadata), Modifier.weight(1f), style = Sohva.typography.headline.copy(fontWeight = FontWeight.Bold), color = Sohva.palette.textPrimary)
            TvActionButton(stringResource(R.string.addon_ui_back_to_catalogs), back, Modifier.focusRequester(backButton).testTag("discover-library-back"), TvIcons.Back, compact = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((f, label) in listOf(LibraryFilter.ALL to R.string.manager_all, LibraryFilter.MOVIES to R.string.home_movies, LibraryFilter.SERIES to R.string.home_series)) {
                TvActionButton(stringResource(label), { model.filter(f) }, Modifier.testTag("discover-library-${f.name.lowercase()}"), compact = true, state = SurfaceState(selected = s.filter == f))
            }
        }
        val titles = s.titles
        when {
            titles == null -> Text(stringResource(R.string.addon_ui_loading_saved_titles), style = Sohva.typography.body, color = Sohva.palette.textMuted)
            s.shown.isEmpty() -> Text(
                stringResource(
                    when (s.filter) {
                        LibraryFilter.ALL -> R.string.addon_ui_your_library_is_empty_choose_add_to_library_on_a_movie_or_series_d
                        LibraryFilter.MOVIES -> R.string.addon_ui_no_saved_type
                        LibraryFilter.SERIES -> R.string.addon_ui_no_saved_series
                    },
                ),
                Modifier.testTag("discover-library-empty"), style = Sohva.typography.body, color = Sohva.palette.textMuted,
            )
            else -> LazyVerticalGrid(
                GridCells.Adaptive(132.dp), Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(s.shown, key = { _, t -> "${t.installation}|${t.type}|${t.id}" }) { _, t ->
                    val key = "${t.installation}|${t.type}|${t.id}"
                    val requester = remember(key) { requesters.getOrPut(key) { FocusRequester() } }
                    PosterCard(
                        PosterContent(t.name, t.poster, caption = t.year.orEmpty()), 132.dp,
                        { if (t.installation in s.enabled) open(t) else orphan = t },
                        Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) model.lastFocused = key }
                            .testTag("discover-library-card-${t.id}"),
                    )
                }
            }
        }
    }
    orphan?.let { t ->
        OrphanDialog({ orphan = null }) {
            orphan = null
            model.remove(t)
        }
    }
    // Return from a title: that title, else the Back button (FR-111).
    LaunchedEffect(s.titles != null) {
        if (s.titles == null) return@LaunchedEffect
        val key = model.lastFocused
        if (key == null || requesters[key]?.requestFocusWhenAttached() != true) backButton.requestFocusWhenAttached()
    }
}

@Composable
private fun OrphanDialog(close: () -> Unit, remove: () -> Unit) {
    val closeButton = remember { FocusRequester() }
    Dialog(onDismissRequest = close) {
        DialogCard(Modifier.width(480.dp).testTag("discover-library-orphan")) {
            Text(stringResource(R.string.addon_ui_this_title_s_addon_is_disabled_or_no_longer_installed_your_saved_t), style = Sohva.typography.body, color = Sohva.palette.textPrimary)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(stringResource(R.string.action_close), close, Modifier.focusRequester(closeButton), compact = true)
                TvActionButton(stringResource(R.string.addon_ui_remove_from_library), remove, Modifier.testTag("discover-library-remove"), compact = true, state = SurfaceState(danger = true))
            }
        }
    }
    LaunchedEffect(Unit) { closeButton.requestFocusWhenAttached() }
}
