package com.sohva.tv.feature.sport.dictionary

import android.database.sqlite.SQLiteDatabase
import com.sohva.tv.core.model.sport.ProviderReference
import com.sohva.tv.core.model.sport.ReferenceKind
import com.sohva.tv.core.model.sport.Side
import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.core.model.sport.pairing.DictionaryNames
import com.sohva.tv.core.model.sport.pairing.EventNames
import com.sohva.tv.core.model.sport.pairing.MatchText
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

data class DictionaryLookupStats(val bindings: Int = 0, val aliasPages: Int = 0, val guardPages: Int = 0, val phrases: Int = 0, val accountedBytes: Long = 0, val boundedFallback: Boolean = false)

/** One scan's lookups, with no permanent participant cache. Each cursor is narrow and <=256 rows. */
class AssetPairingDictionary(private val asset: DictionaryAsset) : PairingDictionary {
    var lastStats: DictionaryLookupStats = DictionaryLookupStats()
        private set

    override suspend fun resolve(events: List<SportEvent>): DictionaryNames {
        if (events.isEmpty()) {
            lastStats = DictionaryLookupStats()
            return DictionaryNames.NONE
        }
        return asset.read { db, revision ->
            val lookup = Lookup(db)
            try {
                val references = events.flatMap { listOfNotNull(it.home.reference, it.away.reference, it.competitionReference) }.distinct()
                val bindings = lookup.bind(references)
                val names = LinkedHashMap<String, EventNames>()
                val competitions = LinkedHashMap<String, Set<String>>()
                for (event in events) {
                    currentCoroutineContext().ensureActive()
                    val day = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    val sport = if (event.sport == SportType.NBA) "basketball" else event.sport.provider
                    val homeKind = when (event.sport) {
                        SportType.MMA -> ReferenceKind.FIGHTER
                        SportType.FORMULA_1 -> ReferenceKind.COMPETITION
                        else -> ReferenceKind.TEAM
                    }
                    val awayKind = if (event.sport == SportType.FORMULA_1) ReferenceKind.CIRCUIT else homeKind
                    val homeId = lookup.identity(event.home, sport, homeKind, day, bindings)
                    val awayId = lookup.identity(event.away, sport, awayKind, day, bindings)
                    val competitionId = event.competitionReference?.let { reference ->
                        val productSport = if (reference.product == "nba") "basketball" else reference.product
                        if (reference.kind == ReferenceKind.COMPETITION && productSport == sport) bindings[reference] else null
                    }
                        ?: if (event.competitionReference == null) lookup.exact(sport, ReferenceKind.COMPETITION, event.competition, day) else null
                    val supporting = if (competitionId == null && event.competitionReference == null)
                        lookup.memberships(homeId, day).intersect(lookup.memberships(awayId, day)) else emptySet()
                    val home = lookup.aliases(homeId, day)
                    val away = lookup.aliases(awayId, day)
                    val homeGuards = lookup.guards(sport, homeKind, home + MatchText.normalise(event.home.name), day)
                    val awayGuards = lookup.guards(sport, awayKind, away + MatchText.normalise(event.away.name), day)
                    lookup.account(home + homeGuards)
                    lookup.account(away + awayGuards)
                    names[event.id] = EventNames(homeId, awayId, competitionId, home, away, homeGuards, awayGuards, supporting, day)
                    for (competition in supporting + listOfNotNull(competitionId)) {
                        val aliases = lookup.aliases(competition, day)
                        lookup.account(aliases)
                        val datedIdentity = "$competition@$day"
                        competitions[datedIdentity] = competitions[datedIdentity].orEmpty() + aliases
                    }
                    yield()
                }
                lastStats = lookup.stats()
                DictionaryNames(revision, names, competitions)
            } catch (error: BudgetExceeded) {
                lastStats = lookup.stats().copy(boundedFallback = true)
                // Never ship a partial alias group into a scan: overflow falls back as a whole.
                DictionaryNames(revision = "$revision:bounded")
            }
        } ?: DictionaryNames.NONE.also { lastStats = DictionaryLookupStats() }
    }

    private class BudgetExceeded : RuntimeException()

    private class Lookup(private val db: SQLiteDatabase) {
        private var bindings = 0
        private var aliasPages = 0
        private var guardPages = 0
        private var phrases = 0
        private var bytes = 0L
        // Scan-local deduplication only. The aggregate byte/phrase budget bounds this map and
        // the eventual duplicated per-event index entries; all are released after publishing.
        private val loaded = HashMap<Pair<String, Long>, Set<String>>()

        suspend fun bind(refs: List<ProviderReference>): Map<ProviderReference, String> {
            val out = HashMap<ProviderReference, String>()
            for ((namespace, values) in refs.groupBy { Triple(it.provider, it.product, it.kind) }) {
                for (page in values.chunked(PAGE)) {
                    currentCoroutineContext().ensureActive()
                    val byId = page.associateBy { it.externalId }
                    val args = arrayOf(namespace.first, namespace.second, namespace.third.stored) + byId.keys
                    db.rawQuery("SELECT external_id,uuid FROM binding WHERE provider=? AND product=? AND kind=? AND external_id IN (${marks(byId.size)})", args).use { cursor ->
                        while (cursor.moveToNext()) {
                            out[byId.getValue(cursor.getString(0))] = cursor.getString(1)
                            bytes += 256
                            checkBudget()
                        }
                    }
                    bindings++
                    yield()
                }
            }
            return out
        }

        fun identity(side: Side, sport: String, kind: ReferenceKind, day: Long, refs: Map<ProviderReference, String>): String? {
            val reference = side.reference ?: return exact(sport, kind, side.name, day)
            val productSport = if (reference.product == "nba") "basketball" else reference.product
            return if (reference.kind == kind && productSport == sport) refs[reference] else null
        }

