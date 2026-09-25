package com.sohva.tv.feature.player

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.player.SkipLadder
import com.sohva.tv.core.model.player.SkipStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where a timeshift stream is: position and duration in ms (duration 0 when unknown), and whether it plays. */
@Immutable
data class Progress(val position: Long, val duration: Long, val playing: Boolean) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** The last skip's signed distance, shown for 900 ms (PLAY-FR-63); [serial] tells two equal skips apart. */
@Immutable
data class SkipFeedback(val millis: Long, val back: Boolean, val serial: Int)

/**
 * The transport controls of catch-up (and, from M4, films): spec 30 §4.5 and §4.8. Shown on entry
 * and every reveal, hidden after 5 s unless a control has focus or a picker is open; position is
 * polled every 500 ms only while they are visible (§9), and nothing ticks while they are hidden.
 */
class PlayerTransport internal constructor(private val model: PlayerModel, private val scope: CoroutineScope) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _progress = MutableStateFlow(Progress(0, 0, false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    /** A new serial asks the screen to focus Play/Pause (PLAY-FR-49). */
    private val _focusPlay = MutableStateFlow(0)
    val focusPlayRequest: StateFlow<Int> = _focusPlay.asStateFlow()

    private val _feedback = MutableStateFlow<SkipFeedback?>(null)
    val feedback: StateFlow<SkipFeedback?> = _feedback.asStateFlow()

    private var ladder = SkipLadder(SkipStep.TEN_SECONDS)
    private var hideTimer: Job? = null
    private var poller: Job? = null
    private var feedbackTimer: Job? = null

    /** Whether one of the controls holds focus: then they do not hide (PLAY-FR-46). */
    var focused: Boolean = false
        set(value) {
            field = value
            if (!value && _visible.value) restartTimer()
        }

    val step: SkipStep get() = model.settings.skipStep

    fun useStep(step: SkipStep) {
        ladder = SkipLadder(step)
    }

    /** Shows the controls; [focusPlay] also focuses Play/Pause (entering them by remote). */
    fun show(focusPlay: Boolean) {
        _visible.value = true
        restartTimer()
        startPolling()
        if (focusPlay) _focusPlay.value++
    }

    fun hide() {
        hideTimer?.cancel()
        poller?.cancel()
        focused = false
        _visible.value = false
    }

    fun togglePlay() {
        val c = model.controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        touch()
    }

    /** Skips by the ladder's distance (PLAY-FR-61, -62): not below 0, not past a known duration. */
    fun skip(back: Boolean) {
        val c = model.controller ?: return
        val distance = ladder.skip(if (back) -1 else 1, model.now())
        val duration = c.duration.takeIf { it > 0 }
        val target = (c.currentPosition + if (back) -distance else distance).coerceAtLeast(0).let { if (duration != null) it.coerceAtMost(duration) else it }
        c.seekTo(target)
        _feedback.value = SkipFeedback(distance, back, (_feedback.value?.serial ?: 0) + 1)
        feedbackTimer?.cancel()
        feedbackTimer = scope.launch {
            delay(FEEDBACK_MS)
            _feedback.value = null
        }
        touch()
    }

    fun restart() {
        model.controller?.seekTo(0)
        touch()
    }

    /** A button press restarts the idle timer (PLAY-FR-49) and refreshes the figures at once. */
    private fun touch() {
        if (_visible.value) restartTimer()
        poll()
    }

    private fun restartTimer() {
        hideTimer?.cancel()
        hideTimer = scope.launch {
            delay(HIDE_MS)
            if (!focused && model.picker.value == null) hide()
        }
    }

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (true) {
                poll()
                delay(POLL_MS)
            }
        }
    }

    private fun poll() {
        val c = model.controller ?: return
        _progress.value = Progress(c.currentPosition.coerceAtLeast(0), c.duration.takeIf { it > 0 } ?: 0, c.isPlaying)
    }

    companion object {
        const val HIDE_MS: Long = 5_000
        const val POLL_MS: Long = 500
        const val FEEDBACK_MS: Long = 900
    }
}
