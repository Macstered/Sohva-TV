package com.sohva.tv.core.model.sport.pairing

import com.sohva.tv.core.model.sport.SportType

/**
 * Club identities (SPORT-FR-106, -107), in either provider's spelling. Membership must be exact:
 * city names, youth/women qualifiers and shared nicknames cannot be inferred from part of a name.
 */
object TeamVariants {
    /** Stored canonical → alias rows become direct, bidirectional groups, without transitive guesses. */
    fun aliases(extra: Map<String, Set<String>>): Map<String, Set<String>> = groups(
        extra.map { (canonical, names) -> listOf(canonical) + names },
    )

    fun of(team: String, aliases: Map<String, Set<String>>, sport: SportType = SportType.FOOTBALL): List<String> {
        val key = MatchText.normalise(team)
        val builtIn = when (sport) {
            SportType.FOOTBALL -> football
            SportType.ICE_HOCKEY -> hockey
            SportType.BASKETBALL, SportType.NBA -> basketball
            SportType.AMERICAN_FOOTBALL -> americanFootball
            else -> emptyMap()
        }
        val names = linkedSetOf(key)
        names += builtIn[key].orEmpty()
        // A saved extension of a known club also applies when the API uses its short name.
        for (member in names.toList()) names += aliases[member].orEmpty()
        return names.filter { it.length >= MIN_LENGTH }
    }

    private fun groups(rows: List<List<String>>): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        for (row in rows) {
            // Identity keys may be short; only the variants indexed into provider text need 3 chars.
            val names = row.map(MatchText::normalise).filter { it.isNotEmpty() }.distinct()
            for (key in names) out.getOrPut(key) { LinkedHashSet() }.addAll(names.filter { it != key })
        }
        return out
    }

    // Finite club data, built once on the bulk thread when that sport is first matched. No catalogue
    // entries are cached here. Adding an identity group requires fixtures and a derived-cache bump.
    private val football: Map<String, Set<String>> by lazy {
        groups(listOf(
            listOf("Manchester United", "Man Utd", "Man United", "Manchester Utd", "ManU", "Man U"),
            listOf("Manchester City", "Man City"),
            listOf("Tottenham Hotspur", "Tottenham", "Spurs"),
            listOf("Paris Saint-Germain", "PSG", "Paris SG"),
            listOf("Inter", "Inter Milan", "Internazionale"),
            listOf("Bayern München", "Bayern Munich", "Bayern"),
        ))
    }

    private val hockey: Map<String, Set<String>> by lazy {
        groups(listOf(
            listOf("TPS Turku", "TPS", "Turun Palloseura"),
            listOf("IFK Helsinki", "Helsinki IFK", "HIFK", "Helsingin IFK"),
            listOf("Hameenlinna", "HPK", "Hämeenlinnan Pallokerho"),
            listOf("Vaasan Sport", "Sport", "Vaasa Sport"),
            listOf("Kiekko-Espoo", "K-Espoo"),
            listOf("New York Rangers", "NY Rangers", "Rangers"),
            listOf("New Jersey Devils", "NJ Devils", "Devils"),
            listOf("Los Angeles Kings", "LA Kings", "Kings"),
            listOf("Toronto Maple Leafs", "Maple Leafs"),
        ))
    }

    private val basketball: Map<String, Set<String>> by lazy {
        groups(listOf(
            listOf("Los Angeles Lakers", "LA Lakers", "Lakers"),
            listOf("Los Angeles Clippers", "LA Clippers", "Clippers"),
            listOf("New York Knicks", "NY Knicks", "Knicks"),
            listOf("San Antonio Spurs", "Spurs"),
        ))
    }

    private val americanFootball: Map<String, Set<String>> by lazy {
        groups(listOf(
            listOf("New England Patriots", "Patriots"),
            listOf("New York Jets", "NY Jets", "Jets"),
            listOf("New York Giants", "NY Giants", "Giants"),
            listOf("Philadelphia Eagles", "Eagles"),
        ))
    }

    private const val MIN_LENGTH = 3
}
