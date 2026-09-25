package com.sohva.tv.core.data.home

import com.sohva.tv.core.data.vod.ContinueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Continue watching as Home reads it (spec 02 HOME-FR-25). */
sealed interface ResumeState {
    data object Loading : ResumeState

    /** A read failed or had not answered after 5 s; a later answer still replaces it. */
    data object Failed : ResumeState

    data class Ready(val items: List<ContinueItem>) : ResumeState

    data object Empty : ResumeState

    /** Not loading any more ("settled", HOME-FR-26): what Trakt rows and hero lookups wait for. */
    val settled: Boolean get() = this != Loading
}

/**
 * The Continue watching row for the life of the process (spec 02 §4.5, §9.1): its first read runs
 * as soon as the app starts, so Home's first frame can already show it; later it re-reads on
 * changes at most once a second, not while a video plays (it catches up when the player closes),
 * and publishes only a different result. Library part only until Trakt (M8) and Discover (M10).
 */
@OptIn(FlowPreview::class)
class ContinueFeed(
    private val scope: CoroutineScope,
    private val read: suspend () -> List<ContinueItem>,
    private val changes: Flow<Unit>,
    private val playing: StateFlow<Boolean>,
    private val onFirstSettled: (ms: Long) -> Unit = {},
) {
    private val _state = MutableStateFlow<ResumeState>(ResumeState.Loading)
    val state: StateFlow<ResumeState> = _state.asStateFlow()

    private var reading: Job? = null
    private var started = false
    private var reported = false

    /** Starts the first read and the change subscription; once. */
    fun start() {
        if (started) return
        started = true
        refresh()
        scope.launch {
            changes.debounce(QUIET_MS).collect {
                if (playing.value) playing.first { !it }
                refresh()
            }
        }
    }

    /** OK on the failure card (HOME-FR-28). */
    fun retry() {
        _state.value = ResumeState.Loading
        refresh()
    }

    private fun refresh() {
        reading?.cancel()
        val begun = System.nanoTime()
        reading = scope.launch {
            // The 5 s failure is a state, not a cancellation: a late answer still replaces it.
            val timeout = launch {
                delay(FAIL_AFTER_MS)
                if (_state.value == ResumeState.Loading) _state.value = ResumeState.Failed
            }
            val next = try {
                val items = read()
                if (items.isEmpty()) ResumeState.Empty else ResumeState.Ready(items)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ResumeState.Failed
            } finally {
                timeout.cancel()
            }
            if (next != _state.value) _state.value = next
            if (!reported && next.settled) {
                reported = true
                onFirstSettled((System.nanoTime() - begun) / 1_000_000)
            }
        }
    }

    /** The first read that settles; for work that must wait for it (HOME-FR-26). */
    suspend fun awaitSettled() {
        state.filter { it.settled }.first()
    }

    companion object {
        const val FAIL_AFTER_MS: Long = 5_000
        const val QUIET_MS: Long = 1_000
    }
}
