package com.sohva.tv.feature.player

import com.sohva.tv.core.model.profile.ChannelAdmission
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks as MediaTracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.model.guide.GuideWindow
import com.sohva.tv.core.model.player.PictureShape
import com.sohva.tv.core.model.player.PlaybackSettings
import com.sohva.tv.core.model.player.SubtitleChoice
import com.sohva.tv.core.model.player.TrackLanguages
import com.sohva.tv.core.model.text.Initials
import com.sohva.tv.core.model.text.StreamTags
import com.sohva.tv.core.player.PlaybackErrors
import com.sohva.tv.core.player.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The player's state holder (spec 30 §9 "a plain state holder class plus small composables"):
 * the controller, the playing channel, the overlays' flags and their timers. Overlays are
 * composed only while their flag is up; nothing here ticks while they are hidden. With an
 * [archive] window it plays that programme from the provider's archive (spec 22): the transport
 * controls replace the live box and channels do not change.
 */
@OptIn(UnstableApi::class)
class PlayerModel(
    private val env: PlayerEnvironmentUi,
    firstChannel: String,
    val navigation: PlayerNavigation,
    val archive: ArchiveWindow? = null,
    /** False when a reminder or a notification started playback: not a recent channel (CHAN-FR-61). */
    private val recordFirst: Boolean = true,
    /** A film or an episode instead of a channel (spec 30 §3.1): transport controls, no channels. */
    val vod: VodPlay? = null,
    /** The first channel already passed the group check and lock (spec 01 SHELL-FR-20, -23). */
    private val firstAdmitted: Boolean = false,
    /** A Discover addon stream (spec 50 §4.12): the loading screen first, transport controls, no channels. */
    addonPlay: AddonPlay? = null,
) : ViewModel() {
    /** Live TV, as opposed to catch-up, VOD and addon streams ("timeshift" in spec 31). */
    val live: Boolean get() = archive == null && vod == null && addon == null

    private val _title = MutableStateFlow<String?>(null)

    /** The session's title (spec 30 PLAY-FR-47): the film or episode as the service resolved it. */
    val title: StateFlow<String?> = _title.asStateFlow()

    /** The end of a film or an episode is handled once per item (PLAY-FR-132). */
    private var finished = false

    /** VOD language preferences are applied once per item (PLAY-FR-75), never over the viewer's own choice. */
    private var languagesApplied = false
    private var audioByHand = false
    private var textByHand = false

    private val reads = env.reads

    private val _connection = MutableStateFlow(Connection.CONNECTING)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _playing = MutableStateFlow<Playing?>(null)
    val playing: StateFlow<Playing?> = _playing.asStateFlow()

    private val _box = MutableStateFlow(false)

    /** The live information box (PLAY-FR-43): shown on interaction only. */
    val boxVisible: StateFlow<Boolean> = _box.asStateFlow()
    private val _nowNext = MutableStateFlow(NowNext(null, null))
    val nowNext: StateFlow<NowNext> = _nowNext.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _shape = MutableStateFlow(PictureShape.FIT)
    val shape: StateFlow<PictureShape> = _shape.asStateFlow()

    private val _video = MutableStateFlow(VideoSize.UNKNOWN)
    val videoSize: StateFlow<VideoSize> = _video.asStateFlow()

    private val _tracks = MutableStateFlow(Tracks())
    val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private val _picker = MutableStateFlow<Picker?>(null)
    val picker: StateFlow<Picker?> = _picker.asStateFlow()

    private val _quick = MutableStateFlow(false)
    val quickActions: StateFlow<Boolean> = _quick.asStateFlow()

    private val _stats = MutableStateFlow(false)
    val statsOn: StateFlow<Boolean> = _stats.asStateFlow()

    private val _external = MutableStateFlow(false)

    /** "Opening…" while another player app is being started (PLAY-FR-44). */
    val externalBusy: StateFlow<Boolean> = _external.asStateFlow()

    private val _frameRate = MutableStateFlow<Float?>(null)

    /** The selected video format's frame rate, for matching the display (PLAY-FR-103, -105). */
    val frameRate: StateFlow<Float?> = _frameRate.asStateFlow()

    val channels = PlayerChannels(this, reads, viewModelScope)
    val dial = PlayerDial(this, reads, viewModelScope)
    val keys = PlayerKeys(this)
    val transport = PlayerTransport(this, viewModelScope)
    val addon: AddonSession? = addonPlay?.let { play -> env.addon?.let { AddonSession(this, it, play, viewModelScope) } }

    /** The action row asks for focus when Up/Down step into the box (PLAY-FR-45); serial = a new request. */
    private val _boxFocus = MutableStateFlow(0)
    val boxFocusRequest: StateFlow<Int> = _boxFocus.asStateFlow()

    var controller: MediaController? = null
        private set
    var settings: PlaybackSettings = PlaybackSettings()
        private set

    /** Whether one of the box's buttons holds focus: then it does not time out (PLAY-FR-43). */
    var boxFocused: Boolean = false
        set(value) {
            field = value
            if (!value && _box.value) restartBoxTimer()
        }

    /** The channel watched before this one, for zap-back (PLAY-FR-58). */
    private var previousKey: String? = null
    private var attempt = 0
    private var boxTimer: Job? = null
    private var boxTicker: Job? = null
    private var reconnectJob: Job? = null

    private val listener = Listener()

    init {
        viewModelScope.launch { env.remoteMapping.collect(keys::useMapping) }
        viewModelScope.launch {
            settings = env.settings()
            transport.useStep(settings.skipStep)
            try {
                controller = env.client.connect(listener).also { it.addListener(listener) }
                _connection.value = Connection.READY
                val session = addon
                when {
                    session != null -> startAddon(session)
                    vod != null -> playVod(vod)
                    else -> play(firstChannel, record = recordFirst, admitted = firstAdmitted)
                }
            } catch (e: Exception) {
                _connection.value = Connection.FAILED
            }
        }
    }

    // ---- Playing a channel ---------------------------------------------------------------------

    /**
     * Stops the old stream, clears its item (the service releases its lease) and opens [key]
     * (PLAY-FR-22..23). Every zap records the channel as recent (PLAY-FR-57). Unless [admitted],
     * the profile's group check and the lock come first (spec 01 SHELL-FR-22): a refused channel
     * leaves the current one playing; a locked one asks for the PIN over the player.
     */
    fun play(key: String, record: Boolean = true, admitted: Boolean = false) {
        val c = controller ?: return
        viewModelScope.launch {
            if (!admitted) {
                when (env.admit(key)) {
                    ChannelAdmission.PLAY -> Unit
                    ChannelAdmission.REFUSED -> {
                        navigation.refused()
                        // Nothing playing yet (a start route's last channel): the guide instead.
                        if (_playing.value == null) navigation.guide()
                        return@launch
                    }
                    ChannelAdmission.LOCKED -> {
                        navigation.unlock(key)
                        return@launch
                    }
                }
            }
            val current = _playing.value?.channel?.key
            if (current != null && current != key) previousKey = current
            val channel = reads.channel(key) ?: run {
                // A channel gone before anything played (a stale "Last channel") falls back to the guide.
                if (_playing.value == null) navigation.guide() else _banner.value = Banner(BannerReason.Unavailable, 0, 0, stopped = true)
                return@launch
            }
            _playing.value = format(channel)
            _nowNext.value = NowNext(null, null)
            attempt = 0
            reconnectJob?.cancel()
            _banner.value = null
            c.stop()
            c.clearMediaItems()
            c.setMediaItem(itemFor(key))
            c.prepare()
            c.play()
            if (record) env.recordWatched(key)
            channels.onPlaying(channel)
            reveal()
        }
    }

    /** A film or an episode from its resume position (PLAY-FR-01, spec 40 VOD-FR-64). */
    private fun playVod(request: VodPlay) {
        val c = controller ?: return
        finished = false
        c.stop()
        c.clearMediaItems()
        val extras = Bundle().apply { putBoolean(PlaybackService.EXTRA_VOD, true) }
        val item = MediaItem.Builder().setMediaId(request.contentKey)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build()).build()
        c.setMediaItem(item, request.startMs)
        c.prepare()
        c.play()
        reveal()
    }

    /** FR-89: the controls hide even when focused, but stay while paused, buffering or stopped. */
    private fun startAddon(session: AddonSession) {
        val c = controller ?: return
        transport.autoHideWhileFocused = true
        transport.hold = { !c.playWhenReady || _buffering.value || session.stop.value != null }
        session.start(c)
    }

    /** The channel or the film playing: what Back and Leave report to the app. */
    fun currentKey(): String? = _playing.value?.channel?.key ?: vod?.contentKey ?: addon?.key

    /** A catch-up item carries the programme's times for the service (spec 22 CATCH-FR-40). */
    private fun itemFor(key: String): MediaItem {
        val builder = MediaItem.Builder().setMediaId(key)
        val window = archive ?: return builder.build()
        val extras = Bundle().apply {
            putLong(PlaybackService.EXTRA_ARCHIVE_START, window.start)
            putLong(PlaybackService.EXTRA_ARCHIVE_STOP, window.stop)
        }
        return builder.setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build()).build()
    }

    private suspend fun format(channel: LiveChannel): Playing = kotlinx.coroutines.withContext(env.format) {
        Playing(channel, channel.number, StreamTags.of(channel.name), Initials.of(channel.name))
    }

    /** Zap-back, and again to return (PLAY-FR-58). Returns false when there is no previous channel. */
    fun switchToPrevious(): Boolean {
        val previous = previousKey ?: return false
        if (previous == _playing.value?.channel?.key) return false
        play(previous)
        return true
    }

    fun hasPrevious(): Boolean = previousKey != null && previousKey != _playing.value?.channel?.key

    // ---- Chrome ----------------------------------------------------------------------------------

    /** Shows the live box for 5 s (PLAY-FR-43), or in catch-up the transport controls; not while the channel list is open. */
    fun reveal() {
        if (!live) {
            transport.show(focusPlay = false)
            return
        }
        if (channels.open.value) return
        _box.value = true
        restartBoxTimer()
        startBoxTicker()
    }

    fun hideBox() {
        boxTimer?.cancel()
        boxTicker?.cancel()
        _box.value = false
    }

    /** Up/Down pressed while the box was visible: focus its action row (PLAY-FR-45). */
    fun focusBox() {
        reveal()
        _boxFocus.value++
    }

    private fun restartBoxTimer() {
        boxTimer?.cancel()
        boxTimer = viewModelScope.launch {
            delay(BOX_MS)
            if (!boxFocused) hideBox()
        }
    }

    /** Now and next while the box is shown, every 30 s (PLAY-FR-40, §9 "only while visible"). */
    private fun startBoxTicker() {
        if (boxTicker?.isActive == true) return
        boxTicker = viewModelScope.launch {
            while (true) {
                refreshNowNext()
                delay(NOW_TICK_MS)
            }
        }
    }

    private suspend fun refreshNowNext() {
        val channel = _playing.value?.channel ?: return
        val epg = channel.epgId ?: run {
            _nowNext.value = NowNext(null, null)
            return
        }
        val source = reads.sources.first().firstOrNull { it.id == channel.sourceId } ?: return
        val now = env.clock.wallMillis()
        val schedule = reads.schedules(source, listOf(epg), GuideWindow.anchor(now))[epg].orEmpty()
        val current = schedule.firstOrNull { it.isLive(now) }
        val nextStart = current?.stop ?: now
        _nowNext.value = NowNext(current, schedule.firstOrNull { it.start >= nextStart })
    }

    fun now(): Long = env.clock.wallMillis()

    fun cycleShape() {
        _shape.value = _shape.value.next()
    }

    fun toggleStats() {
        _stats.value = !_stats.value
    }

    /** The score ticker where Sohva Sport offers it (PLAY-31). */
    val ticker: ScoreTickerSource? get() = env.ticker

    /** The mapped Score ticker action or quick actions (PLAY-FR-120); false where there is none. */
    fun toggleTicker(): Boolean {
        val t = env.ticker ?: return false
        t.toggle()
        return true
    }

    fun openPicker(which: Picker) {
        _quick.value = false
        _picker.value = which
    }

    fun closePicker() {
        _picker.value = null
        if (resumeAfterPicker || addon?.pickerClosed() == true) controller?.play()
        resumeAfterPicker = false
        afterOverlay()
    }

    /** Whether closing the picker plays again: it paused a running addon stream (spec 50 FR-97). */
    private var resumeAfterPicker = false

    /** The addon player's Subtitles (FR-87, -97): pauses, then opens the picker; start-up continues underneath. */
    fun pauseAndPick(which: Picker) {
        val c = controller ?: return
        resumeAfterPicker = addon?.stage?.value == null && c.playWhenReady
        c.pause()
        openPicker(which)
    }

    fun openQuickActions() {
        if (_picker.value != null) return
        _quick.value = true
    }

    fun closeQuickActions() {
        _quick.value = false
        afterOverlay()
    }

    /** Closing an overlay reveals the chrome; in catch-up focus goes to Play/Pause (PLAY-FR-09). */
    private fun afterOverlay() {
        if (live) reveal() else transport.show(focusPlay = true)
    }

    // ---- Tracks (PLAY-FR-70..74) -----------------------------------------------------------------

    fun chooseTrack(item: TrackItem?, type: Int) {
        if (type == C.TRACK_TYPE_AUDIO) audioByHand = true else textByHand = true
        select(item, type)
        closePicker()
    }

    private fun select(item: TrackItem?, type: Int) {
        val c = controller ?: return
        val builder = c.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (item == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val group = c.currentTracks.groups.getOrNull(item.group) ?: return
            builder.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, item.index))
        }
        c.trackSelectionParameters = builder.build()
    }

    /** Once the audio tracks of a film or episode are known (PLAY-FR-75); live and catch-up keep the stream's own. */
    private fun applyLanguages(tracks: Tracks) {
        if ((vod == null && addon == null) || languagesApplied || tracks.audio.isEmpty()) return
        languagesApplied = true
        viewModelScope.launch {
            val prefs = env.settings().vodLanguages
            val choice = TrackLanguages.choose(prefs, tracks.audio.map { it.language }, tracks.text.map { it.language }, audioByHand, textByHand)
            choice.audio?.let { select(tracks.audio[it], C.TRACK_TYPE_AUDIO) }
            when (val subtitles = choice.subtitles) {
                SubtitleChoice.Keep -> Unit
                SubtitleChoice.Off -> select(null, C.TRACK_TYPE_TEXT)
                is SubtitleChoice.Track -> select(tracks.text[subtitles.index], C.TRACK_TYPE_TEXT)
            }
        }
    }

    /** Next audio track (mapped action): needs two or more (PLAY-FR-73). */
    fun nextAudio(): Boolean {
        val audio = _tracks.value.audio
        if (audio.size < 2) return false
        val at = audio.indexOfFirst { it.selected }
        chooseTrack(audio[(at + 1) % audio.size], C.TRACK_TYPE_AUDIO)
        return true
    }

    /** Subtitles on/off (mapped action): off when any is on, else the first track (PLAY-FR-73). */
    fun toggleSubtitles(): Boolean {
        val text = _tracks.value.text
        if (text.isEmpty()) return false
        chooseTrack(if (text.any { it.selected }) null else text.first(), C.TRACK_TYPE_TEXT)
        return true
    }

    // ---- Errors and reconnection (PLAY-FR-90..95) -------------------------------------------------

    /** The viewer's Reconnect: a fresh set of attempts (PLAY-FR-91). */
    fun reconnect() {
        attempt = 0
        reconnectJob?.cancel()
        _banner.value = null
        controller?.let {
            it.prepare()
            it.play()
        }
    }

    private fun onError(error: PlaybackException) {
        val c = controller ?: return
        // Addon streams never reconnect on their own: Retry with fresh source (spec 50 FR-91).
        addon?.let {
            env.logFailure("addon: ${PlaybackErrors.of(error).detail}")
            it.onError()
            return
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            // Not an attempt: back to the live edge (PLAY-FR-93 table).
            c.seekToDefaultPosition()
            c.prepare()
            return
        }
        val failure = PlaybackErrors.of(error)
        val policy = settings.reconnect
        attempt++
        val delayMs = policy.delayBefore(attempt)
        _banner.value = Banner(BannerReason.Cause(failure.cause, failure.detail), attempt, policy.attempts, stopped = delayMs == null)
        env.logFailure("${_playing.value?.channel?.key}: ${failure.detail}, attempt $attempt")
        if (delayMs != null) {
            reconnectJob?.cancel()
            reconnectJob = viewModelScope.launch {
                delay(delayMs)
                c.prepare()
                c.play()
            }
        }
    }

    // ---- Another player (PLAY-FR-115..116) ------------------------------------------------------------

    /**
     * Stops the stream and clears it (the lease goes back), waits 150 ms, then hands the live
     * address to another app. Success leaves the player; failure says why and plays again.
     */
    fun openExternal() {
        if (_external.value) return
        val c = controller ?: return
        val key = _playing.value?.channel?.key ?: return
        _external.value = true
        c.stop()
        c.clearMediaItems()
        viewModelScope.launch {
            delay(EXTERNAL_WAIT_MS)
            val stream = env.externalStream(key)
            val error = if (stream == null) null else navigation.openExternal(stream)
            _external.value = false
            when {
                stream == null -> {
                    _banner.value = Banner(BannerReason.Unavailable, 0, 0, stopped = true)
                    play(key, record = false)
                }
                error == null -> navigation.leave(key)
                else -> {
                    play(key, record = false)
                    _banner.value = Banner(BannerReason.ExternalFailed(com.sohva.tv.core.model.diagnostics.Redactor.redact(error.message).orEmpty()), 0, 0, stopped = true)
                }
            }
        }
    }

    // ---- Lifecycle (PLAY-FR-20) ------------------------------------------------------------------

    fun onStop() {
        addon?.let { return it.onBackground() }
        controller?.stop()
    }

    fun onStart() {
        // Nothing restarts unattended after a background stop (spec 50 FR-91).
        if (addon != null) return
        val c = controller ?: return
        if (c.mediaItemCount > 0) {
            c.prepare()
            c.play()
        }
    }

    override fun onCleared() {
        addon?.release()
        controller?.let {
            it.removeListener(listener)
            it.stop()
            it.clearMediaItems()
        }
        env.client.release()
    }

    private inner class Listener : Player.Listener, MediaController.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _buffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                // A recovered stream gets a fresh set of attempts (PLAY-FR-92).
                attempt = 0
                _banner.value = null
            }
            addon?.onState(playbackState)
            val request = vod
            if (playbackState == Player.STATE_ENDED && request != null && !finished) {
                finished = true
                navigation.finished(request.contentKey)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            _title.value = mediaMetadata.title?.toString()
        }

        override fun onPlayerError(error: PlaybackException) = onError(error)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            addon?.onPlaying(isPlaying)
        }

        override fun onRenderedFirstFrame() {
            addon?.onFirstFrame()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _video.value = videoSize
        }

        override fun onTracksChanged(tracks: MediaTracks) {
            _tracks.value = tracksOf(tracks)
            applyLanguages(_tracks.value)
            _frameRate.value = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it).frameRate } }
                ?.takeIf { it > 0f }
        }

        /** The engine's own refusals before playback (PLAY-FR-95). */
        override fun onError(controller: MediaController, sessionError: SessionError) {
            addon?.let { return it.onError() }
            val code = sessionError.extras.getInt(PlaybackService.EXTRA_CODE)
            val reason = when (code) {
                PlaybackErrors.CONNECTION_LIMIT -> BannerReason.ConnectionLimit(
                    sessionError.extras.getString(PlaybackErrors.EXTRA_SOURCE_NAME).orEmpty(),
                    sessionError.extras.getInt(PlaybackErrors.EXTRA_LIMIT),
                )
                else -> BannerReason.Unavailable
            }
            _banner.value = Banner(reason, 0, 0, stopped = true)
        }
    }

    private fun tracksOf(tracks: MediaTracks): Tracks {
        val audio = ArrayList<TrackItem>()
        val text = ArrayList<TrackItem>()
        tracks.groups.forEachIndexed { g, group ->
            val target = when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> text
                else -> return@forEachIndexed
            }
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                target += TrackItem(g, i, f.label, f.language, if (f.channelCount > 0) f.channelCount else 0, group.isTrackSelected(i))
            }
        }
        return Tracks(audio, text)
    }

    companion object {
        const val BOX_MS: Long = 5_000
        const val NOW_TICK_MS: Long = 30_000
        const val EXTERNAL_WAIT_MS: Long = 150
    }
}
