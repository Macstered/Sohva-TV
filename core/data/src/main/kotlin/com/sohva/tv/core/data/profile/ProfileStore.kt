package com.sohva.tv.core.data.profile

import com.sohva.tv.core.data.database.LockedChannelEntity
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.ProfileDao
import com.sohva.tv.core.data.prefs.AppPreferences
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.profile.ParentalPin
import com.sohva.tv.core.model.profile.Profiles
import com.sohva.tv.core.model.profile.Restriction
import com.sohva.tv.core.model.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A group a profile can be limited to (PROF-FR-22): one per key, with the sources that carry it. */
data class GroupChoice(val key: String, val name: String?, val sources: List<String>)

/**
 * The household's profiles, the active one, what each may see, locked channels and the PIN
 * (spec 04). The household comes from the start snapshot ([seed]) and then follows the
 * preferences; database and secret reads run on [io].
 */
class ProfileStore(
    private val prefs: AppPreferences,
    private val dao: () -> ProfileDao,
    private val secrets: SecretValues,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(Household())
    private val following = AtomicBoolean(false)
    private val restrictionsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val household: StateFlow<Household> = state

    /** The active profile's id: what every per-profile read and write uses (PROF-FR-07). */
    val activeId: String get() = state.value.active.id

    val activeChanges: Flow<String> = state.map { it.active.id }.distinctUntilChanged()

    /** "A PIN exists", for controls that need one (Channel management's lock, spec 04 PROF-FR-40). */
    val pinConfigured: StateFlow<Boolean> = state.map { it.pinConfigured }.stateIn(scope, SharingStarted.Eagerly, false)

    /** Whenever what the active profile sees may have changed: a switch or an edited restriction. */
    val changes: Flow<Unit> = merge(activeChanges.map { }, restrictionsChanged)

    /**
     * The household read for the first frame; from then on the preferences are followed. Once
     * followed, a later seed changes nothing: its snapshot may be older than what the preferences
     * already delivered, and they would not deliver it again.
     */
    fun seed(household: Household) {
        if (!following.compareAndSet(false, true)) return
        state.value = household
        scope.launch { prefs.household.collect { state.value = it } }
    }

    suspend fun add(name: String): Boolean {
        var added = false
        edit { h -> Profiles.add(h.stored, name, Profiles.newId(clock.wallMillis()))?.let { added = true; h.copy(stored = it) } ?: h }
        return added
    }

    /**
     * Removes [profileId] with everything it kept (PROF-FR-06): its database rows, its keys; the
     * first viewer becomes active when it was the active one. The first viewer cannot be removed.
     */
    suspend fun remove(profileId: String) {
        if (profileId == Profiles.DEFAULT_ID) return
        withContext(io) { dao().deleteProfile(profileId) }
        prefs.forgetProfile(profileId)
        edit { h ->
            h.copy(
                stored = h.stored.filterNot { it.id == profileId },
                activeId = if (h.activeId == profileId) Profiles.DEFAULT_ID else h.activeId,
            )
        }
        restrictionsChanged.tryEmit(Unit)
    }

    suspend fun switchTo(profileId: String) {
        edit { h -> if (h.shown.any { it.id == profileId }) h.copy(activeId = profileId) else h }
    }

    suspend fun setAskAtStart(on: Boolean) {
        edit { it.copy(askAtStart = on) }
    }

    suspend fun restriction(profileId: String): Restriction = withContext(io) {
        val rows = dao().allowed(profileId)
        fun keys(room: OrgRoom) = rows.filter { it.room == room.wire }.mapTo(HashSet()) { it.groupKey }
        Restriction(keys(OrgRoom.LIVE), keys(OrgRoom.MOVIES), keys(OrgRoom.SERIES))
    }

    suspend fun restrictedIds(): Set<String> = withContext(io) { dao().restrictedProfiles().toHashSet() }

    /** Adds or removes one allowed group; removing the last lifts the room's restriction (PROF-FR-22). */
    suspend fun setAllowed(profileId: String, room: OrgRoom, groupKey: String, allowed: Boolean) {
        withContext(io) {
            if (allowed) dao().allow(ProfileAllowedGroupEntity(profileId, room.wire, groupKey)) else dao().disallow(profileId, room.wire, groupKey)
        }
        restrictionsChanged.tryEmit(Unit)
    }

    /** The room's groups across enabled sources, one per key, named by the first non-blank name (PROF-FR-22). */
    suspend fun groupChoices(room: OrgRoom): List<GroupChoice> = withContext(io) {
        val byKey = LinkedHashMap<String, Pair<String?, LinkedHashSet<String>>>()
        for (row in dao().groupChoices(room.wire)) {
            val entry = byKey.getOrPut(row.groupKey) { null to LinkedHashSet() }
            byKey[row.groupKey] = (entry.first ?: row.name.takeIf { it.isNotBlank() }) to entry.second.apply { add(row.sourceName) }
        }
        byKey.map { (key, v) -> GroupChoice(key, v.first, v.second.toList()) }
    }

    /** Entering [targetId] asks for the PIN (PROF-FR-34 item 2). */
    suspend fun entryNeedsPin(targetId: String): Boolean {
        val restricted = restrictedIds()
        return ParentalPin.entryNeedsPin(state.value.pinConfigured, restricted.isNotEmpty(), targetId in restricted)
    }

    /** Settings and the managers ask a restricted profile for the PIN (PROF-FR-34 item 3, PROF-FR-35). */
    suspend fun managementNeedsPin(): Boolean = state.value.pinConfigured && activeId in restrictedIds()

    /** A lock counts only while a PIN exists (PROF-FR-42). */
    suspend fun isLocked(channelKey: String): Boolean =
        state.value.pinConfigured && withContext(io) { dao().isLocked(activeId, channelKey) }

    suspend fun lockedKeys(): Set<String> = withContext(io) { dao().lockedKeys(activeId).toHashSet() }

    suspend fun setLocked(channelKey: String, locked: Boolean) {
        val profile = activeId
        withContext(io) { if (locked) dao().lock(LockedChannelEntity(profile, channelKey)) else dao().unlock(profile, channelKey) }
    }

    /** Stores a new household PIN, encrypted (PROF-FR-33). */
    suspend fun setPin(pin: String): Outcome<Unit> {
        if (!ParentalPin.valid(pin)) return Outcome.Failed(AppError.PinFormat)
        val written = secrets.write(PIN_KEY, pin)
        if (written is Outcome.Ok) edit { it.copy(pinConfigured = true) }
        return written
    }

    /** Off the main thread, compared in constant time; no PIN or an unreadable one is "wrong" (PROF-FR-32). */
    suspend fun verifyPin(pin: String): Boolean {
        val stored = (secrets.read(PIN_KEY) as? Outcome.Ok)?.value
        return withContext(io) { ParentalPin.same(stored, pin) }
    }

    /** The PIN's digits for a backup (spec 71 BACKUP-FR-07 step 3); null without one or when unreadable. */
    suspend fun pinForBackup(): String? = (secrets.read(PIN_KEY) as? Outcome.Ok)?.value

    /** A restore's PIN (spec 71 BACKUP-FR-18 steps 7, 10): stored when the backup has one, removed when not. */
    suspend fun restorePin(pin: String?): Outcome<Unit> = secrets.write(PIN_KEY, pin)

    /** Removes the PIN after the current one, dropping every profile's locks (PROF-FR-33). */
    suspend fun removePin(current: String): Boolean {
        if (!verifyPin(current)) return false
        edit { it.copy(pinConfigured = false) }
        withContext(io) { dao().unlockAll() }
        secrets.write(PIN_KEY, null)
        return true
    }

    /** Replaces the PIN after the current one, keeping the locks (decision "Spec 04 open questions", Q7). */
    suspend fun changePin(current: String, next: String): Outcome<Boolean> {
        if (!ParentalPin.valid(next)) return Outcome.Failed(AppError.PinFormat)
        if (!verifyPin(current)) return Outcome.Ok(false)
        return when (val written = secrets.write(PIN_KEY, next)) {
            is Outcome.Ok -> Outcome.Ok(true)
            is Outcome.Failed -> written
        }
    }

    /**
     * After the first frame: the secret store wins over the mirrored flag (PROF-FR-31). Without a
     * PIN no lock may remain (PROF-FR-42).
     */
    suspend fun reconcilePin() {
        val stored = secrets.read(PIN_KEY)
        if (stored !is Outcome.Ok) return
        val exists = stored.value != null
        if (exists != state.value.pinConfigured) edit { it.copy(pinConfigured = exists) }
        if (!exists) withContext(io) { dao().unlockAll() }
    }

    private suspend fun edit(change: (Household) -> Household) {
        var next: Household? = null
        prefs.editHousehold { h -> change(h).also { next = it } }
        next?.let { state.value = it }
    }

    companion object {
        /** Beta 23's key (spec 04 §6), in the app's secret store. */
        const val PIN_KEY: String = "parental_pin_v1"
    }
}
