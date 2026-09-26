package com.sohva.tv.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sohva.tv.core.data.backup.BackupStore
import com.sohva.tv.core.data.channels.ChannelEditStore
import com.sohva.tv.core.data.channels.ChannelListStore
import com.sohva.tv.core.data.channels.ChannelManagerReads
import com.sohva.tv.core.data.database.DatabaseFactory
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.home.HomeReads
import com.sohva.tv.core.data.live.LiveStore
import com.sohva.tv.core.data.migration.Beta23HiddenCategories
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.data.org.OrgManager
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.data.reminder.ReminderStore
import com.sohva.tv.core.data.search.SearchReads
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.data.security.SecretStore
import com.sohva.tv.core.data.source.RefreshFacts
import com.sohva.tv.core.data.source.RefreshStatusStore
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.profile.ProfileStore
import com.sohva.tv.core.data.source.SourceStore
import com.sohva.tv.core.data.vod.ProgressStore
import com.sohva.tv.core.data.vod.TitleReads
import com.sohva.tv.core.data.vod.WallReads
import com.sohva.tv.core.model.concurrent.AppDispatchers
import com.sohva.tv.core.model.time.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Lazy holders for the data layer (plan/03 §4.4). Constructing the graph opens nothing; each
 * store opens on first touch, which the start-up sequence guarantees is off the main thread.
 */
class DataGraph(context: Context, private val dispatchers: AppDispatchers) {
    private val app: Context = context.applicationContext

    val database: SohvaDatabase by lazy { DatabaseFactory.create(app) }

    val preferences: AppPreferences by lazy {
        AppPreferences(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatchers.io + SupervisorJob()),
                produceFile = { app.preferencesDataStoreFile(AppPreferences.FILE_NAME) },
            ),
        )
    }

    /** Synchronous by design: read in attachBaseContext below Android 13 (spec 01 FR-03). */
    val locale: LocaleStore by lazy { LocaleStore(app) }

    /** The app's value cipher: secrets, and stream addresses sealed by imports (spec 73 §4.2). */
    val cipher: EnvelopeCipher by lazy {
        val spec = EnvelopeSpec.MAIN
        EnvelopeCipher(
            spec = spec,
            keyProvider = AndroidKeystoreKeyProvider(spec.keystoreAlias),
            wrappedKeyStore = PrefsWrappedKeyStore(app, spec),
        )
    }

    val secrets: SecretStore by lazy { SecretStore(app, cipher, dispatchers.io) }

    /**
     * The household (spec 04): seeded from the start snapshot before the first frame, so
     * [ProfileStore.activeId] is right for every read; the database and secrets open on use.
     */
    val profiles: ProfileStore by lazy {
        ProfileStore(preferences, { database.profiles() }, secrets, dispatchers.io, SystemClock, CoroutineScope(dispatchers.io + SupervisorJob()))
    }

    val sources: SourceStore by lazy { SourceStore(database.sources(), secrets, SystemClock) }

    val refreshFacts: RefreshFacts by lazy { RefreshFacts(database) }

    val refreshStatus: RefreshStatusStore by lazy { RefreshStatusStore(database, dispatchers.io) }

    val serviceKeys: ServiceKeys by lazy { ServiceKeys(secrets) }

    /** The guide's and player's reads, favourites and recents (M2). */
    val live: LiveStore by lazy { LiveStore(database, dispatchers.io, SystemClock) { profiles.activeId } }

    /** Channel management's edits and lists (M3, spec 21). */
    val channelEdits: ChannelEditStore by lazy { ChannelEditStore(database, dispatchers.io, SystemClock) }
    val channelLists: ChannelListStore by lazy { ChannelListStore(database, dispatchers.io, SystemClock) }
    val channelManager: ChannelManagerReads by lazy { ChannelManagerReads(database, dispatchers.io) }

    /** Programme and match reminders (M3, spec 22). */
    val reminders: ReminderStore by lazy { ReminderStore(database, dispatchers.io) }

    /** Movie and series walls and progress (M4), for the active profile (spec 04 PROF-FR-07). */
    val walls: WallReads by lazy { WallReads(database, dispatchers.io) { profiles.activeId } }
    val progress: ProgressStore by lazy { ProgressStore(database, dispatchers.io, SystemClock) { profiles.activeId } }
    val titles: TitleReads by lazy { TitleReads(database, dispatchers.io, SystemClock) }

    /** Search's groups (spec 03). */
    val search: SearchReads by lazy { SearchReads(database, dispatchers.io) { profiles.activeId } }

    /** Home's recent channels (spec 02). */
    val home: HomeReads by lazy { HomeReads(database, dispatchers.io) { profiles.activeId } }

    /** The library manager's reads (spec 42 §4.9); its writes go through [OrgRules] and [OrgPass]. */
    val organization: OrgManager by lazy { OrgManager(database, OrgRules(database), dispatchers.io) }

    /** The one-time import of beta 23's sources and keys (decision A1); after the first frame. */
    val beta23Import: Beta23SourceImport by lazy { Beta23SourceImport(app, sources, serviceKeys, database.appMeta(), dispatchers.io) }

    /** Beta 23's hidden categories as rules, once (spec 42 ORG-15); after the first frame. */
    val beta23Categories: Beta23HiddenCategories by lazy { Beta23HiddenCategories(app, database, dispatchers.io) }

    /** The backup's database side (spec 71). */
    val backup: BackupStore by lazy { BackupStore(database, dispatchers.io) }
}
