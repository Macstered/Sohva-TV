package com.sohva.tv.feature.live

import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.time.Clock
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/** A saved source for the empty-library card (GUIDE-FR-101), whether or not it has channels. */
data class SavedSource(val id: String, val name: String, val enabled: Boolean)

/**
 * What the guide needs from the app (plan/03 §4.6): reads, the few preferences it uses, the health
 * of the sources for the empty card, and a formatting thread. Tests hand in a gated [reads].
 */
interface GuideEnvironment {
    val reads: LiveReads
    val clock: Clock

    /** Formatting and mapping of one page (AppDispatchers.ui). */
    val format: CoroutineDispatcher
    val locale: Locale

    val lastGuideSource: Flow<String?>
    val lastChannel: Flow<String?>
    val showChannelNumbers: Flow<Boolean>
    val timeZone: Flow<String?>
    val savedSources: Flow<List<SavedSource>>
    val health: Flow<List<SourceHealth>>

    suspend fun saveGuideSource(id: String)

    /** Starts a background sync of every source (GUIDE-FR-101 "Sync now"). */
    fun syncAll()

    /** The rows kept from the last visit (GUIDE-FR-120): a list index, only while still valid. */
    fun keptList(spec: ListSpec): ChannelList?

    fun keepList(list: ChannelList)

    /** Whether this build has reminders at all (spec 22 REM-15: none in the Lab build). */
    val remindersOn: Boolean

    /** The stored reminder ids, for "Reminder set" (REM-NFR-03). */
    val reminderIds: Flow<Set<String>>

    /** Adds or removes the reminder (REM-FR-04); true when it was added. */
    suspend fun toggleReminder(reminder: Reminder): Boolean
}

/** Where the guide sends the viewer; implemented by the app's router. */
interface GuideNavigation {
    fun play(channelKey: String)

    /** A programme from the provider's archive, from its start to its stop (spec 22 CATCH-02…04). */
    fun playArchive(channelKey: String, start: Long, stop: Long)

    fun openSettings()

    /** Options › Edit (channels): channel management (spec 21 CHAN-01). */
    fun openChannels()

    /** Options › Sort, Edit (groups) and Edit (channels) until their milestones (M3, M4). */
    fun notYetAvailable()

    fun leave()
}
