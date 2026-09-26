package com.sohva.tv.feature.discover.ui.landing

import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the hero shows (§5.1): the catalog's identity and artwork; only [synopsis] is ever replaced. */
data class Hero(val preview: MetaPreview?, val synopsis: String?)

/** What has focus on the landing, as the hero needs it. */
sealed interface LandingFocus {
    /** A shelf card, with the addon whose catalog it came from. */
    data class ShelfCard(val owner: String, val item: MetaPreview) : LandingFocus

    data class ContinueCard(val item: MetaPreview) : LandingFocus

    /** A loading placeholder or the empty-history button: the hero clears. */
    data object Empty : LandingFocus

    /** The rail or a Show all button: the hero stays as it was. */
    data object Other : LandingFocus
}

/**
 * The focused title's synopsis (FR-70, spec 50 §11 "Hero synopsis"). Focus is read here only (a
 * holder, not composition state), so a D-pad press never recomposes the shelves (§9 "Focus state").
 */
class HeroResolver(private val host: DiscoverHost, private val profile: String, scope: CoroutineScope) {
    private val _hero = MutableStateFlow(Hero(null, null))
    val hero: StateFlow<Hero> = _hero.asStateFlow()

    val focus = MutableStateFlow<LandingFocus>(LandingFocus.Other)
    val resumed = MutableStateFlow(false)
    private val failures = LinkedHashMap<String, Long>(16, 0.75f, true)

    init {
        scope.launch {
            combine(focus, resumed) { f, r -> f to r }.collectLatest { (f, active) -> resolve(f, active) }
        }
    }

    private suspend fun resolve(focus: LandingFocus, active: Boolean) {
        when (focus) {
            LandingFocus.Other -> Unit
            LandingFocus.Empty -> {
                delay(SETTLE_MS)
                _hero.value = Hero(null, null)
            }
            is LandingFocus.ContinueCard -> {
                delay(SETTLE_MS)
                _hero.value = Hero(focus.item, focus.item.description)
            }
            is LandingFocus.ShelfCard -> if (active) shelfCard(focus.owner, focus.item)
        }
    }

    private suspend fun shelfCard(owner: String, item: MetaPreview) {
        delay(SETTLE_MS)
        val key = "$owner|${item.type}|${item.id}"
        // Fresh saved details from the owner only, within 200 ms (FR-70).
        val cached = withTimeoutOrNull(CACHE_MS) {
            runCatching { host.browser.cachedDetails(profile, owner, item.type, item.id, freshOnly = true) }.getOrNull()
        }
        cached?.preview?.description?.let {
            _hero.value = Hero(item, it)
            return
        }
        val failedAt = synchronized(failures) { failures[key] }
        if (failedAt != null && host.clock.wallMillis() - failedAt < COOL_DOWN_MS) {
            _hero.value = Hero(item, item.description)
            return
        }
        // Never the catalog-language text while the localized one may be coming (lesson 15).
        _hero.value = Hero(item, null)
        delay(FETCH_AFTER_MS)
        val details = try {
            lookups.withLock {
                withTimeoutOrNull(FETCH_MS) { host.browser.details(profile, owner, item.type, item.id).value }
            }
        } catch (e: AddonException) {
            if (e.failure.revocation) {
                _hero.value = Hero(null, null)
                return
            }
            null
        }
        val synopsis = details?.preview?.description
        if (synopsis != null) {
            _hero.value = Hero(item, synopsis)
        } else {
            synchronized(failures) {
                failures[key] = host.clock.wallMillis()
                while (failures.size > MAX_FAILURES) failures.remove(failures.keys.first())
            }
            _hero.value = Hero(item, item.description)
        }
    }

    companion object {
        private const val SETTLE_MS = 120L
        private const val CACHE_MS = 200L
        private const val FETCH_AFTER_MS = 350L
        private const val FETCH_MS = 8_000L
        private const val COOL_DOWN_MS = 30_000L
        private const val MAX_FAILURES = 64

        /** One hero lookup at a time across the app (FR-70). */
        private val lookups = Mutex()
    }
}
