package com.sohva.tv.feature.trakt.demo

import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.shelf.TraktCard
import com.sohva.tv.feature.trakt.shelf.TraktShelf
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import com.sohva.tv.feature.trakt.store.TraktAccount
import com.sohva.tv.feature.trakt.store.TraktAccountStore
import kotlinx.coroutines.withContext

/**
 * The demo build's Trakt (spec 51 FR-37, TRAKT-30): a fictional connected account with a Watch next
 * shelf and recommendations, for screenshots. Trakt stays offline in that build, so nothing here is
 * ever sent; the tokens are placeholders. Seeded once per profile, so a disconnect in the demo sticks.
 */
object DemoTraktSeed {
    suspend fun seed(host: TraktHost, profile: String) {
        if (!host.offline || host.account(profile) != null) return
        // Its own marker, which a disconnect does not clear: the demo seeds each profile once.
        if (withContext(host.dispatchers.io) { host.store.long("demo-seeded:$profile") } != 0L) return
        val now = host.clock.wallMillis()
        withContext(host.dispatchers.io) {
            host.store.putLong("demo-seeded:$profile", now)
            val tokens = TraktTokens("demo-not-a-token", "demo-not-a-token", now + 90 * TraktHost.DAY_MS, TraktAccountStore.DEFAULT_LIFETIME)
            host.store.saveAccount(profile, TraktAccount("sohva-demo", "demo", tokens, reauthorize = false))
            // A first sync "done", so Home shows no first-sync note.
            host.store.putLong("activity:$profile", now)
        }
        host.shelves.save(profile, TraktShelfKind.WATCH_NEXT, TraktShelf(now, NEXT.mapIndexed { i, c -> c.copy(updatedAt = now - i * HOUR_MS) }))
        host.shelves.save(profile, TraktShelfKind.RECOMMENDED, TraktShelf(now, RECOMMENDED))
        host.accountsImported(listOf(profile))
    }

    private const val HOUR_MS = 60L * 60 * 1000

    private fun show(id: Long, title: String, year: Int, season: Int, number: Int, episode: String, overview: String) =
        TraktCard(TraktKind.SHOW, TraktIds(trakt = id), title, year, overview, null, null, season, number, episode)

    private fun film(id: Long, title: String, year: Int, overview: String) =
        TraktCard(TraktKind.MOVIE, TraktIds(trakt = id), title, year, overview, null, null)

    private val NEXT = listOf(
        show(900_001, "Harbour Lights", 2021, 2, 4, "The Night Ferry", "A coastal town keeps its secrets until the lighthouse goes dark."),
        show(900_002, "Northern Line", 2019, 1, 7, "Last Stop", "Two detectives ride the late trains of a fictional city."),
        show(900_003, "Garden of Clocks", 2023, 3, 1, "Spring Forward", "A family of watchmakers inherits a house where time runs oddly."),
    )

    private val RECOMMENDED = listOf(
        film(910_001, "The Quiet Orbit", 2022, "An astronaut and a radio operator talk across a delayed signal."),
        show(920_001, "Lakeside Kitchen", 2020, 1, 1, "Opening Night", "A small restaurant tries to survive its first winter.").copy(season = null, number = null, episodeTitle = null),
        film(910_002, "Paper Boats", 2018, "Three friends race homemade boats down a flooded street."),
        show(920_002, "Mountain Post", 2024, 1, 1, "Arrival", "Letters arrive late at a village beyond the pass.").copy(season = null, number = null, episodeTitle = null),
        film(910_003, "Late Summer Radio", 2021, "A night-time DJ answers calls from an island that is not on the map."),
    )
}
