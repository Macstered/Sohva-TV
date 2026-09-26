package com.sohva.tv.feature.player

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.sohva.tv.core.player.PlaybackService
import com.sohva.tv.core.model.player.AddonLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The loading screen's stage text (spec 50 FR-86). */
enum class AddonStage { PREPARING, SUBTITLES, STARTING }

/** Why an addon playback stopped (FR-91): an error or access loss, or the app going to the background. */
enum class AddonStop { FAILED, BACKGROUND }

/**
 * An addon playback in the player (spec 50 §4.12): the start-up stages behind the loading screen,
 * the 5 s re-validation and progress snapshot, the end, the background stop, and Retry with a
 * fresh source. Nothing restarts unattended.
 */
class AddonSession internal constructor(
    private val model: PlayerModel,
    private val env: AddonPlaybackEnv,
    val play: AddonPlay,
    private val scope: CoroutineScope,
    /** Where subtitle text is shifted: off the main thread (performance rule 1). */
    work: CoroutineDispatcher,
) {
    private val _stage = MutableStateFlow<AddonStage?>(AddonStage.PREPARING)
    val stage: StateFlow<AddonStage?> = _stage.asStateFlow()

    private val _stop = MutableStateFlow<AddonStop?>(null)
    val stop: StateFlow<AddonStop?> = _stop.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)

    /** "Watch progress could not be saved." (FR-91). */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private var token = play.token
    // Separate flags, not one event: ExoPlayer renders the first frame while paused, often before READY.
    private val ready = MutableStateFlow(false)
    private val firstFrame = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private var startedAt = 0L
    private var sequence = 0L
    private var lastSaved = -1L
    private var lastPlaying: Boolean? = null
    private var ended = false
    private var loop: Job? = null
    private var traktApplied = false
    private var waitingForPicker = false
    private var trackPrefsSet = false

    val key: String get() = "addon:$token"

    val subtitles: AddonSubtitles = AddonSubtitles(
        env, { token }, { model.controller }, { model.tracks.value }, { model.settings.vodLanguages }, scope, work,
        reload = ::reload, readyAgain = ::readyAgain,
    )

    /** FR-86: prepare paused, subtitles within 5 s (M9 part 4), the first frame, access again, then play. */
    fun start(c: MediaController, from: Long = play.startMs) {
        _stop.value = null
        _stage.value = AddonStage.PREPARING
        ended = false
        scope.launch {
            startedAt = SystemClock.elapsedRealtime()
            if (!env.stillAllowed(token)) return@launch fail()
            if (!trackPrefsSet) {
                trackPrefsSet = true
                trackPreferences(c)
            }
            prepare(c, from, playWhenReady = false)
            if (!readyAgain(PREPARE_MS)) return@launch fail()
            env.milestone("stream-ready", elapsed())
            _stage.value = AddonStage.SUBTITLES
            // FR-98: one bounded automatic attempt; a retry keeps the choice already made.
            if (subtitles.pick.value == null) {
                val loaded = withTimeoutOrNull(SUBTITLE_MS) { subtitles.auto() }
                if (loaded == null) subtitles.embeddedFallback()
                if (loaded == true) {
                    prepare(c, c.currentPosition, playWhenReady = false)
                    if (!readyAgain(PREPARE_MS)) return@launch fail()
                }
            }
            subtitles.select()
            env.milestone("subtitles-ready", elapsed())
            _stage.value = AddonStage.STARTING
            val audioOnly = !c.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            if (!audioOnly) withTimeoutOrNull(FIRST_FRAME_MS) { combine(firstFrame, failed) { r, f -> r || f }.first { it } }
            if (failed.value) return@launch fail()
            env.milestone("first-frame", elapsed())
            if (!env.stillAllowed(token)) return@launch fail()
            _stage.value = null
            // A picker opened during start-up keeps playback paused until it closes (FR-86 step 4).
            if (model.picker.value == null) c.play() else waitingForPicker = true
            env.milestone("playing", elapsed())
            model.reveal()
            startLoop()
        }
    }

    /**
     * FR-85: audio follows the primary and secondary audio preferences; text starts disabled so no
     * unrelated default track flashes while the automatic choice runs.
     */
    private fun trackPreferences(c: MediaController) {
        val prefs = model.settings.vodLanguages
        val audio = listOfNotNull(prefs.audio, prefs.audioSecond).mapNotNull(AddonLanguages::normalise).distinct()
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*audio.toTypedArray())
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** The stream by its token, with the current side-loaded subtitle, at [from]. */
    private fun prepare(c: MediaController, from: Long, playWhenReady: Boolean) {
        ready.value = false
        firstFrame.value = false
        failed.value = false
        c.stop()
        c.clearMediaItems()
        val extras = Bundle().apply { putBoolean(PlaybackService.EXTRA_ADDON, true) }
        subtitles.extras(extras)
        c.setMediaItem(MediaItem.Builder().setMediaId(token).setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build()).build(), from)
        c.playWhenReady = playWhenReady
        c.prepare()
    }

    /** A subtitle choice or new timing (FR-97, -102): the same place, playing or paused as it was. */
    private fun reload() {
        val c = model.controller ?: return
        prepare(c, c.currentPosition.coerceAtLeast(0), c.playWhenReady)
    }

    private suspend fun readyAgain(timeoutMs: Long): Boolean {
        withTimeoutOrNull(timeoutMs) { combine(ready, failed) { r, f -> r || f }.first { it } }
        return ready.value && !failed.value
    }

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt

    /** Every 5 s while it runs: access again, and progress when it moved 5 s or the state changed (FR-84, -106). */
    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            while (true) {
                delay(CHECK_MS)
                if (!env.stillAllowed(token)) {
                    snapshot()
                    model.controller?.stop()
                    _stop.value = AddonStop.FAILED
                    return@launch
                }
                snapshot()
            }
        }
    }

    /** One progress write; skipped when nothing moved (§9 "During playback"). */
    fun snapshot(force: Boolean = false, end: Boolean = false) {
        val c = model.controller ?: return
        val position = c.currentPosition.coerceAtLeast(0)
        val duration = c.duration.takeIf { it > 0 }
        val playing = c.isPlaying
        if (!force && !end && lastSaved >= 0 && kotlin.math.abs(position - lastSaved) < MOVED_MS && playing == lastPlaying) return
        lastSaved = position
        lastPlaying = playing
        val seq = ++sequence
        env.saveProgress(token, position, duration, end, seq) { _saveFailed.value = true }
    }

    fun onState(state: Int) {
        when (state) {
            Player.STATE_READY -> {
                ready.value = true
                applyTrakt()
            }
            Player.STATE_ENDED -> if (!ended && _stage.value == null) {
                // The first end only: pause, buffering, errors and background stops never advance (FR-93).
                ended = true
                loop?.cancel()
                snapshot(end = true)
                model.navigation.addonFinished(token)
            }
        }
    }

    fun onPlaying(playing: Boolean) {
        if (!playing && _stage.value == null && !ended) snapshot(force = true)
    }

    /** The picker closed: true when start-up finished under it and playback should begin now. */
    fun pickerClosed(): Boolean {
        val waiting = waitingForPicker
        waitingForPicker = false
        return waiting
    }

    fun onFirstFrame() {
        firstFrame.value = true
    }

    fun onError() {
        failed.value = true
        if (_stage.value == null) {
            snapshot(force = true)
            loop?.cancel()
            _stop.value = AddonStop.FAILED
        }
    }

    /** FR-88: a newer Trakt pause seeks once the duration is known. */
    private fun applyTrakt() {
        val fraction = play.traktFraction ?: return
        if (traktApplied) return
        val c = model.controller ?: return
        val duration = c.duration.takeIf { it > 0 } ?: return
        traktApplied = true
        c.seekTo((duration * fraction).toLong())
    }

    /** ON_STOP (FR-91): progress saved, the player stopped, and a Retry offered on return. */
    fun onBackground() {
        snapshot(force = true)
        loop?.cancel()
        model.controller?.stop()
        if (_stage.value == null && !ended) _stop.value = AddonStop.BACKGROUND
    }

    /** Retry with fresh source (FR-91): the same provider's matching stream, at the current position. */
    fun retry() {
        val c = model.controller ?: return
        val at = c.currentPosition.coerceAtLeast(0)
        scope.launch {
            val fresh = env.freshToken(token)
            if (fresh == null) {
                _stop.value = AddonStop.FAILED
                return@launch
            }
            token = fresh
            start(c, at)
        }
    }

    fun release() {
        subtitles.release()
        loop?.cancel()
        snapshot(force = true)
    }

    private fun fail() {
        _stage.value = null
        _stop.value = AddonStop.FAILED
        model.controller?.stop()
    }

    private companion object {
        const val PREPARE_MS = 45_000L
        const val FIRST_FRAME_MS = 20_000L
        const val SUBTITLE_MS = 5_000L
        const val CHECK_MS = 5_000L
        const val MOVED_MS = 5_000L
    }
}
