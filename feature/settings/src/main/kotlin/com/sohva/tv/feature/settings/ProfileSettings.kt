package com.sohva.tv.feature.settings

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.data.profile.GroupChoice
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.profile.ParentalPin
import com.sohva.tv.core.model.profile.Restriction
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Profiles group and Parental controls do to the world (spec 04); the app implements it. */
interface ProfileSettingsServices {
    val household: StateFlow<Household>

    suspend fun add(name: String): Boolean

    suspend fun remove(profileId: String)

    /** Through the PIN when entering it needs one (spec 01 SHELL-FR-33); the app routes. */
    fun switchTo(profileId: String)

    suspend fun setAskAtStart(on: Boolean)

    suspend fun restriction(profileId: String): Restriction

    suspend fun anyRestricted(): Boolean

    suspend fun groupChoices(room: OrgRoom): List<GroupChoice>

    suspend fun setAllowed(profileId: String, room: OrgRoom, groupKey: String, allowed: Boolean)

    suspend fun setPin(pin: String): Outcome<Unit>

    suspend fun removePin(current: String): Boolean

    suspend fun changePin(current: String, next: String): Outcome<Boolean>
}

/**
 * The profile rows' state (spec 04 §5.3): the profile being limited (for as long as Settings is
 * open, PROF-FR-21), its restriction, whether any profile is restricted, the typed name and PIN.
 */
@Immutable
data class ProfileSettingsState(
    val editedId: String? = null,
    val restriction: Restriction = Restriction.NONE,
    val anyRestricted: Boolean = false,
    val name: String = "",
    val pin: String = "",
    val newPin: String = "",
    val busy: Boolean = false,
    val status: SettingsMessage? = null,
    val statusIsError: Boolean = false,
)

/** Profiles and Parental controls for the life of the Settings screen, owned by [SettingsModel]. */
class ProfileSettings internal constructor(private val services: ProfileSettingsServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ProfileSettingsState())
    val state: StateFlow<ProfileSettingsState> = _state.asStateFlow()

    val household: StateFlow<Household> get() = services.household

    init {
        scope.launch { services.household.collect { reload() } }
    }

    /** The profile the group rows edit: the chosen one while it exists, else the active one (PROF-FR-21). */
    fun edited(h: Household = household.value): String = _state.value.editedId?.takeIf { id -> h.shown.any { it.id == id } } ?: h.active.id

    fun typeName(value: String) = _state.update { it.copy(name = value.take(NAME_INPUT_MAX)) }

    fun typePin(value: String) = _state.update { it.copy(pin = ParentalPin.cut(value)) }

    fun typeNewPin(value: String) = _state.update { it.copy(newPin = ParentalPin.cut(value)) }

    /** Enabled with a name and room for one more (PROF-FR-03). */
    fun canAdd(s: ProfileSettingsState, h: Household): Boolean = s.name.isNotBlank() && h.shown.size < com.sohva.tv.core.model.profile.Profiles.MAX

    fun add() = act {
        if (services.add(_state.value.name)) _state.update { it.copy(name = "") }
        null
    }

    fun remove(profileId: String) = act {
        services.remove(profileId)
        null
    }

    fun switchTo(profileId: String) = services.switchTo(profileId)

    fun setAskAtStart(on: Boolean) {
        scope.launch { services.setAskAtStart(on) }
    }

    fun edit(profileId: String) {
        _state.update { it.copy(editedId = profileId) }
        scope.launch { reload() }
    }

    suspend fun choices(room: OrgRoom): List<GroupChoice> = runCatching { services.groupChoices(room) }.getOrDefault(emptyList())

    /** Saves at once (PROF-FR-22). */
    fun toggle(room: OrgRoom, groupKey: String) {
        val profile = edited()
        val allowed = groupKey !in _state.value.restriction.of(room)
        _state.update { it.copy(restriction = it.restriction.with(room, if (allowed) it.restriction.of(room) + groupKey else it.restriction.of(room) - groupKey)) }
        scope.launch {
            services.setAllowed(profile, room, groupKey, allowed)
            reload()
        }
    }

    /** "Enable" (PROF-FR-33). */
    fun enablePin() = act {
        when (val result = services.setPin(_state.value.pin)) {
            is Outcome.Ok -> {
                _state.update { it.copy(pin = "") }
                SettingsMessage.Text(R.string.parental_saved)
            }
            is Outcome.Failed -> result.error
        }
    }

    /**
     * "Remove / change PIN" (PROF-FR-33) after the current PIN: with new digits typed the PIN
     * changes and the locks stay (decision "Spec 04 open questions", Q7); without, the PIN and
     * every profile's locks go.
     */
    fun removeOrChangePin() = act {
        val s = _state.value
        if (s.newPin.isEmpty()) {
            val ok = services.removePin(s.pin)
            _state.update { it.copy(pin = "") }
            return@act if (ok) SettingsMessage.Text(R.string.parental_removed) else wrong()
        }
        when (val result = services.changePin(s.pin, s.newPin)) {
            is Outcome.Ok -> {
                _state.update { it.copy(pin = "", newPin = "") }
                if (result.value) SettingsMessage.Text(R.string.parental_saved) else wrong()
            }
            is Outcome.Failed -> result.error
        }
    }

    private fun wrong(): SettingsMessage {
        _state.update { it.copy(statusIsError = true) }
        return SettingsMessage.Text(R.string.pin_wrong)
    }

    private suspend fun reload() {
        val restriction = runCatching { services.restriction(edited()) }.getOrDefault(Restriction.NONE)
        val any = runCatching { services.anyRestricted() }.getOrDefault(false)
        _state.update { it.copy(restriction = restriction, anyRestricted = any) }
    }

    private fun act(block: suspend () -> Any?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, statusIsError = false) }
        scope.launch {
            try {
                when (val result = block()) {
                    is com.sohva.tv.core.model.error.AppError -> _state.update { it.copy(status = SettingsMessage.Failure(result), statusIsError = true) }
                    is SettingsMessage -> _state.update { it.copy(status = result) }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        /** The field cuts at 24 (PROF-FR-03); the store trims and cuts again. */
        const val NAME_INPUT_MAX = 24
    }
}
