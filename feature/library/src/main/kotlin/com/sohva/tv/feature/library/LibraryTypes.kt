package com.sohva.tv.feature.library

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.vod.Rail
import com.sohva.tv.core.data.vod.WallDestination
import com.sohva.tv.core.data.vod.WallItem
import com.sohva.tv.core.data.vod.WallRoom
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.vod.CustomGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The rail's two views (spec 40 VOD-FR-03/04). */
enum class RailView { GROUPS, GENRES }

/**
 * One rail row: History, a provider group, a genre or Unsorted. [label] is the provider's text
 * for a group, null for rows whose label is a string resource ([WallDestination] tells which).
 * [count] is null where the rail shows none (History; custom groups).
 */
@Immutable
data class RailRow(val key: String, val destination: WallDestination, val label: String?, val count: Int?)

/**
 * The wall on screen (spec 40 §4.2). While a new destination or search loads, the previous
 * window stays drawn with [current] false: its cards cannot take focus (VOD-FR-14).
 */
@Immutable
data class WallState(
    val destination: WallDestination?,
    val window: WallWindow,
    val current: Boolean,
    val failed: Boolean,
) {
    companion object {
        val Idle: WallState = WallState(null, WallWindow.Empty, current = false, failed = false)
    }
}

/** The result of Options › Refresh (spec 40 VOD-FR-46). */
sealed interface RefreshNote {
    data class Imported(val movies: Int, val series: Int) : RefreshNote
    data object NoSource : RefreshNote
    data class Failed(val error: AppError) : RefreshNote
}

/**
 * What a wall needs from the app (plan/03 §4.6). Every read runs on the database dispatcher
 * behind the environment; tests hand in a gated one.
 */
interface LibraryEnvironment {
    val room: WallRoom

    /** Any write that can change the wall or its rail. */
    fun changes(): Flow<Unit>

    /** The shown groups in the room's order and History's shortcut rule (spec 42 ORG-FR-19, ORG-11). */
    suspend fun rail(): Rail

    /** Groups of your own in their saved order (VOD-FR-04, -11); device-wide. */
    fun customGroups(): Flow<List<CustomGroup>> = flowOf(emptyList())

    /** Titles per genre wire value, `""` for Unsorted (VOD-FR-04). */
    suspend fun genreCounts(): Map<String, Int>

    suspend fun page(destination: WallDestination, search: String, from: WallItem?, forward: Boolean, limit: Int): List<WallItem>

    /** The films to tick as watched among (content key, film identity) pairs, ≤ 200 (VOD-FR-37). */
    suspend fun watched(films: List<Pair<String, String?>>): Set<String>

    /**
     * Looks up the titles on screen that metadata has not settled (spec 41 Q10), one a second;
     * cancelled by the caller when focus moves on.
     */
    suspend fun lookUpVisible(items: List<WallItem>)

    /** Whether TVmaze may have supplied what this wall shows: its credit then shows (spec 41 Q9). */
    suspend fun tvmazeCredit(): Boolean

    /** Imports every enabled source whose scope includes films and series. */
    suspend fun refresh(): RefreshNote

    /** Opens a card's details page (spec 40 §3). */
    fun open(item: WallItem)

    /**
     * Options › Edit: the library manager for this room (spec 42 §3) at [group]: the provider
     * group's name, `@history`, or null for genre and other destinations.
     */
    fun openManager(group: String?)

    /** Options › Back leaves the screen like the Back key (VOD-FR-48). */
    fun leave()
}
