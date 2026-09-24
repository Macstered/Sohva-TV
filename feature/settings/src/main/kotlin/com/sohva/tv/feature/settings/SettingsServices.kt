package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.RefreshKind
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.sync.SourceChecks
import kotlinx.coroutines.flow.Flow

/**
 * What the Settings screen model does to the world; the app implements it with the source store,
 * the import runner, the scheduler and the preferences (plan/03 §4.4: screens receive a screen
 * model, never repositories or graphs).
 */
interface SettingsServices {
    fun sources(): Flow<List<Source>>

    fun health(): Flow<List<SourceHealth>>

    fun refreshInterval(): Flow<RefreshInterval>

    suspend fun load(sourceId: String): Outcome<SourceConfig?>

    /** Validates and saves (the store's rules); the normalised config. */
    suspend fun save(config: SourceConfig): Outcome<SourceConfig>

    /** Stops the source's imports, deletes its rows, then its configuration (spec 10 SRC-FR-41). */
    suspend fun remove(sourceId: String): Outcome<Unit>

    /** Queues "sync now" for the source in the background (SRC-FR-99). */
    fun syncNow(sourceId: String)

    /** Runs one kind through the import runner, waits for it, and returns its stored result (SRC-FR-30…34). */
    suspend fun refresh(sourceId: String, kind: RefreshKind): SourceHealth?

    suspend fun catalogueCounts(sourceId: String): Pair<Int, Int>

    suspend fun testPlaylist(address: String): SourceChecks.Result

    suspend fun testXtream(account: XtreamAccount): SourceChecks.Result

    suspend fun setRefreshInterval(interval: RefreshInterval)

    /** The phone setup page (spec 11); it closes itself after 15 minutes. */
    fun phoneSetup(): Flow<PhoneSetupState>

    fun openPhoneSetup()

    fun closePhoneSetup()

    /** The page address as a QR code, built off the main thread (PHONE-FR-51); null when it cannot be. */
    suspend fun qrCode(url: String): QrMatrix?
}
