package com.sohva.tv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.vod.Progress
import com.sohva.tv.core.model.metadata.TitleCleaner
import com.sohva.tv.core.model.vod.QualityChips
import com.sohva.tv.core.model.vod.VodText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The film page (spec 40 §4.10) for the life of its stack entry: the record passed in by key, its
 * progress (re-read after every progress write, so a finished film shows as watched on return),
 * metadata (the memory cache in the first frame, then the details lookup), Versions and Similar,
 * and the play and mark actions. Text work (the breadcrumb's regexes, the title cleaner) runs once,
 * off the main thread.
 */
class FilmModel(private val env: TitleEnvironment, val key: String) : ViewModel() {
    private val _page = MutableStateFlow<FilmPageState?>(null)
    val page: StateFlow<FilmPageState?> = _page.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _metadata = MutableStateFlow<TitleMetadata?>(null)
    val metadata: StateFlow<TitleMetadata?> = _metadata.asStateFlow()

    private val _versions = MutableStateFlow<List<VersionCard>>(emptyList())
    val versions: StateFlow<List<VersionCard>> = _versions.asStateFlow()

    private val _similar = MutableStateFlow<SimilarState>(SimilarState.Hidden)
    val similar: StateFlow<SimilarState> = _similar.asStateFlow()

    private val _picker = MutableStateFlow<MatchPicker?>(null)

    /** The open match picker, if any (VOD-FR-104). */
    val picker: StateFlow<MatchPicker?> = _picker.asStateFlow()

    /** False once the lookup found nothing: the source was disabled or re-imported (spec 40 §3). */
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    init {
        viewModelScope.launch {
            val record = env.film(key) ?: run {
                _gone.value = true
                return@launch
            }
            _metadata.value = env.cachedFilmMetadata(record)
            _page.value = withContext(env.format) {
                FilmPageState(
                    record,
                    record.groupName?.let(VodText::breadcrumbGroup),
                    QualityChips.labels(record.qualityMask),
                    TitleCleaner.searchTitle(record.name).ifBlank { record.name },
                )
            }
            launch { _versions.value = env.versions(record).takeIf { it.size >= 2 }.orEmpty() }
            show(env.filmMetadata(record) ?: _metadata.value)
        }
        viewModelScope.launch { env.progressChanges().collect { _progress.value = env.progress(key) } }
    }

    /** Resume or Watch (VOD-FR-64): from the saved place, else the start. */
    fun watch() = env.play(key, _progress.value?.resumeMs ?: 0)

    fun restart() = env.play(key, 0)

    /** A Versions card plays that copy at the film's resume position (VOD-FR-68). */
    fun playVersion(copyKey: String) = env.play(copyKey, _progress.value?.resumeMs ?: 0)

    /** Mark as watched / unwatched by the finished flag (VOD-FR-65, -96). */
    fun toggleWatched() {
        val now = _progress.value
        viewModelScope.launch {
            if (now?.completed == true) env.forget(key) else env.markWatched(key, now?.durationMs ?: 0)
        }
    }

    fun openSource() {
        _metadata.value?.sourceUrl?.let(env::openUrl)
    }

    fun openFilm(filmKey: String) = env.openFilm(filmKey)

    /** "Wrong details?": the picker searches the cleaned provider name at once (VOD-FR-104). */
    fun wrongDetails() {
        val page = _page.value ?: return
        val record = page.record
        _picker.value = MatchPicker(env, viewModelScope, PickerTarget(record.key, record.name, record.year, film = true), page.cleanTitle) { chosen ->
            viewModelScope.launch { show(chosen) }
        }
    }

    fun closePicker() {
        _picker.value?.dispose()
        _picker.value = null
    }

    /** New metadata on the page; Similar follows it, and goes with it (VOD-FR-70, -106). */
    private suspend fun show(details: TitleMetadata?) {
        _metadata.value = details
        val record = _page.value?.record ?: env.film(key) ?: return
        if (details?.detailsLoaded == true) {
            _similar.value = SimilarState.Checking
            _similar.value = SimilarState.Ready(env.similar(record, details))
        } else {
            _similar.value = SimilarState.Hidden
        }
    }
}
