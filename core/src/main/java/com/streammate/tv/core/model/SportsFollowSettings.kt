package com.streammate.tv.core.model

data class SportsCompetition(
    val sport: SportType,
    val id: String,
    val name: String,
    val country: String?,
    val type: String?,
    val logoUrl: String?,
) {
    val preferenceKey: String
        get() = preferenceKey(sport, id)

    companion object {
        fun preferenceKey(sport: SportType, id: String): String = "${sport.name}:$id"
    }
}

object SportsFollowDefaults {
    val sports: Set<SportType> = setOf(
        SportType.FOOTBALL,
        SportType.ICE_HOCKEY,
        SportType.AUSTRALIAN_FOOTBALL,
    )

    val competitionKeys: Set<String> = buildSet {
        setOf("2", "3", "39", "78", "135", "140", "848").forEach { id ->
            add(SportsCompetition.preferenceKey(SportType.FOOTBALL, id))
        }
        add(SportsCompetition.preferenceKey(SportType.ICE_HOCKEY, "16"))
        add(SportsCompetition.preferenceKey(SportType.AUSTRALIAN_FOOTBALL, "1"))
    }
}
