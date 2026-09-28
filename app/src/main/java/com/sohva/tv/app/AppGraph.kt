package com.sohva.tv.app

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import android.app.Application
import android.util.Log
import com.sohva.tv.app.settings.PhoneSetup
import com.sohva.tv.core.data.DataGraph
import com.sohva.tv.core.data.diagnostics.RingDiagnosticsLog
import com.sohva.tv.core.data.home.ContinueFeed
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.FeatureFlags
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.concurrent.PauseGate
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.time.SystemClock
import com.sohva.tv.feature.home.HomeModel
import com.sohva.tv.feature.library.BrowseSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app's object graph: lazy holders only (plan/03 §4.4). Creating it opens nothing; the first
 * touch of [data] happens off the main thread in the start-up sequence (plan/03 §4.9).
 */
class AppGraph(val app: Application, val flags: FeatureFlags) {
    val clock: Clock = SystemClock

    /**
     * What the start-up log lines count from (spec 02 HOME-FR-26): the process start for a cold launch.
     * A process an update, a reminder or a refresh started earlier without a screen counts from the
     * first activity instead; counting from the process once logged 54 s for a 7 s start on the
     * owner's Shield (decision "Start-up timing base").
     */
    @Volatile
    var launchedAt: Long = android.os.SystemClock.elapsedRealtime()
        private set

    @Volatile
    private var launchMarked = false

