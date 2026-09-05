package com.streammate.tv.app

import android.content.Context
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import com.streammate.tv.iptv.repository.OrganizationRepository
import okhttp3.OkHttpClient

class StreamMateContainer(context: Context) {
    private val applicationContext = context.applicationContext
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

    init {
        demoContentProvider?.let { provider ->
            runBlocking(Dispatchers.IO) {
                provider.seed(
                    context = applicationContext,
                    database = database,
                    secretCipher = secretCipher,
                    secretSettingsStore = secretSettingsStore,
                    preferencesRepository = preferencesRepository,
                )
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val guideStore = RoomGuideStore(database.guideDao())
    private val guideSourceClient = GuideSourceClient(httpClient)
    private val m3uParser = M3uParser()
    private val xtreamClient = XtreamClient(httpClient)
    private val connectionLimiter = SourceConnectionLimiter()
    val organizationRepository = OrganizationRepository(database.organizationDao(), preferencesRepository)
    private val organizationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val guideRepository = GuideRepository(database.guideDao(), organization = organizationRepository)
    val catalogueRepository = CatalogueRepository(database.catalogueDao(), organization = organizationRepository)
    init {
        // Complete the small, idempotent legacy preference import before any screen reads it.
        runBlocking(Dispatchers.IO) { organizationRepository.migrateLegacy(preferencesRepository.preferences.first()) }
        organizationScope.launch { organizationRepository.movieIdentityUpdates().collect() }
    }
    val metadataRepository = MetadataRepository(
        dao = database.metadataDao(),
        settingsStore = secretSettingsStore,
        httpClient = httpClient,
    )
    val guideImportService = GuideImportService(
        sourceClient = guideSourceClient,
        m3uParser = m3uParser,
        xmlTvParser = XmlTvParser(),
        store = guideStore,
        secretCipher = secretCipher,
    )
    val xtreamImportService = XtreamImportService(
        client = xtreamClient,
        store = guideStore,
        secretCipher = secretCipher,
        guideImportService = guideImportService,
    )
    val xtreamCatalogueImportService = XtreamCatalogueImportService(
        organization = organizationRepository,
        client = xtreamClient,
        dao = database.catalogueDao(),
        secretCipher = secretCipher,
    )
    val m3uCatalogueImportService = M3uCatalogueImportService(
        organization = organizationRepository,
        sourceClient = guideSourceClient,
        parser = m3uParser,
        dao = database.catalogueDao(),
        secretCipher = secretCipher,
    )
    val playbackRepository = PlaybackRepository(
        guideRepository,
        secretCipher,
        connectionLimiter,
        catalogueRepository,
    )
    val externalPlayerLauncher = ExternalPlayerLauncher(applicationContext, playbackRepository)
    val backupManager = StreamMateBackupManager(
        applicationContext,
        secretSettingsStore,
        preferencesRepository,
        guideRepository,
    )
    val appUpdateChecker = AppUpdateChecker(applicationContext, httpClient)
    val phoneSetupServer = PhoneSetupServer(applicationContext) { submission ->
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
    val playbackHttpClient: OkHttpClient = httpClient
    val sportsRepository: SportsRepository by lazy {
        demoContentProvider?.sportsRepository ?: DirectSportsRepository(
            httpClient = httpClient,
            cacheDao = database.sportsCacheDao(),
            settingsStore = secretSettingsStore,
        )
    }
    val eventChannelMatchingRepository: EventChannelMatchingRepository by lazy {
        EventChannelMatchingRepository(database.guideDao())
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
