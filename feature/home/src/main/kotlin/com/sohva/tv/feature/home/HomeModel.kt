package com.sohva.tv.feature.home

import kotlinx.coroutines.flow.emitAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.data.home.RecentChannel
import com.sohva.tv.core.data.home.ResumeState
import com.sohva.tv.core.data.vod.ContinueItem
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.vod.VodText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home for the life of one entry (spec 02 §3.4: every arrival is fresh). Rows are built off the
 * main thread from the Continue watching projection and the recent channels read at entry; while
 * the structure is locked (the viewer below the first row) cards keep their places and only their
 * values follow the data (HOME-FR-50…52). The hero follows focus after it has rested 180 ms; focus
 * changes reach the model through callbacks, so Home recomposes only when the hero changes (§9.6).
 * The rows follow the profile's layout; a hidden row's data is never read (HOME-FR-87).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeModel(private val env: HomeEnvironment) : ViewModel() {
    private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
    val rows: StateFlow<List<HomeRow>> = _rows.asStateFlow()

    private val _hero = MutableStateFlow<HeroSubject>(HeroSubject.Welcome)
    val hero: StateFlow<HeroSubject> = _hero.asStateFlow()

    private val _details = MutableStateFlow<HeroDetails?>(null)
    val details: StateFlow<HeroDetails?> = _details.asStateFlow()

    /**
     * The minute ticker (HOME-FR-55): the clock and progress bars read it; nothing re-queries on it.
     * It ticks on the minute boundary, and only while the screen shows it.
     */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(env.now())
            delay(MINUTE_MS - env.now() % MINUTE_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), env.now())

    val resume: StateFlow<ResumeState> get() = env.resume

    /** The chosen time zone (HOME-FR-56); only it and the recent channels are read from the settings (§9.6). */
    val timeZone: StateFlow<String?> = env.timeZone.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val channels = MutableStateFlow<List<RecentChannel>?>(null)

    /** Settings › Home's layout; a change (after coming back from Settings) rebuilds the rows. */
    private val layout = env.layout.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** HOME-FR-32: Trakt's lists only after Continue watching's first read has settled, and only while a Trakt row is shown. */
    private val trakt = flow {
        emit(TraktLists.EMPTY)
        env.resume.filter { it.settled }.first()
        emitAll(
            layout.flatMapLatest { l ->
                val shown = l != null && (l.isShown(HomeRow.WATCH_NEXT) || l.isShown(HomeRow.RECOMMENDED) || l.added.any(l::isShown))
                if (shown) env.trakt else flowOf(TraktLists.EMPTY)
            },
        )
    }

    /** Today's games only while the row is shown; the feed itself runs for Search and reminders (HOME-FR-87). */
    private val games = layout.flatMapLatest { l -> if (l?.isShown(HomeRow.SPORT) == true) env.sportGames else flowOf(emptyList()) }

    private val _firstSync = MutableStateFlow(false)

    /** HOME-FR-48: the note that Trakt's history has not synced yet. */
    val firstSync: StateFlow<Boolean> = _firstSync.asStateFlow()

    private val _empty = MutableStateFlow(false)

    /** Home is empty (HOME-FR-46): both reads answered, no status card, no rows. Welcome then takes focus. */
    val empty: StateFlow<Boolean> = _empty.asStateFlow()
    /**
     * The card focus was last on and its row (HOME-FR-97). The model outlives the screen while
     * another screen is on top, so Home comes back to them instead of the first row. Plain fields:
     * written on focus, read once per entry, never drawn.
     */
    internal var returnCard: String? = null
    internal var returnRow: String? = null

    private var latest: List<HomeRow> = emptyList()
    private var locked = false
    private var focused: HeroSubject? = null
    private var heroJob: Job? = null
    private var detailsJob: Job? = null

    init {
        viewModelScope.launch {
            // A hidden row reads nothing (HOME-FR-87): the channels are read only when their row is shown.
            val shown = layout.first { it != null }!!.isShown(HomeRow.RECENT)
            channels.value = if (shown) runCatching { env.recentChannels(env.now()) }.getOrDefault(emptyList()) else emptyList()
        }
        viewModelScope.launch {
            // Never the default order first: the rows wait for the profile's layout (already in memory).
            combine(env.resume, channels, games, trakt, layout.filterNotNull()) { resume, recent, games, lists, layout ->
                _firstSync.value = lists.firstSync && resume.settled
                Triple(build(resume, recent.orEmpty(), games, lists, layout), resume, recent)
            }.collect { (rows, resume, recent) ->
                latest = rows
                publish()
                _empty.value = rows.isEmpty() && resume.settled && recent != null
                if (focused == null) apply(idle())
            }
        }
    }

    /** The screen reports the lock: list scrolled, or the last focused card below the first row (HOME-FR-50). */
    fun setLocked(value: Boolean) {
        if (value == locked) return
        locked = value
        publish()
    }

    private fun publish() {
        val shown = _rows.value
        _rows.value = if (locked && shown.isNotEmpty()) StructureLock.merge(shown, latest) else latest
    }

    /** A card took focus (its subject), or focus left the rows (null): the hero follows (HOME-FR-62). */
    fun focus(subject: HeroSubject?) {
        focused = subject
        heroJob?.cancel()
        if (subject == null) {
            apply(idle())
            return
        }
        heroJob = viewModelScope.launch {
            delay(HERO_REST_MS)
            apply(subject)
        }
    }

    private fun apply(subject: HeroSubject) {
        if (subject == _hero.value) return
        _hero.value = subject
        detailsJob?.cancel()
        _details.value = null
        if (subject == HeroSubject.Welcome) return
        detailsJob = viewModelScope.launch {
            // Lookups wait for Continue watching to settle, the start-up read comes first (HOME-FR-26).
            env.resume.filter { it.settled }.first()
            _details.value = runCatching { env.heroDetails(subject) }.getOrNull()
        }
    }

    /** HOME-FR-61: the newest Continue watching title with progress, else a recent channel, else Welcome. */
    private fun idle(): HeroSubject {
        val rows = _rows.value
        rows.filterIsInstance<HomeRow.Resume>().firstOrNull()?.cards?.firstOrNull { it.fraction > 0f }?.let { return HeroSubject.Resume(it) }
        val recent = rows.filterIsInstance<HomeRow.Channels>().firstOrNull()?.cards.orEmpty()
        val at = now.value
        recent.firstOrNull { c -> c.channel.programme?.let { at in it.startAt until it.stopAt } == true }?.let { return HeroSubject.Channel(it) }
        return recent.firstOrNull()?.let(HeroSubject::Channel) ?: HeroSubject.Welcome
    }

    fun retryResume() = env.retryResume()

    fun open(card: ResumeCard, fromStart: Boolean = false) = env.resume(card, fromStart)

    fun open(card: ChannelCard) = env.playChannel(card)

    fun openGuide() = env.openGuide()

    fun open(card: SportCard) = env.openSportGame(card.event)

    fun open(card: TraktCard) = env.openTrakt(card)

    fun markWatched(card: ResumeCard) {
        viewModelScope.launch { env.markWatched(card) }
    }

    fun remove(card: ResumeCard) {
        viewModelScope.launch { env.remove(card) }
    }

    /** The shown rows in the layout's order (HOME-FR-87); a row with no cards is still left out (HOME-FR-01). */
    private fun build(resume: ResumeState, recent: List<RecentChannel>, games: List<SportEvent>, trakt: TraktLists, layout: HomeLayout): List<HomeRow> = buildList {
        for (id in layout.shownIds) {
            when (id) {
                HomeRow.CONTINUE -> when (resume) {
                    is ResumeState.Ready -> add(HomeRow.Resume(resume.items.take(RESUME_CARDS).map(::card).distinctBy { it.key }))
                    ResumeState.Loading -> add(HomeRow.Status(failed = false))
                    ResumeState.Failed -> add(HomeRow.Status(failed = true))
                    ResumeState.Empty -> Unit
                }
                HomeRow.WATCH_NEXT -> if (trakt.next.isNotEmpty()) add(HomeRow.Trakt(trakt.next.distinctBy { it.key }, next = true))
                HomeRow.SPORT -> if (games.isNotEmpty()) add(HomeRow.Sport(games.take(SPORT_CARDS).map { SportCard("sport:${it.id}", it) }, games.size))
                HomeRow.RECOMMENDED -> if (trakt.recommended.isNotEmpty()) add(HomeRow.Trakt(trakt.recommended.distinctBy { it.key }, next = false))
                HomeRow.RECENT -> if (recent.isNotEmpty()) add(HomeRow.Channels(recent.map { ChannelCard("channel:${it.id}", it) }.distinctBy { it.key }))
                // An added Trakt row (HOME-FR-94): drawn once its list has titles, like the built-in Trakt rows.
                else -> trakt.rows[id]?.takeIf { it.isNotEmpty() }?.let { add(HomeRow.Trakt(it.distinctBy { c -> c.key }, next = false, id = id)) }
            }
        }
    }

    companion object {
        const val HERO_REST_MS: Long = 180
        const val RESUME_CARDS: Int = 12
        const val SPORT_CARDS: Int = 6
        private const val MINUTE_MS = 60_000L

        /** A card from a Continue watching entry (HOME-FR-13): the display title, fraction and minutes left. */
        fun card(item: ContinueItem): ResumeCard {
            val fraction = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else item.fraction ?: 0f
            val left = if (item.durationMs > 0) maxOf(1, ((item.durationMs - item.positionMs) / MINUTE_MS).toInt()) else null
            val image = item.replacementPoster?.takeIf { item.replacePoster } ?: item.posterUrl ?: item.replacementPoster
            // A Discover card's key is its own (HOME-FR-23); its title is the addon's name as stored. The card
            // is landscape, so it shows the addon's background, as beta 23 did (decision "Discover card art").
            item.discover?.let { d -> return ResumeCard(item.contentKey, item, item.title, d.backdrop ?: item.posterUrl, fraction, left) }
            // Library cards are landscape too: the title's backdrop when one is known, else its poster.
            return ResumeCard("vod:${item.contentKey}", item, VodText.breadcrumbGroup(item.title), item.backdrop ?: image, fraction, left)
        }
    }
}