        fun exact(sport: String, kind: ReferenceKind, name: String, day: Long): String? {
            val norm = MatchText.normalise(name)
            db.rawQuery("SELECT 1 FROM collision WHERE sport=? AND kind=? AND normalised=?", arrayOf(sport, kind.stored, norm)).use {
                if (it.moveToFirst()) return null
            }
            // Pin the exact spelling first, then PK identity lookups, rather than scanning a sport.
            db.rawQuery("SELECT DISTINCT a.uuid FROM alias a CROSS JOIN identity i ON i.uuid=a.uuid " +
                "WHERE a.normalised=? AND i.sport=? AND i.kind=? AND a.valid_from<=? AND a.valid_to>=? LIMIT 2",
                arrayOf(norm, sport, kind.stored, "$day", "$day")).use {
                if (!it.moveToFirst()) return null
                val id = it.getString(0)
                return if (it.moveToNext()) null else id
            }
        }

        suspend fun aliases(identity: String?, day: Long): Set<String> {
            if (identity == null) return emptySet()
            loaded[identity to day]?.let { return it }
            val out = LinkedHashSet<String>()
            var after = ""
            var from = FIRST_DAY
            var to = FIRST_DAY
            while (true) {
                currentCoroutineContext().ensureActive()
                var count = 0
                db.rawQuery("SELECT normalised,valid_from,valid_to FROM alias WHERE uuid=? AND (normalised,valid_from,valid_to)>(?,?,?) " +
                    "ORDER BY normalised,valid_from,valid_to LIMIT $PAGE", arrayOf(identity, after, "$from", "$to")).use { cursor ->
                    while (cursor.moveToNext()) {
                        count++
                        after = cursor.getString(0)
                        from = cursor.getLong(1)
                        to = cursor.getLong(2)
                        if (day in from..to && out.add(after)) {
                            bytes += 128 + after.length * 4L
                            checkBudget()
                        }
                    }
                }
                aliasPages++
                if (count < PAGE) break
                yield()
            }
            loaded[identity to day] = out
            return out
        }

        suspend fun guards(sport: String, kind: ReferenceKind, bases: Set<String>, day: Long): Set<String> {
            val out = LinkedHashSet<String>()
            for (page in bases.filter { it.isNotEmpty() }.chunked(PAGE)) {
                var base = ""
                var qualified = ""
                var from = FIRST_DAY
                var to = FIRST_DAY
                while (true) {
                    currentCoroutineContext().ensureActive()
                    var count = 0
                    val args = arrayOf(sport, kind.stored) + page + arrayOf(base, qualified, "$from", "$to")
                    db.rawQuery("SELECT base,qualified,valid_from,valid_to FROM guard WHERE sport=? AND kind=? AND base IN (${marks(page.size)}) " +
                        "AND (base,qualified,valid_from,valid_to)>(?,?,?,?) ORDER BY base,qualified,valid_from,valid_to LIMIT $PAGE", args).use { cursor ->
                        while (cursor.moveToNext()) {
                            count++
                            base = cursor.getString(0)
                            qualified = cursor.getString(1)
                            from = cursor.getLong(2)
                            to = cursor.getLong(3)
                            if (day in from..to && out.add(qualified)) {
                                bytes += 128 + qualified.length * 4L
                                checkBudget()
                            }
                        }
                    }
                    guardPages++
                    if (count < PAGE) break
                    yield()
                }
            }
            return out
        }

        suspend fun memberships(identity: String?, day: Long): Set<String> {
            if (identity == null) return emptySet()
            val out = LinkedHashSet<String>()
            var competition = ""
            var season = ""
            var from = FIRST_DAY
            var to = FIRST_DAY
            while (true) {
                currentCoroutineContext().ensureActive()
                var count = 0
                db.rawQuery("SELECT competition,season,valid_from,valid_to FROM membership WHERE participant=? " +
                    "AND (competition,season,valid_from,valid_to)>(?,?,?,?) ORDER BY competition,season,valid_from,valid_to LIMIT $PAGE",
                    arrayOf(identity, competition, season, "$from", "$to")).use { cursor ->
                    while (cursor.moveToNext()) {
                        count++
                        competition = cursor.getString(0)
                        season = cursor.getString(1)
                        from = cursor.getLong(2)
                        to = cursor.getLong(3)
                        // Open-ended membership cannot define a fixture's competition permanently.
                        if (from != FIRST_DAY && to != 2_932_896L && day in from..to && out.add(competition)) {
                            bytes += 256
                            checkBudget()
                        }
                    }
                }
                if (count < PAGE) break
                yield()
            }
            return out
        }

        fun account(names: Set<String>) {
            for (name in names) {
                val words = name.count { it == ' ' } + 1
                phrases += if (words > 1) 2 else 1
                bytes += 128 + name.length * 4L + words * 48L
                checkBudget()
            }
        }

        fun stats(): DictionaryLookupStats = DictionaryLookupStats(bindings, aliasPages, guardPages, phrases, bytes)

        private fun checkBudget() {
            if (bytes > MAX_ACCOUNTED_BYTES || phrases > MAX_PHRASES) throw BudgetExceeded()
        }

        private fun marks(count: Int): String = List(count) { "?" }.joinToString(",")
    }

    private companion object {
        const val PAGE = 256
        const val FIRST_DAY = -719_162L
        const val MAX_ACCOUNTED_BYTES = 2L * 1024 * 1024
        const val MAX_PHRASES = 16_384
    }
}
