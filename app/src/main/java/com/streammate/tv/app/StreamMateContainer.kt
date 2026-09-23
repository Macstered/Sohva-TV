package com.streammate.tv.app

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.Protocol
import com.streammate.tv.core.database.RemindersDao
import kotlinx.coroutines.withContext
import com.streammate.tv.core.diagnostics.DiagnosticsLog
import android.content.Context
import android.os.Build
import com.streammate.tv.iptv.playback.PlaybackHttp
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.network.GuideSourceClient
import com.streammate.tv.core.security.AesGcmSecretCipher
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.PreferencesWrappedKeyStore
import com.streammate.tv.core.security.EnvelopeSecretCipher
import com.streammate.tv.core.security.AndroidKeystoreKeyProvider
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.iptv.m3u.M3uParser
import com.streammate.tv.iptv.metadata.MetadataRepository
import com.streammate.tv.iptv.playback.PlaybackRepository
import com.streammate.tv.iptv.playback.SourceConnectionLimiter
import com.streammate.tv.iptv.repository.GuideImportService
import com.streammate.tv.iptv.repository.GuideRepository
import com.streammate.tv.iptv.repository.M3uCatalogueImportService
import com.streammate.tv.iptv.repository.RoomGuideStore
import com.streammate.tv.iptv.repository.XtreamImportService
import com.streammate.tv.iptv.repository.CatalogueRepository
import com.streammate.tv.iptv.repository.XtreamCatalogueImportService
import com.streammate.tv.iptv.xmltv.XmlTvParser
import com.streammate.tv.iptv.xtream.XtreamClient
import com.streammate.tv.matching.EventChannelMatchingRepository
import com.streammate.tv.sports.repository.DirectSportsRepository
import com.streammate.tv.sports.repository.SportsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import com.streammate.tv.iptv.repository.OrganizationRepository
import okhttp3.OkHttpClient

class StreamMateContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val runtimePolicy = AppRuntimePolicy.forPackage(applicationContext.packageName)
    private val database = StreamMateDatabase.create(applicationContext)
    private val keyProvider = AndroidKeystoreKeyProvider()
    // The keystore key wraps one software data key; every stream URL and
    // credential is then encrypted in software. See EnvelopeSecretCipher.
    val secretCipher: SecretCipher = EnvelopeSecretCipher(
        keystoreCipher = AesGcmSecretCipher(keyProvider::getOrCreate),
        wrappedKeyStore = PreferencesWrappedKeyStore(applicationContext),
    )
    val secretSettingsStore = SecretSettingsStore(applicationContext, secretCipher)
    val preferencesRepository = AppPreferencesRepository(applicationContext)
    private val demoContentProvider = DemoContentProvider.load(applicationContext)
    val demoMode: Boolean = demoContentProvider != null
    val demoPlaybackArtworkUrl: String? = demoContentProvider?.playbackArtworkUrl(applicationContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val guideStore = RoomGuideStore(database.guideDao())
    /** How the app introduces itself to a provider: the same name for a playlist, a guide and a stream. */
    private val userAgent: String = PlaybackHttp.userAgent(
        versionName = runCatching {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        }.getOrNull() ?: "?",
        androidRelease = Build.VERSION.RELEASE ?: "?",
    )
    private val guideSourceClient = GuideSourceClient(httpClient, userAgent)
    private val m3uParser = M3uParser()
    private val xtreamClient = XtreamClient(httpClient)
    private val connectionLimiter = SourceConnectionLimiter()
    val organizationRepository = OrganizationRepository(database.organizationDao(), preferencesRepository)
    private val organizationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val guideRepository = GuideRepository(database.guideDao(), organization = organizationRepository, rosterScope = organizationScope)
    val catalogueRepository = CatalogueRepository(
        database.catalogueDao(),
        organization = organizationRepository,
        activeProfile = preferencesRepository.preferences.map { it.activeProfileId }.distinctUntilChanged(),
        trakt = database.traktStateDao(),
    )
    // Lazy so initialization cannot race construction of the repositories below.
    private val initialization by lazy {
        organizationScope.async {
            val started = android.os.SystemClock.elapsedRealtime()
            demoContentProvider?.seed(applicationContext, database, secretCipher, secretSettingsStore, preferencesRepository)
            organizationRepository.migrateLegacy(preferencesRepository.preferences.first())
            trakt.initialize()
            DiagnosticsLog.i("startup", "local state ready: ${android.os.SystemClock.elapsedRealtime() - started} ms")
            organizationScope.launch { organizationRepository.movieIdentityUpdates().collect() }
        }
    }

    suspend fun awaitReady() { initialization.await() }

    val metadataRepository = MetadataRepository(
        dao = database.metadataDao(),
        settingsStore = secretSettingsStore,
        httpClient = httpClient,
    )
    // The planner statistics an import leaves behind. Room never runs
    // ANALYZE on its own, and every query used to be planned without any.
    private val analyzeAfterImport: suspend () -> Unit = {
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            runCatching { database.analyze() }
                .onSuccess { DiagnosticsLog.i("database", "analyze: ${System.currentTimeMillis() - started} ms") }
                .onFailure { DiagnosticsLog.w("database", "analyze failed", it) }
        }
    }
    val guideImportService = GuideImportService(
        sourceClient = guideSourceClient,
        m3uParser = m3uParser,
        xmlTvParser = XmlTvParser(),
        store = guideStore,
        secretCipher = secretCipher,
        afterImport = analyzeAfterImport,
    )
    val xtreamImportService = XtreamImportService(
        client = xtreamClient,
        store = guideStore,
        secretCipher = secretCipher,
        guideImportService = guideImportService,
        afterImport = analyzeAfterImport,
    )
    val xtreamCatalogueImportService = XtreamCatalogueImportService(
        organization = organizationRepository,
        client = xtreamClient,
        dao = database.catalogueDao(),
        secretCipher = secretCipher,
        afterImport = analyzeAfterImport,
    )
    val m3uCatalogueImportService = M3uCatalogueImportService(
        organization = organizationRepository,
        sourceClient = guideSourceClient,
        parser = m3uParser,
        dao = database.catalogueDao(),
        secretCipher = secretCipher,
        afterImport = analyzeAfterImport,
    )
    val playbackRepository = PlaybackRepository(
        guideRepository,
        secretCipher,
        connectionLimiter,
        catalogueRepository,
    )
    val externalPlayerLauncher = ExternalPlayerLauncher(applicationContext, playbackRepository)
    /** Trakt sign-in and scrobbling. Local VOD and Discover progress are unaffected by it. */
    val trakt = com.streammate.tv.trakt.TraktService(applicationContext, secretCipher, database.traktStateDao(), offline = demoMode)
    val traktLibraryLookup = com.streammate.tv.trakt.TraktLibraryLookup(database.metadataDao(), catalogueRepository)
    val traktVodIdentity = com.streammate.tv.trakt.TraktVodIdentity(database.catalogueDao(), database.metadataDao(), catalogueRepository, metadataRepository)
    internal val homeResume by lazy {
        com.streammate.tv.feature.home.HomeResumeStore(
            scope = organizationScope,
            profiles = preferencesRepository.preferences.onStart { awaitReady() }.map {
                com.streammate.tv.feature.home.HomeResumeProfile(it.activeProfileId, runtimePolicy.addonsAllowed && !demoMode && !it.activeRestriction.restricted)
            },
            vod = catalogueRepository::observeContinueWatching,
            discover = { profile -> com.streammate.tv.addons.AddonHost.get(applicationContext, this).progress.observeRecent(profile) },
            onInitialRead = { millis -> DiagnosticsLog.i("home", "cached resume ready: $millis ms") },
        )
    }
    val backupManager = StreamMateBackupManager(
        applicationContext,
        secretSettingsStore,
        preferencesRepository,
        guideRepository,
    )
    val appUpdateChecker = AppUpdateChecker(applicationContext, httpClient)
    val remindersDao: RemindersDao get() = database.remindersDao()
    val reminderRepository = ReminderRepository(applicationContext, database.remindersDao())
    /** What a notification asked the app to open; the app takes it once it is up. */
    val openRequests = OpenRequests()
    val reminderAlerts = ReminderAlerts()
    val diagnosticsReport = DiagnosticsReport(
        applicationContext,
        preferencesRepository,
        secretSettingsStore,
        guideRepository,
        sqliteVersion = {
            database.openHelper.readableDatabase.query("SELECT sqlite_version()").use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "?"
            }
        },
    )
    val channelLogoStore = ChannelLogoStore(applicationContext)
    val phoneSetupServer = PhoneSetupServer(applicationContext) { submission ->
        submission.logo?.let { logo ->
            guideRepository.setChannelLogo(logo.channelId, channelLogoStore.save(logo.channelId, logo.image))
        }
        submission.source?.let { source ->
            secretSettingsStore.upsertSource(source)
            guideRepository.upsertSourceState(source)
            GuideRefreshScheduler.syncNow(applicationContext, source.id)
        }
        submission.tmdbToken?.let { token ->
            secretSettingsStore.saveMetadataSettings(
                secretSettingsStore.loadMetadataSettings().copy(tmdbEnabled = true, tmdbReadAccessToken = token),
            )
        }
        submission.apiSportsKey?.let { key ->
            secretSettingsStore.saveSportsApiSettings(secretSettingsStore.loadSportsApiSettings().copy(apiKey = key))
        }
    }
    /**
     * Streams go over HTTP/1.1 only. Some IPTV servers and the fronts before
     * them reset HTTP/2 streams part-way, which reaches the screen as an
     * unspecified read error; the players that work with them speak 1.1.
     */
    val playbackHttpClient: OkHttpClient = httpClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    val sportsRepository: SportsRepository by lazy {
        demoContentProvider?.sportsRepository ?: DirectSportsRepository(
            httpClient = httpClient,
            cacheDao = database.sportsCacheDao(),
            settingsStore = secretSettingsStore,
        )
    }
    val eventChannelMatchingRepository: EventChannelMatchingRepository by lazy {
        EventChannelMatchingRepository(database.guideDao(), cache = com.streammate.tv.matching.EventChannelMatchCache(
            java.io.File(applicationContext.cacheDir, "sports-channel-matches.bin"),
        ))
    }

    suspend fun refreshDemoContent(): Boolean {
        val provider = demoContentProvider ?: return false
        provider.seed(
            context = applicationContext,
            database = database,
            secretCipher = secretCipher,
            secretSettingsStore = secretSettingsStore,
            preferencesRepository = preferencesRepository,
        )
        return true
    }
}
