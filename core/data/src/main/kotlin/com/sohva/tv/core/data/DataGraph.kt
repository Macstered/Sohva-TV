package com.sohva.tv.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sohva.tv.core.data.database.DatabaseFactory
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.data.prefs.LocaleStore
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.data.security.SecretStore
import com.sohva.tv.core.data.source.RefreshFacts
import com.sohva.tv.core.data.source.RefreshStatusStore
import com.sohva.tv.core.data.source.ServiceKeys
import com.sohva.tv.core.data.source.SourceStore
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

    val sources: SourceStore by lazy { SourceStore(database.sources(), secrets, SystemClock) }

    val refreshFacts: RefreshFacts by lazy { RefreshFacts(database) }

    val refreshStatus: RefreshStatusStore by lazy { RefreshStatusStore(database, dispatchers.io) }

    val serviceKeys: ServiceKeys by lazy { ServiceKeys(secrets) }
}
