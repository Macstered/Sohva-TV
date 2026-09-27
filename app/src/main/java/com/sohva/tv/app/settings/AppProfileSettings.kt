package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.profile.GroupChoice
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.profile.Restriction
import com.sohva.tv.feature.settings.ProfileSettingsServices
import kotlinx.coroutines.flow.StateFlow

/** Settings' profile rows and Parental controls on the profile store (spec 04); [switch] routes through the PIN gate. */
class AppProfileSettings(private val graph: AppGraph, private val switch: (String) -> Unit) : ProfileSettingsServices {
    private val store get() = graph.data.profiles

    override val household: StateFlow<Household> get() = store.household

    override suspend fun add(name: String): Boolean = store.add(name)

    /** A removed profile takes its Discover data and its Trakt account with it (plan/04 §15.8, spec 51 FR-11). */
    override suspend fun remove(profileId: String) {
        store.remove(profileId)
        graph.discover?.forgetProfile(profileId)
        graph.trakt?.disconnect(profileId)
    }

    override fun switchTo(profileId: String) = switch(profileId)

    override suspend fun setAskAtStart(on: Boolean) = store.setAskAtStart(on)

    override suspend fun restriction(profileId: String): Restriction = store.restriction(profileId)

    override suspend fun anyRestricted(): Boolean = store.restrictedIds().isNotEmpty()

    override suspend fun groupChoices(room: OrgRoom): List<GroupChoice> = store.groupChoices(room)

    override suspend fun setAllowed(profileId: String, room: OrgRoom, groupKey: String, allowed: Boolean) =
        store.setAllowed(profileId, room, groupKey, allowed)

    override suspend fun setPin(pin: String): Outcome<Unit> = store.setPin(pin)

    override suspend fun removePin(current: String): Boolean = store.removePin(current)

    override suspend fun changePin(current: String, next: String): Outcome<Boolean> = store.changePin(current, next)
}
