package com.sohva.tv.feature.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Discover's subtitle addons for a film or an episode from the library (spec 30 PLAY-FR-141): the
 * same picker and timing as an addon playback. Once the tracks are known, and only when the file has
 * no subtitle in a preferred language, one is looked for while the film already plays; a subtitle
 * found, or one chosen, prepares the item again at the same place with it side-loaded.
 */
internal class VodSubtitleSession(
    private val model: PlayerModel,
    private val source: SubtitleSource,
    private val scope: CoroutineScope,
    work: CoroutineDispatcher,
    /** Whether this profile may use Discover's addons at all; the picker offers them only then. */
    available: suspend () -> Boolean,
) {
    private val ready = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()
    private var autoDone = false

    val subtitles: AddonSubtitles = AddonSubtitles(
        source, { model.controller }, { model.tracks.value }, { model.settings.vodLanguages }, scope, work,
        reload = ::reload, readyAgain = ::readyAgain,
    )

    private val check = scope.launch { _available.value = runCatching { available() }.getOrDefault(false) }

    fun onState(state: Int) {
        ready.value = state == Player.STATE_READY
        if (state == Player.STATE_READY) failed.value = false
    }

    fun onError() {
        failed.value = true
    }

    /** New tracks: an addon subtitle stays selected; the first time, the automatic look. */
    fun onTracks() {
        if (subtitles.pick.value is SubtitlePick.Addon) subtitles.select()
        if (autoDone || model.tracks.value.audio.isEmpty()) return
        autoDone = true
        scope.launch {
            check.join()
            if (!_available.value) return@launch
            if (subtitles.autoAddonOnly()) reload()
        }
    }

    /** The same place, playing or paused as it was, with the chosen subtitle. */
    private fun reload() {
        val c = model.controller ?: return
        ready.value = false
        failed.value = false
        model.prepareVod(c, c.currentPosition.coerceAtLeast(0), c.playWhenReady, subtitles::extras)
    }

    private suspend fun readyAgain(timeoutMs: Long): Boolean {
        withTimeoutOrNull(timeoutMs) { combine(ready, failed) { r, f -> r || f }.first { it } }
        return ready.value && !failed.value
    }

    fun release() = subtitles.release()
}
