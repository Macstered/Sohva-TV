package com.sohva.tv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.vod.Progress
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
 * and the play and mark actions. The breadcrumb's regexes run once, off the main thread.
 */
class FilmModel(private val env: TitleEnvironment, val key: String) : ViewModel() {
    private val _page = MutableStateFlow<FilmPageState?>(null)
    val page: StateFlow<FilmPageState?> = _page.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    /** False once the lookup found nothing: the source was disabled or re-imported (spec 40 §3). */
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    init {
        viewModelScope.launch {
            val record = env.film(key) ?: run {
                _gone.value = true
                return@launch
            }
            _page.value = withContext(env.format) {
                FilmPageState(record, record.groupName?.let(VodText::breadcrumbGroup), QualityChips.labels(record.qualityMask))
            }
        }
        viewModelScope.launch { env.progressChanges().collect { _progress.value = env.progress(key) } }
    }

    /** Resume or Watch (VOD-FR-64): from the saved place, else the start. */
    fun watch() = env.play(key, _progress.value?.resumeMs ?: 0)

    fun restart() = env.play(key, 0)

    /** Mark as watched / unwatched by the finished flag (VOD-FR-65, -96). */
    fun toggleWatched() {
        val now = _progress.value
        viewModelScope.launch {
            if (now?.completed == true) env.forget(key) else env.markWatched(key, now?.durationMs ?: 0)
        }
    }

    fun wrongDetails() = env.wrongDetails()
}
