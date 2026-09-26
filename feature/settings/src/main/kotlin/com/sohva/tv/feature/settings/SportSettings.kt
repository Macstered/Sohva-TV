package com.sohva.tv.feature.settings

import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.sport.Competition
import com.sohva.tv.core.model.sport.SportFeedStatus
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.text.StreamTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Settings › Sohva Sport does to the world (spec 60 §4.1); the app implements it. */
interface SportSettingsServices {
    fun hasKey(): Flow<Boolean>

    /** Saves [key], or removes the key when it is blank (SPORT-FR-02). */
    suspend fun saveKey(key: String): Outcome<Unit>

    fun follows(): Flow<SportFollows>

    suspend fun setFollows(follows: SportFollows)

    /** The provider's competitions for [sport] (cached a week); throws on failure. */
    suspend fun competitions(sport: SportType): List<Competition>

    fun priority(): Flow<List<String>>

    suspend fun setPriority(codes: List<String>)

    fun status(): Flow<SportFeedStatus>
}

/** The section's own messages (SPORT-FR-13: they show in the section itself). */
enum class SportMessage { KEY_SAVED, KEY_REMOVED, KEY_INVALID, SAVE_FAILED, PRIORITY_SAVED }

sealed interface CompetitionList {
    data object Loading : CompetitionList

    data object Failed : CompetitionList

    data class Loaded(val all: List<Competition>) : CompetitionList
}

data class SportSettingsState(
    val typedKey: String = "",
    val codes: String = "",
    val message: SportMessage? = null,
    val menuOpen: Boolean = false,
    val editing: SportType = SportType.FOOTBALL,
    val search: String = "",
    /** Loaded in this Settings visit only (SPORT-FR-06). */
    val lists: Map<SportType, CompetitionList> = emptyMap(),
)

/** Settings › Sohva Sport for the life of the Settings screen. */
class SportSettings internal constructor(private val services: SportSettingsServices, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SportSettingsState())
    val state: StateFlow<SportSettingsState> = _state.asStateFlow()
    val hasKey: StateFlow<Boolean> = services.hasKey().stateIn(scope, SharingStarted.Eagerly, false)
    val follows: StateFlow<SportFollows> = services.follows().stateIn(scope, SharingStarted.Eagerly, SportFollows.DEFAULT)
    val status: StateFlow<SportFeedStatus?> = services.status().stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { _state.update { it.copy(codes = services.priority().first().joinToString(", ")) } }
    }

    fun typeKey(value: String) = _state.update { it.copy(typedKey = value.take(KEY_MAX)) }

    fun typeCodes(value: String) = _state.update { it.copy(codes = value.take(CODES_MAX)) }

    /**
     * SPORT-FR-01, -02: trimmed; a line break or more than 512 characters is refused; blank removes
     * the key, closes the menu and forgets the loaded lists. Saving does not refresh by itself.
     */
    fun saveKey() {
        val key = _state.value.typedKey.trim()
        if (key.length > KEY_MAX || key.any { it == '\n' || it == '\r' }) {
            _state.update { it.copy(message = SportMessage.KEY_INVALID) }
            return
        }
        scope.launch {
            val saved = services.saveKey(key)
            _state.update {
                when {
                    saved is Outcome.Failed -> it.copy(message = SportMessage.SAVE_FAILED)
                    key.isEmpty() -> it.copy(message = SportMessage.KEY_REMOVED, menuOpen = false, lists = emptyMap())
                    else -> it.copy(message = SportMessage.KEY_SAVED, typedKey = "")
                }
            }
        }
    }

    /** SPORT-FR-10: known codes only, canonical, distinct, at most 8; the field shows what was saved. */
    fun saveCodes() {
        val codes = StreamTags.normalizeLanguages(_state.value.codes.split(','))
        scope.launch {
            services.setPriority(codes)
            _state.update { it.copy(codes = codes.joinToString(", "), message = SportMessage.PRIORITY_SAVED) }
        }
    }

    fun openMenu(open: Boolean) {
        _state.update { it.copy(menuOpen = open) }
        if (open) load(_state.value.editing)
    }

    /** OK on a sport makes it the one being edited and clears the search (SPORT-FR-04). */
    fun edit(sport: SportType) {
        _state.update { it.copy(editing = sport, search = "") }
        load(sport)
    }

    fun search(query: String) = _state.update { it.copy(search = query.take(SEARCH_MAX)) }

    /** SPORT-FR-05: the sport being edited in or out at once. */
    fun toggleSport() {
        val sport = _state.value.editing
        val f = follows.value
        write(f.copy(sports = if (sport in f.sports) f.sports - sport else f.sports + sport))
    }

    fun toggleCompetition(competition: Competition) {
        val f = follows.value
        write(f.copy(competitions = if (competition.key in f.competitions) f.competitions - competition.key else f.competitions + competition.key))
    }

    fun retry() = load(_state.value.editing, force = true)

    private fun write(follows: SportFollows) {
        scope.launch { services.setFollows(follows) }
    }

    private fun load(sport: SportType, force: Boolean = false) {
        if (!sport.hasCompetitions) return
        val current = _state.value.lists[sport]
        if (!force && (current is CompetitionList.Loaded || current == CompetitionList.Loading)) return
        _state.update { it.copy(lists = it.lists + (sport to CompetitionList.Loading)) }
        scope.launch {
            val result = try {
                CompetitionList.Loaded(services.competitions(sport))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CompetitionList.Failed
            }
            _state.update { it.copy(lists = it.lists + (sport to result)) }
        }
    }

    companion object {
        const val KEY_MAX: Int = 512
        const val CODES_MAX: Int = 80
        const val SEARCH_MAX: Int = 100

        /**
         * The rows to show (SPORT-FR-07): [query] matches name or country, case-insensitive; followed
         * first, then country, then name.
         */
        fun visible(all: List<Competition>, followed: Set<String>, query: String): List<Competition> {
            val q = query.trim().lowercase()
            return all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.country?.lowercase()?.contains(q) == true }
                .sortedWith(compareBy({ it.key !in followed }, { it.country?.lowercase() ?: "" }, { it.name.lowercase() }))
        }
    }
}
