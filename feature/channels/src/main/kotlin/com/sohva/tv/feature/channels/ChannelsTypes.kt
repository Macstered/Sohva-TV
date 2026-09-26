package com.sohva.tv.feature.channels

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.channels.ChannelFields
import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.ChannelListEntity
import com.sohva.tv.core.data.database.EpgChannelOption
import com.sohva.tv.core.data.database.ManagedChannel
import com.sohva.tv.core.data.database.ManagerSource
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.model.phone.QrMatrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

enum class ChannelSort { PLAYLIST, NAME }

/** The list's filters (spec 21 CHAN-FR-13); [search] is the text as typed, at most 100 characters. */
@Immutable
data class ChannelFilter(
    val sourceId: String? = null,
    val groupName: String? = null,
    val showHidden: Boolean = true,
    val search: String = "",
    val sort: ChannelSort = ChannelSort.PLAYLIST,
)

/**
 * What channel management needs from the app (plan/03 §4.6): paged reads within one source,
 * the edit and list stores, and the shared "Show hidden" preference. Tests hand in a gated one.
 */
interface ChannelsEnvironment {
    /** Formatting and mapping of one page (AppDispatchers.ui). */
    val format: CoroutineDispatcher

    /** Whether a parental PIN exists: the lock button needs one (spec 04 PROF-FR-40). */
    val pinConfigured: kotlinx.coroutines.flow.StateFlow<Boolean>

    /** The active profile's lock on [key] (PROF-FR-41). */
    suspend fun isLocked(key: String): Boolean

    suspend fun setLocked(key: String, locked: Boolean)

    suspend fun sources(): List<ManagerSource>

    suspend fun groupNames(sourceId: String): List<String>

    /** One page of ≤ [limit] rows of [sourceId] after [after] (null = from the start), under [filter]. */
    suspend fun page(sourceId: String, filter: ChannelFilter, after: ManagedChannel?, limit: Int): List<ManagedChannel>

    suspend fun count(sourceId: String, filter: ChannelFilter): Int

    suspend fun custom(key: String): ChannelCustomEntity?

    suspend fun epgName(sourceId: String, epgId: String): String?

    suspend fun epgOptions(sourceId: String, search: String, after: EpgChannelOption?, limit: Int): List<EpgChannelOption>

    suspend fun save(key: String, fields: ChannelFields)

    suspend fun setHidden(key: String, hidden: Boolean)

    suspend fun reset(key: String)

    /** True when the channel moved; false at the top or bottom of what is shown. */
    suspend fun move(key: String, up: Boolean, filter: ChannelFilter): Boolean

    val lists: Flow<List<ChannelListEntity>>

    fun listsOf(key: String): Flow<List<String>>

    suspend fun createList(name: String): String?

    suspend fun deleteList(id: String)

    suspend fun addToList(listId: String, key: String)

    suspend fun removeFromList(listId: String, key: String)

    /** `editors_show_hidden`, shared with the Library manager (CHAN-FR-16). */
    val showHidden: Flow<Boolean>

    suspend fun setShowHidden(value: Boolean)

    /** The phone page (spec 11), here in logo mode for one channel (spec 21 CHAN-FR-40). */
    val phone: Flow<PhoneSetupState>

    fun openLogoPhone(key: String, name: String)

    fun closePhone()

    /** The page address as a QR code, built off the main thread; null when it cannot be. */
    suspend fun qrCode(url: String): QrMatrix?
}

/** A row as the list draws it: formatted once per page, off the main thread. */
@Immutable
data class ChannelRow(
    val channel: ManagedChannel,
    /** The group, else the source's name (CHAN-FR-09). */
    val line: String,
    val initials: String,
    val hidden: Boolean,
) {
    val key: String get() = channel.key
}

/** The editor pane's fields for the selected channel (CHAN-FR-20…24); reset whenever it changes. */
@Immutable
data class EditorFields(
    val name: String = "",
    val group: String = "",
    val logoUrl: String = "",
    val number: String = "",
    /** The chosen guide mapping; null = Automatic (the playlist's `tvg-id`). */
    val manualEpgId: String? = null,
    val manualEpgName: String? = null,
)

/** A status line after an action (CHAN-FR-32): a string resource, cleared when a list row gains focus. */
@Immutable
data class Status(val text: Int, val serial: Int)