    /** Called by the activity when it is created; only the first one counts. */
    fun markLaunch() {
        if (launchMarked) return
        launchMarked = true
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - launchedAt > WARM_PROCESS_MS) launchedAt = now
    }
    val dispatchers: AppDispatchers by lazy { AndroidDispatchers() }

    val diagnostics: DiagnosticsLog by lazy {
        RingDiagnosticsLog(clock) { level, line ->
            if (level == 'E') Log.e(LOG_TAG, line) else Log.i(LOG_TAG, line)
        }
    }

    val data: DataGraph by lazy {
        DataGraph(app, dispatchers).also { d ->
            // Trakt's history on the library (spec 51 FR-32 to -36): unrestricted profiles only; building
            // Trakt's host reads nothing, and rows exist only for connected accounts.
            if (flags.trakt) d.progress.traktAllowed = { profile -> trakt?.access?.allowed(profile) == true }
        }
    }

    /** Work that outlives a screen: imports started from Settings keep going (spec 10 SRC-FR-94). */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    /** True while an activity is started; set by [MainActivity]. */
    val inForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** The TV's own zone id, read again on its change broadcast and on return (spec 74 L10N-FR-33). */
    private val deviceZone: MutableStateFlow<String> = MutableStateFlow(java.time.ZoneId.systemDefault().id)

    fun refreshDeviceZone() {
        deviceZone.value = java.util.TimeZone.getDefault().id
    }

    /**
     * The zone screens write times in: the chosen one, else the TV's own as it is now, so a change of
     * the TV's zone reaches the guide and Home without a restart.
     */
    val appZone: kotlinx.coroutines.flow.Flow<String?> by lazy {
        kotlinx.coroutines.flow.combine(kotlinx.coroutines.flow.flow { emitAll(data.preferences.timeZone) }, deviceZone) { chosen, device -> chosen ?: device }
            .distinctUntilChanged()
    }

    /**
     * Beta 23's viewer data has been imported, or there was none (decision A1): the start step
     * completes it before the first screen, and the background importers wait for it so nothing
     * else touches the organisation rules meanwhile.
     */
    val upgradeSettled: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred()

    /**
     * Beta 23's sources and service keys have been imported, or there were none. Sohva Sport waits
     * for it: its first refresh once ran before the API-Sports key was copied and said the key was
     * missing until the next refresh (the owner's Elisa box, 28 Sept).
     */
    private val keysSettled: kotlinx.coroutines.CompletableDeferred<Unit> = kotlinx.coroutines.CompletableDeferred()

    /** True while video plays; the player (M2) sets it. */
    val playbackActive: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val pauseGate: PauseGate by lazy { PauseGate(playbackActive, inForeground) }

    val sync: SyncGraph by lazy { SyncGraph(this) }

    /**
     * Continue watching for the life of the process (spec 02 HOME-FR-24): started as the first work
     * after the launch screen; it re-reads on library and progress changes, not while video plays.
     */
    val continueFeed: ContinueFeed by lazy {
        ContinueFeed(
            scope = appScope,
            // The library and Discover in parallel, then one row (spec 02 §4.4).
            read = {
                kotlinx.coroutines.coroutineScope {
                    val library = async { data.progress.continueWatching() }
                    val addons = async { com.sohva.tv.app.discover.DiscoverGraph.continueItems(this@AppGraph, HomeModel.RESUME_CARDS) }
                    com.sohva.tv.core.data.home.ContinueMerge.merge(library.await(), addons.await(), HomeModel.RESUME_CARDS)
                }
            },
            // Trakt's cache counts as a change too (spec 51 FR-34); its first value is not one.
            changes = kotlinx.coroutines.flow.merge(
                data.walls.changes(),
                data.traktState.revision.drop(1).map { },
                discover?.progress?.changes?.map { } ?: kotlinx.coroutines.flow.emptyFlow(),
                cardArt.stored,
            ),
            playing = playbackActive,
            onFirstSettled = {
                diagnostics.info("home", "cached resume ready: ${android.os.SystemClock.elapsedRealtime() - launchedAt} ms")
                // Today's games for Home, Search and reminders, after Home's first read (spec 60 SPORT-FR-29).
                if (flags.sport) appScope.launch { keysSettled.await(); sport.feed.start() }
                // Trakt's sync loop follows the active profile from here on (spec 51 FR-21).
                traktLoop?.let { loop -> appScope.launch { data.profiles.activeChanges.collect(loop::start) } }
                // Landscape pictures for library cards matched before matches kept one.
                if (flags.metadataWorker) appScope.launch { runCatching { cardArt.fill() } }
            },
        )
    }

    /** Continue watching's landscape pictures for older matches (decision "Library card art"). */
    val cardArt: com.sohva.tv.app.home.HomeCardArt by lazy { com.sohva.tv.app.home.HomeCardArt(this) }

    /** TMDB and TVmaze lookups and the background enrichment (spec 41); built on first use. */
    val metadata: com.sohva.tv.app.metadata.MetadataGraph by lazy { com.sohva.tv.app.metadata.MetadataGraph(this) }

    /** Sohva Sport (spec 60); built on first use. */
    val sport: com.sohva.tv.app.sport.SportGraph by lazy { com.sohva.tv.app.sport.SportGraph(this) }

    /** Discover (spec 50): null where the build has no addons (the demo); built on first use. */
    val discover: com.sohva.tv.feature.discover.DiscoverHost? by lazy { if (flags.discover) com.sohva.tv.app.discover.DiscoverGraph.build(this) else null }

    /** The addon player's bridge to Discover (spec 50 §4.12); none where the build has no addons. */
    /** Trakt (spec 51): in every build; without credentials its panel says it is not configured. */
    val trakt: com.sohva.tv.feature.trakt.TraktHost? by lazy { if (flags.trakt) com.sohva.tv.app.trakt.TraktGraph.build(this) else null }

    /** Trakt's marks for Discover cards and resume (spec 51 FR-31, -35): free to build, looked up per screen. */
    val titleMarks: com.sohva.tv.core.model.vod.TitleMarks by lazy {
        if (flags.trakt) com.sohva.tv.app.trakt.TraktTitleMarks(this) else com.sohva.tv.core.model.vod.TitleMarks.NONE
    }

    /** Trakt's read side and its loop for the active profile (spec 51 FR-21, -22); started after Home's first resume read. */
    val traktSync: com.sohva.tv.feature.trakt.sync.TraktSync? by lazy { trakt?.let { com.sohva.tv.feature.trakt.sync.TraktSync(it, data.traktState, it.shelves) } }
    val traktLoop: com.sohva.tv.feature.trakt.sync.TraktSyncLoop? by lazy {
        val host = trakt ?: return@lazy null
        com.sohva.tv.feature.trakt.sync.TraktSyncLoop(host, traktSync!!, host.shelves, playbackActive)
    }

    val addonPlayback: com.sohva.tv.app.discover.AddonPlaybackBridge? by lazy { discover?.let { com.sohva.tv.app.discover.AddonPlaybackBridge(this, it) } }

    /**
     * The public updater (spec 72 §4): only the release package checks, downloads or installs
     * (ABOUT-FR-01); built on first use, after the first frame, on the shared HTTP client.
     */
    val updater: com.sohva.tv.core.sync.update.Updater by lazy {
        val config = com.sohva.tv.core.sync.update.UpdaterConfig(
            enabled = flags.publicUpdates && app.packageName == RELEASE_PACKAGE,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            sdk = android.os.Build.VERSION.SDK_INT,
            feed = BuildConfig.UPDATE_FEED.toHttpUrl(),
        )
        com.sohva.tv.core.sync.update.Updater(
            config, com.sohva.tv.core.net.update.UpdateHttp(sync.http.client), com.sohva.tv.app.update.AppUpdateMemory(app, dispatchers.io),
            java.io.File(app.cacheDir, "updates"), com.sohva.tv.app.update.AndroidUpdateInstaller(app, diagnostics, dispatchers.io), diagnostics, clock, appScope,
            // Notes in the interface language (ABOUT-FR-21): the chosen one, else the TV's.
            language = { data.locale.languageTag() ?: java.util.Locale.getDefault().toLanguageTag() },
        )
    }

    /** The playback engine's and player screen's view of the graph; built on first playback. */
    val player: com.sohva.tv.app.player.PlayerGraph by lazy { com.sohva.tv.app.player.PlayerGraph(this) }

    /** Logos and artwork, decoded at their drawn size; the loader is built on first use. */
    val artwork: com.sohva.tv.app.artwork.CoilArtwork by lazy { com.sohva.tv.app.artwork.CoilArtwork(this) }

    /**
     * What a notification tap or a bring-forward asked the app to open (spec 01 SHELL-FR-40/41);
     * the shell takes it once the start route is applied, then clears it.
     */
    val openRequest: MutableStateFlow<com.sohva.tv.app.reminder.OpenRequest?> = MutableStateFlow(null)

    /** Programme and match reminders (spec 22); built on first use, never in the Lab build. */
    val reminders: com.sohva.tv.app.reminder.ReminderCenter by lazy { com.sohva.tv.app.reminder.ReminderCenter(this) }

    /** The phone setup page (spec 11); built on first use. */
    val phone: PhoneSetup by lazy { PhoneSetup(this) }

    /**
     * The channel last played from the guide in this process: the guide opens on it (spec 20 §3.1,
     * session memory, never stored).
     */
    @Volatile
    var guideFocusChannel: String? = null

    /** One browse session per wall for the life of the process (spec 40 VOD-FR-56). Main thread only. */
    val browseSessions: Map<WallRoom, BrowseSession> = mapOf(WallRoom.MOVIES to BrowseSession(), WallRoom.SERIES to BrowseSession())

    /**
     * The live reads the guide and player use: [DataGraph.live], unless a device test wraps it to
     * gate one read and prove that slow data never moves focus (AGENTS.md §8).
     */
    @Volatile
    var liveReadsOverride: com.sohva.tv.core.data.live.LiveReads? = null

    /** Tests only: awaited before a wall reads a page next to its window, so a page can be made slow (AGENTS §8). */
    @Volatile
    var wallPageGate: (suspend () -> Unit)? = null

    val liveReads: com.sohva.tv.core.data.live.LiveReads get() = liveReadsOverride ?: data.live

    /** Picture in picture (spec 30 PLAY-FR-110): a player is the top destination. */
    val playerOnTop: kotlinx.coroutines.flow.MutableStateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** The app is shown as the corner window (PLAY-FR-111). */
    val inPictureInPicture: kotlinx.coroutines.flow.MutableStateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** "Keep watching in a corner", kept in memory for the leave hint, which cannot wait for a read. */
    val pictureInPictureOn: kotlinx.coroutines.flow.StateFlow<Boolean> by lazy {
        data.preferences.playbackSettings.map { it.pictureInPicture }.flowOn(dispatchers.io)
            .stateIn(appScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)
    }

    /** When the last profile switch began, until its Home rows settle (elapsed real time; 0 = none). */
    val switchedAt: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0L)

    /** Who is watching was answered in this process: an activity recreation does not ask again (spec 04 PROF-FR-10). */
    @Volatile
    var startAnswered: Boolean = false

    /** The guide's rows kept between visits (spec 20 GUIDE-FR-120). */
    val keptRows: com.sohva.tv.app.live.KeptRows by lazy { com.sohva.tv.app.live.KeptRows({ liveReads }, clock, appScope) }

    /** Options that open screens of later milestones say so briefly (the shell's placeholder toast). */
    fun notYetAvailable() {
        android.widget.Toast.makeText(app, AppLocales.texts(app).getString(com.sohva.tv.ui.design.R.string.home_coming_soon), android.widget.Toast.LENGTH_SHORT).show()
    }

    private val started = AtomicBoolean(false)

    /**
     * What waits for the first frame (plan/03 §4.9): start-up repair of interrupted imports and the
     * refresh schedule, which follows the interval setting from then on. Runs once per process.
     */
    fun afterFirstFrame() {
        if (!started.compareAndSet(false, true)) return
        // The Continue watching read comes first: Home's first card waits for it (spec 02 §9.1).
        continueFeed.start()
        pictureInPictureOn
        // A switch or an edited restriction starts Continue watching over for the profile (HOME-FR-27);
        // the time from a switch to its settled rows goes to the diagnostics log (spec 04 §11 budget: 1 s).
        appScope.launch {
            data.profiles.changes.drop(1).collect {
                continueFeed.retry()
                val switched = switchedAt.getAndSet(0L)
                if (switched > 0L) {
                    launch {
                        continueFeed.awaitSettled()
                        diagnostics.info("profile", "home ready after switch: ${android.os.SystemClock.elapsedRealtime() - switched} ms")
                    }
                }
            }
        }
        // The secret store wins over the mirrored "PIN exists" flag (spec 01 SHELL-FR-08).
        appScope.launch { data.profiles.reconcilePin() }
        appScope.launch {
            // Beta 23's sources first, so an upgraded install syncs them at once (decision A1).
            val imported = try {
                data.beta23Import.run()
            } finally {
                keysSettled.complete(Unit)
            }
            if (imported is Beta23SourceImport.Result.Imported) imported.sourceIds.forEach(sync.scheduler::syncNow)
            sync.runner.recoverAfterRestart()
            upgradeSettled.await()
            data.beta23Categories.run()
            // Beta 23's Discover data (decision A1); Discover itself is built only when old files exist.
            if (flags.discover) com.sohva.tv.app.discover.Beta23DiscoverImport(this@AppGraph).run()
            // Beta 23's Trakt accounts (decision A1); Trakt is built only when the old file exists.
            if (flags.trakt) com.sohva.tv.app.trakt.Beta23TraktImport(this@AppGraph).run()
            // Beta 23's files go once every part above came across (plan/04 §17).
            com.sohva.tv.app.migration.Beta23Cleanup(this@AppGraph).run()
            // The demo build's fictional Trakt account (spec 51 FR-37); Trakt itself stays offline there.
            if (flags.trakt && flags.demoContent) trakt?.let { com.sohva.tv.feature.trakt.demo.DemoTraktSeed.seed(it, data.profiles.activeId) }
        }
        // Once a day at most, and never in the demo build (spec 72 ABOUT-FR-02, -04).
        if (flags.publicUpdates) updater.start(automatic = !flags.demoContent)
        // A restore the process did not finish is said at start (spec 71 §8); Backup's status line says it too.
        appScope.launch {
            if (data.backup.unfinished()) {
                withContext(dispatchers.main) {
                    android.widget.Toast.makeText(app, AppLocales.texts(app).getString(com.sohva.tv.ui.design.R.string.backup_restore_incomplete), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        appScope.launch { data.preferences.refreshInterval.collect { sync.scheduler.schedule(it) } }
        // An update or a force-stop drops the alarm; each start sets it again (spec 22 REM-FR-14).
        if (flags.reminders) appScope.launch { reminders.reschedule() }
    }

    private companion object {
        const val LOG_TAG = "SohvaTV"

        /** A process older than this when the first activity starts was started without a screen. */
        private const val WARM_PROCESS_MS = 5_000L
        const val RELEASE_PACKAGE = "com.streammate.tv"
    }
}