/** HOME-FR-51: while locked, each row keeps its cards and order; a card still in the data takes its new values. */
internal object StructureLock {
    fun merge(shown: List<HomeRow>, latest: List<HomeRow>): List<HomeRow> = shown.mapNotNull { row ->
        val next = latest.firstOrNull { it.key == row.key }
        when (row) {
            is HomeRow.Resume -> {
                val fresh = (next as? HomeRow.Resume)?.cards.orEmpty().associateBy { it.key }
                HomeRow.Resume(row.cards.map { fresh[it.key] ?: it })
            }
            is HomeRow.Channels -> {
                val fresh = (next as? HomeRow.Channels)?.cards.orEmpty().associateBy { it.key }
                HomeRow.Channels(row.cards.map { fresh[it.key] ?: it })
            }
            is HomeRow.Trakt -> {
                val fresh = (next as? HomeRow.Trakt)?.cards.orEmpty().associateBy { it.key }
                HomeRow.Trakt(row.cards.map { fresh[it.key] ?: it }, row.next, row.id)
            }
            is HomeRow.Sport -> {
                val next2 = next as? HomeRow.Sport
                val fresh = next2?.cards.orEmpty().associateBy { it.key }
                HomeRow.Sport(row.cards.map { fresh[it.key] ?: it }, next2?.total ?: row.total)
            }
            // The status card is not shown while locked.
            is HomeRow.Status -> null
        }
    }
}
