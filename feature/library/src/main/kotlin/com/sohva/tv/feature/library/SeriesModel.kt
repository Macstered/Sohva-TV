package com.sohva.tv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.database.EpisodeRecord
import com.sohva.tv.core.data.database.SeriesRecord
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.vod.QualityChips
import com.sohva.tv.core.model.vod.VodText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The series page (spec 40 §4.11): the record, its episodes from the database (fetched from the
 * provider on the first open when none are stored), the progress of this series only, the
 * selected season and episode, and metadata for the series and for the selected episode (the
 * latter 350 ms after the selection rests). Season ticks and cards are built off the main thread (§9.8).
 */
class SeriesModel(private val env: TitleEnvironment, val key: String) : ViewModel() {
    private val _page = MutableStateFlow<SeriesPageState?>(null)
    val page: StateFlow<SeriesPageState?> = _page.asStateFlow()

    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    private val _cards = MutableStateFlow<List<EpisodeCard>>(emptyList())

    /** Every episode of the series, season then episode. */
    val cards: StateFlow<List<EpisodeCard>> = _cards.asStateFlow()

    private val _seasons = MutableStateFlow<List<Int>>(emptyList())
    val seasons: StateFlow<List<Int>> = _seasons.asStateFlow()

    /** Seasons whose every episode is finished (VOD-FR-94). */
    private val _watchedSeasons = MutableStateFlow<Set<Int>>(emptySet())
    val watchedSeasons: StateFlow<Set<Int>> = _watchedSeasons.asStateFlow()

