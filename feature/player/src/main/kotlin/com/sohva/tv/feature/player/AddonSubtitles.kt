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
import com.sohva.tv.core.model.player.TrackLanguages
import com.sohva.tv.core.model.player.VodLanguages
import com.sohva.tv.core.player.PlaybackService
import com.sohva.tv.core.player.SideSubtitles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val source: SubtitleSource,
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

    val showAll: StateFlow<Boolean> get() = source.showAllLanguages

    /** "Show all languages" (FR-97): global and persisted. */
    fun setShowAll(on: Boolean) {
        scope.launch { source.setShowAllLanguages(on) }
    }

    private var requestJob: Job? = null
    private var manual = false
    private var automaticJob: Job? = null
    private var selectionJob: Job? = null
    // Selection state stays on the player's main scope. A late download or text conversion may
    // publish only for the choice that started it, even if its source ignored cancellation.
    private var choice = 0L

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
        requestJob = scope.launch { source.subtitles().collect { _results.value = it } }
    }

    // ---- Automatic choice (FR-98, -99) ----

    /**
     * One bounded attempt per playback (the caller times it out at 5 s). True when an addon
     * subtitle was loaded, so the stream must be prepared again with it.
     */
    suspend fun auto(): Boolean = automatically {
        val prefs = languages()
        val wanted = listOfNotNull(prefs.subtitles, prefs.subtitlesSecond).mapNotNull(AddonLanguages::normalise).distinct()
        if (wanted.isEmpty() || audioSuppresses(prefs)) {
            if (!manual) _pick.value = SubtitlePick.Off
            return@automatically false
        }
        request()
        for (lang in wanted) {
            if (manual) return@automatically false
            if (embedded(lang)) {
                _pick.value = SubtitlePick.Embedded(lang, null, null)
                return@automatically false
            }
            if (tryAddon(lang)) return@automatically true
        }
        if (!manual) _pick.value = SubtitlePick.Off
        false
    }

    /**
     * A film or an episode (spec 30 PLAY-FR-141): only when the file has no subtitle in a preferred
     * language (and the preferred audio does not make subtitles unneeded), asked while it already
     * plays; nothing is chosen otherwise, the player's own language choice stands. True when an
     * addon subtitle was loaded, so the item must be prepared again with it.
     */
    suspend fun autoAddonOnly(): Boolean = automatically {
        val prefs = languages()
        val wanted = listOfNotNull(prefs.subtitles, prefs.subtitlesSecond).mapNotNull(AddonLanguages::normalise).distinct()
        if (wanted.isEmpty() || audioSuppresses(prefs) || wanted.any(::embedded)) return@automatically false
        request()
        for (lang in wanted) {
            if (manual) return@automatically false
            if (tryAddon(lang)) return@automatically true
        }
        false
    }

    /** Cancel just the automatic work; Discover's caller must still finish starting playback. */
    private suspend fun automatically(select: suspend () -> Boolean): Boolean = coroutineScope {
        if (manual) return@coroutineScope false
        val attempt = async { select() }
        automaticJob = attempt
        try {
            attempt.await()
        } catch (cancelled: CancellationException) {
            // A manual choice cancels this child. Cancellation of playback itself still propagates.
            currentCoroutineContext().ensureActive()
            false
        } finally {
            if (automaticJob === attempt) automaticJob = null
        }
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
        return tracks().audio.any { AddonLanguages.normalise(TrackLanguages.ofTrack(it.language, it.label)) == primary }
    }

    private fun embedded(lang: String): Boolean = tracks().text.any { !it.sideLoaded && AddonLanguages.normalise(it.language) == lang }

    /** Inline candidates first, then each provider's as they arrive; at most two downloads per language. */
    private suspend fun tryAddon(lang: String): Boolean {
        val tried = HashSet<String>()
        var attempts = 0
        while (!manual) {
            val now = _results.value
            // Provider order is already stable. Two short scans avoid sorting on the player thread.
            val eligible: (SubtitleCandidate) -> Boolean = { it.key !in tried && AddonLanguages.normalise(it.language) == lang }
            val next = now.candidates.firstOrNull { it.provider == null && eligible(it) }
                ?: now.candidates.firstOrNull(eligible)
            if (next == null) {
                if (now.done) return false
                _results.first { it !== now }
                continue
            }
            tried += next.key
            attempts++
            if (load(next, choice)) return true
            if (attempts >= MAX_ATTEMPTS) return false
        }
        return false
    }

    // ---- Choices (FR-97) ----

    /** A subtitle chosen in the picker: downloaded, side-loaded at the current position, timing back at 0. */
    fun choose(candidate: SubtitleCandidate, done: () -> Unit) {
        val revision = manualChoice()
        selectionJob = scope.launch {
            _loading.value = true
            _message.value = null
            try {
                if (load(candidate, revision)) {
                    reload()
                    done()
                }
            } finally {
                if (choice == revision) _loading.value = false
            }
        }
    }

    fun chooseEmbedded(track: TrackItem) {
        manualChoice()
        forgetText()
        _pick.value = SubtitlePick.Embedded(AddonLanguages.normalise(track.language), track.group, track.index)
        select()
    }

    fun off() {
        manualChoice()
        forgetText()
        _pick.value = SubtitlePick.Off
        select()
    }

    private fun manualChoice(): Long {
        manual = true
        choice++
        automaticJob?.cancel()
        selectionJob?.cancel()
        _loading.value = false
        return choice
    }

    private fun forgetText() {
        text = null
        format = null
        sideKey = null
        _sync.value = SyncState()
        SideSubtitles.clear()
    }

    private suspend fun load(candidate: SubtitleCandidate, revision: Long): Boolean {
        val result = source.download(candidate.key)
        if (choice != revision) return false
        return when (result) {
            is SubtitleDownload.Failed -> {
                _message.value = result.message
                false
            }
            is SubtitleDownload.Ready -> {
                val bytes = encode(result.text, result.format, 0)
                if (choice != revision) return false
                text = result.text
                format = result.format
                language = AddonLanguages.normalise(candidate.language)
                _sync.value = SyncState()
                sideKey = SideSubtitles.put(bytes)
                _pick.value = SubtitlePick.Addon(candidate, language)
                true
            }
        }
    }

    /** The text shifted by [offsetMs] (off the main thread: regex work), kept as the one side subtitle. */
    private suspend fun store(offsetMs: Long): Boolean {
        val revision = choice
        val source = text ?: return false
        val fmt = format ?: return false
        val bytes = encode(source, fmt, offsetMs)
        if (choice != revision) return false
        sideKey = SideSubtitles.put(bytes)
        return true
    }

    private suspend fun encode(source: String, fmt: SubtitleFormat, offsetMs: Long): ByteArray =
        withContext(work) { SubtitleText.shift(source, fmt, offsetMs).toByteArray(Charsets.UTF_8) }

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
        val revision = choice
        _sync.value = s.copy(applying = true, failed = false)
        scope.launch {
            if (!store(s.draftMs)) return@launch
            reload()
            val back = readyAgain(APPLY_TIMEOUT_MS)
            if (choice != revision) return@launch
            _sync.value = _sync.value.copy(appliedMs = if (back) s.draftMs else _sync.value.appliedMs, applying = false, failed = !back)
        }
    }

    fun release() {
        manualChoice()
        requestJob?.cancel()
        forgetText()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val HELD_REPEATS = 8
        const val APPLY_TIMEOUT_MS = 20_000L
    }
}
