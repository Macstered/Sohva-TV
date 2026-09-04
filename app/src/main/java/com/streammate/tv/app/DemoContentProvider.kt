package com.streammate.tv.app

import android.content.Context
import android.content.pm.PackageManager
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.security.SecretCipher
import com.streammate.tv.core.security.SecretSettingsStore
import com.streammate.tv.sports.repository.SportsRepository

/** Optional data boundary used by the separately installed screenshot build. */
interface DemoContentProvider {
    val sportsRepository: SportsRepository

    suspend fun seed(
        context: Context,
        database: StreamMateDatabase,
        secretCipher: SecretCipher,
        secretSettingsStore: SecretSettingsStore,
        preferencesRepository: AppPreferencesRepository,
    )

    fun playbackArtworkUrl(context: Context): String

    companion object {
        private const val MANIFEST_KEY = "com.streammate.tv.DEMO_CONTENT_PROVIDER"

        fun load(context: Context): DemoContentProvider? {
            val applicationInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            val providerClassName = applicationInfo.metaData
                ?.getString(MANIFEST_KEY)
                ?.takeIf(String::isNotBlank)
                ?: return null
            return runCatching {
                Class.forName(providerClassName)
                    .getDeclaredConstructor()
                    .newInstance() as DemoContentProvider
            }.getOrElse { error ->
                throw IllegalStateException("Demo content provider could not be created", error)
            }
        }
    }
}
