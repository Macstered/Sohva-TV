package com.sohva.tv.feature.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.sohva.tv.core.model.player.AddonLanguages
import com.sohva.tv.core.model.player.SubtitleFormat
import com.sohva.tv.core.model.player.SubtitleText
import com.sohva.tv.core.model.player.VodLanguages
import com.sohva.tv.core.player.PlaybackService
import com.sohva.tv.core.player.SideSubtitles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The sync panel's state (spec 50 ADDON-FR-102). */
data class SyncState(val draftMs: Long = 0, val appliedMs: Long = 0, val applying: Boolean = false, val failed: Boolean = false)

/**
 * An addon playback's subtitles (spec 50 §4.13): the results (asked once per playback, again on
 * Refresh), the automatic choice, a chosen subtitle's download and side-loading, the track
 * selection that follows the pick, and the timing. A downloaded file lives only in memory.
 */
@OptIn(UnstableApi::class)
class AddonSubtitles internal constructor(
    private val env: AddonPlaybackEnv,
    private val token: () -> String,
    private val controller: () -> MediaController?,
    private val tracks: () -> Tracks,
    private val languages: () -> VodLanguages,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    /** Re-prepares at the current position with the current subtitle (a choice or new timing). */
    private val reload: () -> Unit,
    private val readyAgain: suspend (timeoutMs: Long) -> Boolean,
) {
    private val _results = MutableStateFlow(SubtitleResults())
    val results: StateFlow<SubtitleResults> = _results.asStateFlow()

    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    private val _pick = MutableStateFlow<SubtitlePick?>(null)

    /** Null until something was chosen: text stays off (FR-85). */
    val pick: StateFlow<SubtitlePick?> = _pick.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _syncOpen = MutableStateFlow(false)
    val syncOpen: StateFlow<Boolean> = _syncOpen.asStateFlow()

    val showAll: StateFlow<Boolean> get() = env.showAllLanguages

    private var requestJob: Job? = null
    private var manual = false

    /** The downloaded text before timing, its format and language; the side key once stored. */
    private var text: String? = null
    private var format: SubtitleFormat? = null
    private var language: String? = null
    private var sideKey: String? = null

    val timingAvailable: Boolean get() = _pick.value is SubtitlePick.Addon && text != null

    /** FR-97, -100: asked once per playback (the picker opening or the automatic choice); [refresh] asks again. */
    fun request(refresh: Boolean = false) {
        if (_requested.value && !refresh) return
        _requested.value = true
        requestJob?.cancel()
        _results.value = SubtitleResults(pending = 1)
        requestJob = scope.launch { env.subtitles(token()).collect { _results.value = it } }
    }

    // ---- Automatic choice (FR-98, -99) ----

    /**
     * One bounded attempt per playback (the caller times it out at 5 s). True when an addon
     * subtitle was loaded, so the stream must be prepared again with it.
     */
    suspend fun auto(): Boolean {
        val prefs = languages()
        val wanted = listOfNotNull(prefs.subtitles, prefs.subtitlesSecond).mapNotNull(AddonLanguages::normalise).distinct()
        if (wanted.isEmpty() || audioSuppresses(prefs)) {
            if (!manual) _pick.value = SubtitlePick.Off
            return false
        }
        request()
        for (lang in wanted) {
            if (manual) return false
            if (embedded(lang)) {
                _pick.value = SubtitlePick.Embedded(lang, null, null)
                return false
            }
            if (tryAddon(lang)) return true
        }
        if (!manual) _pick.value = SubtitlePick.Off
        return false
    }

    /** FR-99, after the 5 s budget or an error: an embedded preferred language, unless a choice already stands. */
    fun embeddedFallback() {
        if (manual || _pick.value is SubtitlePick.Addon) return
        val prefs = languages()
        val lang = listOfNotNull(prefs.subtitles, prefs.subtitlesSecond).mapNotNull(AddonLanguages::normalise)
            .firstOrNull { !audioSuppresses(prefs) && embedded(it) }
        _pick.value = lang?.let { SubtitlePick.Embedded(it, null, null) } ?: SubtitlePick.Off
    }

    private fun audioSuppresses(prefs: VodLanguages): Boolean {
        val primary = AddonLanguages.normalise(prefs.audio) ?: return false
        return tracks().audio.any { AddonLanguages.normalise(it.language) == primary }
    }

    private fun embedded(lang: String): Boolean = tracks().text.any { !it.sideLoaded && AddonLanguages.normalise(it.language) == lang }

    /** Inline candidates first, then each provider's as they arrive; at most two downloads per language. */
    private suspend fun tryAddon(lang: String): Boolean {
        val tried = HashSet<String>()
        var attempts = 0
        while (!manual) {
            val now = _results.value
            val next = now.candidates.sortedBy { if (it.provider == null) 0 else 1 }
                .firstOrNull { it.key !in tried && AddonLanguages.normalise(it.language) == lang }
            if (next == null) {
                if (now.done) return false
                _results.first { it !== now }
                continue
            }
            tried += next.key
            attempts++
            if (load(next)) return true
            if (attempts >= MAX_ATTEMPTS) return false
        }
        return false
    }

    // ---- Choices (FR-97) ----

    /** A subtitle chosen in the picker: downloaded, side-loaded at the current position, timing back at 0. */
    fun choose(candidate: SubtitleCandidate, done: () -> Unit) {
        manual = true
        scope.launch {
            _loading.value = true
            _message.value = null
            val ok = load(candidate)
            _loading.value = false
            if (ok) {
                reload()
                done()
            }
        }
    }

    fun chooseEmbedded(track: TrackItem) {
        manual = true
        forgetText()
        _pick.value = SubtitlePick.Embedded(AddonLanguages.normalise(track.language), track.group, track.index)
        select()
    }

    fun off() {
        manual = true
        forgetText()
        _pick.value = SubtitlePick.Off
        select()
    }

    private fun forgetText() {
        text = null
        format = null
        sideKey = null
        _sync.value = SyncState()
        SideSubtitles.clear()
    }

    private suspend fun load(candidate: SubtitleCandidate): Boolean {
        return when (val result = env.downloadSubtitle(token(), candidate.key)) {
            is SubtitleDownload.Failed -> {
                _message.value = result.message
                false
            }
            is SubtitleDownload.Ready -> {
                text = result.text
                format = result.format
                language = AddonLanguages.normalise(candidate.language)
                _sync.value = SyncState()
                store(0)
                _pick.value = SubtitlePick.Addon(candidate, language)
                true
            }
        }
    }

    /** The text shifted by [offsetMs] (off the main thread: regex work), kept as the one side subtitle. */
    private suspend fun store(offsetMs: Long) {
        val source = text ?: return
        val fmt = format ?: return
        val bytes = withContext(work) { SubtitleText.shift(source, fmt, offsetMs).toByteArray(Charsets.UTF_8) }
        sideKey = SideSubtitles.put(bytes)
    }

    /** The request extras that side-load the current subtitle, or none. */
    fun extras(into: Bundle) {
        val key = sideKey?.takeIf { _pick.value is SubtitlePick.Addon } ?: return
        val fmt = format ?: return
        into.putString(PlaybackService.EXTRA_SUBTITLE_KEY, key)
        into.putString(PlaybackService.EXTRA_SUBTITLE_MIME, fmt.mimeType)
        language?.let { into.putString(PlaybackService.EXTRA_SUBTITLE_LANGUAGE, it) }
    }

    /** Makes the player's text selection follow the pick; changes nothing when it already does. */
    fun select() {
        val c = controller() ?: return
        val current = c.trackSelectionParameters
        val builder = current.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        when (val p = _pick.value) {
            null, SubtitlePick.Off -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            is SubtitlePick.Embedded -> {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(p.language)
                val group = p.group?.let { c.currentTracks.groups.getOrNull(it) }
                if (group != null && p.index != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, p.index))
            }
            is SubtitlePick.Addon -> {
                val group = c.currentTracks.groups.firstOrNull { g ->
                    g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.getTrackFormat(it).id?.endsWith(SideSubtitles.TRACK_ID) == true }
                }
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                if (group != null) builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            }
        }
        val next: TrackSelectionParameters = builder.build()
        if (next != current) c.trackSelectionParameters = next
    }

    // ---- Timing (FR-102, -103) ----

    fun openSync() {
        if (!timingAvailable) return
        _sync.value = _sync.value.copy(draftMs = _sync.value.appliedMs, failed = false)
        _syncOpen.value = true
    }

    fun closeSync() {
        _syncOpen.value = false
    }

    /** Left/Right: 0.1 s, or 1 s while held (repeat count ≥ 8); ±60 s. */
    fun nudge(later: Boolean, repeatCount: Int) {
        val step = if (repeatCount >= HELD_REPEATS) 1_000L else 100L
        val draft = (_sync.value.draftMs + if (later) step else -step).coerceIn(-SubtitleText.MAX_OFFSET_MS, SubtitleText.MAX_OFFSET_MS)
        _sync.value = _sync.value.copy(draftMs = draft)
    }

    fun resetDraft() {
        _sync.value = _sync.value.copy(draftMs = 0)
    }

    /** Applies the draft once: the stored text is shifted again and the stream prepared at the same place. */
    fun applyDraft() {
        val s = _sync.value
        if (s.applying || !timingAvailable) return
        _sync.value = s.copy(applying = true, failed = false)
        scope.launch {
            store(s.draftMs)
            reload()
            val back = readyAgain(APPLY_TIMEOUT_MS)
            _sync.value = _sync.value.copy(appliedMs = if (back) s.draftMs else _sync.value.appliedMs, applying = false, failed = !back)
        }
    }

    fun release() {
        requestJob?.cancel()
        SideSubtitles.clear()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val HELD_REPEATS = 8
        const val APPLY_TIMEOUT_MS = 20_000L
    }
}
