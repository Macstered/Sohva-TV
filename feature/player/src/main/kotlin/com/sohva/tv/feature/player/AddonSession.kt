package com.sohva.tv.feature.player

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.sohva.tv.core.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
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

    val key: String get() = "addon:$token"

    /** FR-86: prepare paused, subtitles within 5 s (M9 part 4), the first frame, access again, then play. */
    fun start(c: MediaController, from: Long = play.startMs) {
        _stop.value = null
        _stage.value = AddonStage.PREPARING
        ended = false
        scope.launch {
            startedAt = SystemClock.elapsedRealtime()
            if (!env.stillAllowed(token)) return@launch fail()
            ready.value = false
            firstFrame.value = false
            failed.value = false
            c.stop()
            c.clearMediaItems()
            val extras = Bundle().apply { putBoolean(PlaybackService.EXTRA_ADDON, true) }
            c.setMediaItem(MediaItem.Builder().setMediaId(token).setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build()).build(), from)
            c.playWhenReady = false
            c.prepare()
            withTimeoutOrNull(PREPARE_MS) { combine(ready, failed) { r, f -> r || f }.first { it } }
            if (!ready.value || failed.value) return@launch fail()
            env.milestone("stream-ready", elapsed())
            _stage.value = AddonStage.SUBTITLES
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
        const val CHECK_MS = 5_000L
        const val MOVED_MS = 5_000L
    }
}
