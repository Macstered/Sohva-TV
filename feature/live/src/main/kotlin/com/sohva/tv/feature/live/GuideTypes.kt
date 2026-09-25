package com.sohva.tv.feature.live

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.database.LiveChannel
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.model.guide.GuideProgramme

/**
 * One guide row, formatted once when its page is read (spec 20 GUIDE-NFR-20..21): the number
 * label, the name, the feed line (stream tags, else group, else source) and the initials.
 */
@Immutable
data class GuideRowData(
    val index: Int,
    val channel: LiveChannel,
    /** The shown number: own number, else list position + 1; null when numbers are off. */
    val number: String?,
    val numberValue: Int?,
    val feed: String,
    val initials: String,
    /**
     * How many days back the provider's archive reaches, 0 when the channel offers no catch-up that
     * can play. Worked out once when the row is prepared (spec 22 CATCH-NFR-02).
     */
    val archiveDays: Int = 0,
) {
    val key: String get() = channel.key
    val name: String get() = channel.name

    /** CATCH-FR-10's window: started, and no longer ago than the archive reaches. */
    fun offersArchive(programme: GuideProgramme, now: Long): Boolean =
        archiveDays > 0 && programme.start <= now && programme.start >= now - archiveDays * DAY_MS

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * A channel's programmes for the current window with their time labels, formatted off the main
 * thread when they arrive; drawn as they are (GUIDE-NFR-03). Equal schedules keep the held
 * object (GUIDE-FR-55).
 */
@Immutable
class RowSchedule(val programmes: List<GuideProgramme>, val times: List<String>) {
    override fun equals(other: Any?): Boolean = other is RowSchedule && other.programmes == programmes

    override fun hashCode(): Int = programmes.hashCode()

    companion object {
        val EMPTY: RowSchedule = RowSchedule(emptyList(), emptyList())
    }
}

/** A rail entry (GUIDE-FR-21). Custom lists arrive with channel management (M3). */
@Immutable
sealed interface RailEntry {
    val id: String

    data object Favourites : RailEntry {
        override val id: String = "favourites"
    }

    data object All : RailEntry {
        override val id: String = "all"
    }

    data object Recent : RailEntry {
        override val id: String = "recent"
    }

    data class Group(val group: LiveGroup) : RailEntry {
        override val id: String = "group:" + group.groupKey
    }
}

/** A rail row as shown: the entry and its trailing count, when it has one (GUIDE-FR-24). */
@Immutable
data class RailItem(val entry: RailEntry, val count: Int?)

/** The list on screen: a new [serial] for every list read, so the grid resets its scroll only then. */
@Immutable
class ListView(val entry: RailEntry, val list: ChannelList, val serial: Int, val searching: Boolean) {
    val size: Int get() = list.size
}

enum class GuidePhase { LOADING, EMPTY_LIBRARY, READY }

/** What the hero shows (GUIDE-FR-60): set by focus, never by data arriving. */
@Immutable
data class GuideSelection(val row: GuideRowData, val programme: GuideProgramme?)

/**
 * Where focus goes next (GUIDE-FR-27, -43, -82, -93). [column] is the row's selected column: −1
 * the channel column, ≥ 0 a block. A new [serial] makes a repeated request a new one.
 */
@Immutable
data class GuideFocus(val index: Int, val column: Int, val serial: Int)

/** The programme actions dialog (GUIDE-FR-79), with the row and block it was opened from. */
@Immutable
data class ActionsTarget(val row: GuideRowData, val programme: GuideProgramme, val column: Int)