    private val _season = MutableStateFlow<Int?>(null)
    val season: StateFlow<Int?> = _season.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)

    /** The selected episode's key: the buttons and progress line follow it (VOD-FR-40). */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _metadata = MutableStateFlow<TitleMetadata?>(null)
    val metadata: StateFlow<TitleMetadata?> = _metadata.asStateFlow()

    private val _episodeMetadata = MutableStateFlow<EpisodeMetadata?>(null)

    /** The selected episode's metadata, once looked up; any other episode's is dropped at once. */
    val episodeMetadata: StateFlow<EpisodeMetadata?> = _episodeMetadata.asStateFlow()

    private val _picker = MutableStateFlow<MatchPicker?>(null)

    /** The open match picker, if any (VOD-FR-104). */
    val picker: StateFlow<MatchPicker?> = _picker.asStateFlow()

    private val _episodes = MutableStateFlow(EpisodesState())
    val episodesState: StateFlow<EpisodesState> = _episodes.asStateFlow()

    /** Set once the viewer picked a season: then the arriving episodes do not take focus (VOD-FR-88). */
    var seasonChosen: Boolean = false
        private set

    private var fetch: Job? = null
    private var firstAnswer = true

    init {
        viewModelScope.launch {
            val record = env.series(key) ?: run {
                _gone.value = true
                return@launch
            }
            _metadata.value = env.cachedSeriesMetadata(record)
            _page.value = withContext(env.format) {
                SeriesPageState(record, record.groupName?.let(VodText::breadcrumbGroup), QualityChips.labels(record.qualityMask))
            }
            launch { env.seriesMetadata(record)?.let { _metadata.value = it } }
            // Moving on cancels the previous episode's wait or lookup (VOD-FR-77).
            _selected.collectLatest { selected -> lookUpEpisode(record, selected) }
        }
        viewModelScope.launch {
            // Both flows answer once at the start, then on every change.
            combine(env.episodes(key), env.progressChanges()) { episodes, _ -> episodes }.collect { episodes ->
                onEpisodes(episodes, env.seriesProgress(key))
            }
        }
    }

    private suspend fun onEpisodes(episodes: List<EpisodeRecord>, progress: Map<String, Progress>) {
        // The database's first answer decides: none stored → ask the provider, once (VOD-FR-74).
        if (firstAnswer) {
            firstAnswer = false
            if (episodes.isEmpty()) refresh() else _episodes.value = EpisodesState(loading = false)
        } else if (episodes.isNotEmpty() && fetch?.isActive != true) {
            _episodes.value = _episodes.value.copy(loading = false)
        }
        val built = withContext(env.format) {
            val cards = episodes.map { EpisodeCard(it, it.name?.takeIf { name -> name.isNotBlank() }, progress[it.key]) }
            val seasons = episodes.map { it.season }.distinct().sorted()
            val watched = seasons.filter { s -> cards.filter { it.record.season == s }.all { it.progress?.completed == true } }.toSet()
            Triple(cards, seasons, watched)
        }
        _cards.value = built.first
        _seasons.value = built.second
        _watchedSeasons.value = built.third
        // Season 1, else the first season; the selection stays when it belongs to the season (VOD-FR-76).
        val season = _season.value?.takeIf { it in built.second } ?: built.second.firstOrNull { it == 1 } ?: built.second.firstOrNull()
        _season.value = season
        val selected = _selected.value
        if (selected == null || built.first.none { it.record.key == selected && it.record.season == season }) {
            _selected.value = built.first.firstOrNull { it.record.season == season }?.record?.key
        }
    }

    private suspend fun lookUpEpisode(record: SeriesRecord, selected: String?) {
        if (_episodeMetadata.value?.key != selected) _episodeMetadata.value = null
        val card = _cards.value.firstOrNull { it.record.key == selected } ?: return
        val season = card.record.season
        val number = card.record.number
        env.cachedEpisodeMetadata(record, season, number)?.let {
            _episodeMetadata.value = EpisodeMetadata(card.record.key, it)
            return
        }
        delay(EPISODE_REST_MS)
        env.episodeMetadata(record, season, number)?.let { _episodeMetadata.value = EpisodeMetadata(card.record.key, it) }
    }

    /** "Refresh episodes" (VOD-FR-75); a second press while a fetch runs is ignored. */
    fun refresh() {
        if (fetch?.isActive == true) return
        _episodes.value = _episodes.value.copy(loading = _cards.value.isEmpty(), error = null)
        fetch = viewModelScope.launch {
            val result = env.fetchEpisodes(key)
            val error = (result as? Outcome.Failed)?.error
            _episodes.value = EpisodesState(loading = false, error = error)
        }
    }

    fun chooseSeason(season: Int) {
        seasonChosen = true
        _season.value = season
        if (_cards.value.none { it.record.key == _selected.value && it.record.season == season }) {
            _selected.value = _cards.value.firstOrNull { it.record.season == season }?.record?.key
        }
    }

    /** Focusing an episode selects it (VOD-FR-40). */
    fun select(key: String) {
        _selected.value = key
    }

    fun selectedCard(): EpisodeCard? = _cards.value.firstOrNull { it.record.key == _selected.value }

    /** Continue or Watch the selected episode (VOD-FR-81); OK on a card plays that episode (VOD-FR-84). */
    fun watch(card: EpisodeCard? = selectedCard()) {
        val c = card ?: return
        _selected.value = c.record.key
        env.play(c.record.key, c.progress?.resumeMs ?: 0)
    }

    fun restart() {
        val c = selectedCard() ?: return
        env.play(c.record.key, 0)
    }

    fun toggleWatched() {
        val c = selectedCard() ?: return
        viewModelScope.launch {
            if (c.progress?.completed == true) env.forget(c.record.key) else env.markWatched(c.record.key, knownMs(c))
        }
    }

    fun markSeasonWatched() {
        val season = _season.value ?: return
        viewModelScope.launch { env.markSeasonWatched(key, season) }
    }

    /** "Wrong details?": the picker searches the cleaned provider name at once (VOD-FR-104). */
    fun wrongDetails() {
        val record = _page.value?.record ?: return
        viewModelScope.launch {
            val query = withContext(env.format) { TitleCleaner.searchTitle(record.name).ifBlank { record.name } }
            _picker.value = MatchPicker(env, viewModelScope, PickerTarget(record.key, record.name, record.year, film = false), query) { chosen ->
                _metadata.value = chosen
            }
        }
    }

    fun closePicker() {
        _picker.value?.dispose()
        _picker.value = null
    }

    fun openSource() {
        _metadata.value?.sourceUrl?.let(env::openUrl)
    }

    private fun knownMs(card: EpisodeCard): Long = maxOf(card.progress?.durationMs ?: 0, (card.record.durationSeconds ?: 0) * 1_000L)

    private companion object {
        const val EPISODE_REST_MS = 350L
    }
}
