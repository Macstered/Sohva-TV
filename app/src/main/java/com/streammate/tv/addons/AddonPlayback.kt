package com.streammate.tv.addons

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleExtractor
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMatePlaybackService
import com.streammate.tv.app.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.io.IOException

/** Addon adapter. No service/controller extras carry configured URLs; no IPTV history writes. */
@OptIn(UnstableApi::class)
internal class AddonPlayback(
    context: Context, private val host: AddonHost, private val profileId: String,
    private val identity: AddonWatchIdentity, private val title: String,
    initial: AddonPlaybackSelection,
    private val artwork: AddonWatchArtwork? = null,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
    var selection by mutableStateOf(initial); private set
    var ready by mutableStateOf(false); private set
    var firstFrameReady by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var progressFailure by mutableStateOf(false); private set
    var positionMillis by mutableLongStateOf(0L); private set
    var durationMillis by mutableLongStateOf(0L); private set
    var selectedSubtitle by mutableStateOf<String?>(null); private set
    var subtitleDelayMillis by mutableLongStateOf(0L); private set
    var subtitleTimingSupported by mutableStateOf(false); private set
    var selectedSubtitleChoice by mutableStateOf<String?>(null); private set
    var embeddedSubtitleTracks by mutableStateOf<List<AddonEmbeddedSubtitle>>(emptyList()); private set
    var embeddedSubtitleLanguages by mutableStateOf<List<String>>(emptyList()); private set
    var subtitleResults by mutableStateOf<List<AddonSourceResult<AddonSubtitle>>>(emptyList()); private set
    var subtitleResultsLoading by mutableStateOf(false); private set
    var subtitleResultsFailure by mutableStateOf<AddonFailure?>(null); private set
    private var subtitleResultsLoaded = false
    private val subtitleMutex = Mutex()
    private var manualSubtitleChoice = false
    private var preferredEmbeddedChoice: String? = null
    private var automaticSubtitlesApplied = false
    private var textOff = false
    private var preferredLanguages: List<String> = emptyList()
    private var automaticPreferences: AppPreferences? = null
    private var subtitle: AddonSubtitleData? = null
    private var session: AddonProgressSession? = null
    private var sequence = 0L
    private var everReady = false
    private var released = false
    private var foreground = true
    init {
        check(com.streammate.tv.app.AppRuntimePolicy.forPackage(context.packageName).addonsAllowed)
        // Explicit addon playback must not leave a second IPTV stream running.
        context.stopService(Intent(context, StreamMatePlaybackService::class.java))
        host.activePlayback?.release()
        host.activePlayback = this
        player.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (subtitle != null && !textOff) {
                    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                        (0 until group.length).firstOrNull { group.getTrackFormat(it).id?.endsWith("addon-selected-subtitle") == true }?.let { index ->
                            if (!group.isTrackSelected(index)) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
                        }
                    }
                }
                embeddedSubtitleTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
                    (0 until group.length).filter { group.isTrackSupported(it) && group.getTrackFormat(it).id?.endsWith("addon-selected-subtitle") != true }
                        .map { index -> AddonEmbeddedSubtitle(group.mediaTrackGroup, index, merged = subtitle != null) }
                }
                if (subtitle == null && !textOff) embeddedSubtitleTracks.firstOrNull { it.key == preferredEmbeddedChoice }?.let { target ->
                    if (tracks.groups.none { it.mediaTrackGroup == target.group && it.isTrackSelected(target.index) }) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setOverrideForType(TrackSelectionOverride(target.group, listOf(target.index))).build()
                    }
                }
                embeddedSubtitleLanguages = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && !it.mediaTrackGroup.id.endsWith("addon-selected-subtitle") }
                    .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) && group.getTrackFormat(it).id?.endsWith("addon-selected-subtitle") != true }
                        .mapNotNull { AddonSubtitlePolicy.language(group.getTrackFormat(it).language) } }.distinct()
                val selectedText = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    .firstNotNullOfOrNull { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) } }
                selectedSubtitle = if (textOff || selectedText == null) null
                    else if (selectedText.id?.endsWith("addon-selected-subtitle") == true) subtitle?.language
                    else AddonSubtitlePolicy.language(selectedText.language) ?: selectedText.label ?: context.getString(com.streammate.tv.R.string.addon_ui_embedded)
                subtitleTimingSupported = !textOff && selectedText?.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
                if (subtitle == null && selectedText != null && selectedText.id?.endsWith("addon-selected-subtitle") != true && !textOff) {
                    selectedSubtitleChoice = embeddedSubtitleTracks.firstOrNull { item -> tracks.groups.any {
                        it.mediaTrackGroup == item.group && it.isTrackSelected(item.index)
                    } }?.key
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY
                if (ready) everReady = true
                if (state == Player.STATE_ENDED) snapshot(ended = true)
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; snapshot() }
            override fun onRenderedFirstFrame() { firstFrameReady = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) snapshot() }
        })
    }
    suspend fun start(resume: Boolean, playWhenReady: Boolean = true) {
        host.playbackAccess.check(profileId, selection)
        val origin = host.manager.list(profileId).firstOrNull { it.installationId == identity.metadataInstallationId && it.enabled }
            ?: throw AddonException(AddonFailure.NOT_FOUND)
        check(origin.profileId == profileId)
        host.pendingProgressWrite?.join()
        val previous = host.progress.get(profileId, identity)
        session = host.progress.begin(profileId, identity, title.take(2048), artwork)
        val preferences = host.preferences.first()
        automaticPreferences = preferences
        preferredLanguages = AddonSubtitlePolicy.preferred(preferences.preferredSubtitleLanguage, preferences.secondarySubtitleLanguage)
        textOff = true // Do not briefly display an unrelated default track during discovery.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*listOfNotNull(preferences.preferredAudioLanguage, preferences.secondaryAudioLanguage).toTypedArray())
            .setPreferredTextLanguages(*preferredLanguages.toTypedArray()).build()
        prepare(if (resume) previous?.resumePositionMillis ?: 0 else 0, playWhenReady)
    }
    private suspend fun prepare(position: Long, play: Boolean = true) {
        host.playbackAccess.check(profileId, selection)
        prepareMedia(position, play)
    }
    private fun prepareMedia(position: Long, play: Boolean) {
        if (released || !foreground) return
        failed = false
        ready = false; firstFrameReady = false
        val stream = selection.stream
        val bytes = subtitle
        val httpFactory = OkHttpDataSource.Factory(AddonMediaTransport.client(checkNotNull(stream.url), stream.requestHeaders))
        val factory = DataSource.Factory {
            val upstream = httpFactory.createDataSource()
            object : DataSource {
                private var source: DataSource = upstream
                override fun addTransferListener(listener: TransferListener) = upstream.addTransferListener(listener)
                override fun open(dataSpec: DataSpec): Long {
                    source = if (dataSpec.uri.scheme == "sohva-subtitle" && bytes != null) ByteArrayDataSource(bytes.bytes) else upstream
                    return safe { source.open(dataSpec) }
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = safe { source.read(buffer, offset, length) }
                override fun getUri(): Uri? = source.uri
                override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders
                override fun close() { safe { source.close() } }
                private fun <T> safe(operation: () -> T): T = try { operation() } catch (_: Exception) { throw IOException("Addon media transfer failed") }
            }
        }
        val item = MediaItem.Builder().setMediaId("addon-video").setUri(stream.url)
        val parserFactory = AddonTimingParserFactory(subtitleDelayMillis)
        val videoSource = DefaultMediaSourceFactory(factory, AddonSubtitleExtractors(if (bytes == null) subtitleDelayMillis else 0))
            .setSubtitleParserFactory(parserFactory).createMediaSource(item.build())
        val mediaSource = if (bytes == null) videoSource else {
            val format = Format.Builder().setId("addon-selected-subtitle").setSampleMimeType(bytes.mimeType)
                .setLanguage(bytes.language).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            // Already downloaded and bounded. Use a normal subtitle extractor with
            // a seek map, rather than lazy single-track preparation: applying an
            // offset beyond the last cue must still allow a later backward seek.
            val subtitles = ProgressiveMediaSource.Factory(factory, ExtractorsFactory {
                arrayOf(SubtitleExtractor(parserFactory.create(format), format))
            }).createMediaSource(MediaItem.fromUri("sohva-subtitle://selected"))
            MergingMediaSource(videoSource, subtitles)
        }
        player.setMediaSource(mediaSource, position.coerceAtLeast(0))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textOff).build()
        player.prepare()
        player.playWhenReady = play
    }
    suspend fun retry(playWhenReady: Boolean = true) {
        val position = player.currentPosition.coerceAtLeast(0)
        selection = host.playbackAccess.refresh(profileId, selection)
        if (!manualSubtitleChoice && subtitle == null && textOff) {
            automaticSubtitlesApplied = false
            subtitleResultsLoaded = false
        }
        prepare(position, playWhenReady)
    }
    suspend fun selectEmbeddedSubtitle(language: String) {
        val track = embeddedSubtitleTracks.firstOrNull { it.language == language } ?: throw AddonException(AddonFailure.NOT_FOUND)
        selectEmbeddedSubtitle(track)
    }
    suspend fun selectEmbeddedSubtitle(track: AddonEmbeddedSubtitle) {
        manualSubtitleChoice = true
        host.playbackAccess.check(profileId, selection)
        if (embeddedSubtitleTracks.none { it.key == track.key }) throw AddonException(AddonFailure.NOT_FOUND)
        subtitle = null; selectedSubtitle = track.language; textOff = false
        subtitleDelayMillis = 0; selectedSubtitleChoice = track.key
        preferredEmbeddedChoice = track.key
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(track.group, listOf(track.index))).build()
        prepareMedia(player.currentPosition.coerceAtLeast(0), player.playWhenReady)
    }
    suspend fun setSubtitle(value: AddonSubtitle?, automatic: Boolean = false, providerId: String? = null, validateSubtitleProvider: suspend () -> Unit = {}) {
        if (!automatic) manualSubtitleChoice = true
        host.playbackAccess.check(profileId, selection)
        validateSubtitleProvider()
        val data = value?.let { AddonSubtitleLoader().load(it) }
        host.playbackAccess.check(profileId, selection)
        validateSubtitleProvider()
        if (automatic && manualSubtitleChoice) return
        subtitle = data
        preferredEmbeddedChoice = null
        textOff = value == null
        selectedSubtitle = value?.language
        selectedSubtitleChoice = value?.let { addonSubtitleChoiceKey(it, providerId) }
        subtitleDelayMillis = 0
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
        // Final access/provider validation has finished. Capture position and play/pause
        // now, with no suspension before applying, so a late download cannot undo a seek/pause.
        prepareMedia(player.currentPosition.coerceAtLeast(0), player.playWhenReady)
    }
    suspend fun loadSubtitleResults(refresh: Boolean = false) = subtitleMutex.withLock {
        if (subtitleResultsLoaded && !refresh) return@withLock
        subtitleResultsLoading = true; subtitleResultsFailure = null; subtitleResults = emptyList()
        try {
            host.sources.subtitles(profileId, selection.video, selection.stream.subtitleExtras()).collect { result ->
                subtitleResults = (subtitleResults.filterNot { it.installationId == result.installationId } + result).sortedBy { it.position }
            }
            subtitleResultsLoaded = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: AddonException) { subtitleResultsFailure = error.failure }
        finally { subtitleResultsLoading = false }
    }
    suspend fun automaticSubtitles() {
        val preferences = automaticPreferences ?: return
        if (manualSubtitleChoice || automaticSubtitlesApplied || released || !foreground) return
        // One bounded attempt per playback/retry, not one per buffering transition.
        automaticSubtitlesApplied = true
        if (preferredLanguages.isEmpty()) return
        val audio = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it).language } }
        if (AddonSubtitlePolicy.suppressForAudio(preferences.preferredAudioLanguage, audio)) {
            textOff = true
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        val embedded = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { AddonSubtitlePolicy.language(group.getTrackFormat(it).language) } }
        fun enableEmbedded() {
            textOff = false
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
        if (preferredLanguages.first() in embedded) { enableEmbedded(); return }
        loadSubtitleResults()
        if (manualSubtitleChoice || released || !foreground) return
        for (language in preferredLanguages) {
            if (language in embedded) { enableEmbedded(); return }
            val candidates = selection.stream.subtitles.filter { AddonSubtitlePolicy.language(it.language) == language }.map { it to null } +
                subtitleResults.flatMap { provider -> provider.items.filter { AddonSubtitlePolicy.language(it.language) == language }.map { it to provider } }
            for ((candidate, provider) in candidates.take(2)) {
                if (manualSubtitleChoice || released || !foreground) return
                try {
                    setSubtitle(candidate, automatic = true, providerId = provider?.installationId) { validateSubtitleProvider(provider) }
                    return
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: AddonException) { /* Try the next bounded matching candidate. Never block video playback. */ }
            }
        }
        if (!manualSubtitleChoice) {
            textOff = true
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        }
    }
    suspend fun validateSubtitleProvider(result: AddonSourceResult<AddonSubtitle>?) {
        if (result == null) return
        val current = host.manager.list(profileId).firstOrNull { it.installationId == result.installationId && it.enabled }
            ?: throw AddonException(AddonFailure.NOT_FOUND)
        if (current.revision != result.revision) throw AddonException(AddonFailure.CONFLICT)
    }
    /** Current session/track only. A different subtitle choice starts at zero;
     * seeks and a fresh-URL retry retain the applied correction. */
    suspend fun applySubtitleDelay(millis: Long) {
        checkAccess()
        if (released || !foreground || selectedSubtitle == null || !subtitleTimingSupported) throw AddonException(AddonFailure.NOT_FOUND)
        val adjusted = AddonSubtitleTiming.bound(millis)
        if (adjusted == subtitleDelayMillis) return
        subtitleDelayMillis = adjusted
        prepareMedia(player.currentPosition.coerceAtLeast(0), player.playWhenReady)
    }
    /** A timed-out addon subtitle lookup may not hold playback indefinitely. Preserve
     * manual choices and primary-audio suppression, then use a preferred embedded track. */
    fun useEmbeddedSubtitleFallback() {
        if (manualSubtitleChoice || subtitle != null || released || !foreground) return
        val audio = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }.map { group.getTrackFormat(it).language } }
        val suppressed = AddonSubtitlePolicy.suppressForAudio(automaticPreferences?.preferredAudioLanguage, audio)
        textOff = suppressed || preferredLanguages.none { it in embeddedSubtitleLanguages }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textOff).build()
    }
    suspend fun checkAccess() {
        host.playbackAccess.check(profileId, selection)
        if (host.manager.list(profileId).none { it.installationId == identity.metadataInstallationId && it.enabled }) {
            throw AddonException(AddonFailure.NOT_FOUND)
        }
    }
    fun snapshot(ended: Boolean = false) {
        if (released || !everReady) return
        val progressSession = session ?: return
        positionMillis = player.currentPosition.coerceAtLeast(0)
        durationMillis = player.duration.coerceAtLeast(0)
        val position = positionMillis
        val duration = durationMillis
        val seq = ++sequence
        host.pendingProgressWrite = host.persistenceScope.launch {
            try { host.progress.save(progressSession, seq, position, duration, ended) }
            catch (_: AddonException) { progressFailure = true }
        }
    }
    fun stopForBackground() { foreground = false; snapshot(); player.stop() }
    fun onForeground() { foreground = true }
    fun release() {
        if (released) return
        snapshot()
        released = true
        player.release()
        if (host.activePlayback === this) host.activePlayback = null
    }
}
