package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.model.player.Gesture
import com.sohva.tv.core.model.player.RemoteAction
import com.sohva.tv.core.model.player.RemoteButton
import com.sohva.tv.core.model.player.RemoteMapping
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
    /** Settings › Library (spec 41 §4.1, spec 40 VOD-FR-32). */
    val library: LibrarySettingsServices

    /** Settings › General › Profiles and Parental controls (spec 04). */
    val profiles: ProfileSettingsServices

    /** Settings › General's own rows (spec 70 §4.5). */
    val general: GeneralSettingsServices

    /** Settings › Playback (spec 70 §4.7). */
    val playback: PlaybackSettingsServices

    /** Settings › Backup & tools (spec 71). */
    val backup: BackupSettingsServices

    /** Settings › About (spec 72). */
    val about: AboutSettingsServices

    /** Settings › Sohva Sport (spec 60 §4.1). */
    val sport: SportSettingsServices

    fun sources(): Flow<List<Source>>

    /** Why beta 23's sources could not be imported, when that happened (plan/04 §17 failure path). */
    suspend fun importProblem(): AppError?

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

    /** Whether a due reminder may bring the app forward; null when this build has no reminders (spec 22 REM-FR-34). */
    fun remindersCanOpen(): Boolean?

    /** Opens the TV's "display over other apps" setting (REM-FR-32). */
    /** False when the TV has no such screen. */
    fun openOverlaySettings(): Boolean

    /** The player's remote mapping (spec 31 §4.7). */
    fun remoteMapping(): Flow<RemoteMapping>

    suspend fun setRemoteAction(button: RemoteButton, gesture: Gesture, action: RemoteAction)

    suspend fun resetRemoteMapping()

    /** The phone setup page (spec 11); it closes itself after 15 minutes. */
    fun phoneSetup(): Flow<PhoneSetupState>

    fun openPhoneSetup()

    fun closePhoneSetup()

    /** The page address as a QR code, built off the main thread (PHONE-FR-51); null when it cannot be. */
    suspend fun qrCode(url: String): QrMatrix?
}
