package com.sohva.tv.core.model.vod

/** The list's rules (ORG-FR-60, -64): at most [MAX] groups, names cut to [NAME_MAX]. */
object CustomGroups {
    const val MAX: Int = CustomGroup.MAX
    const val NAME_MAX: Int = 40

    /**
     * [group] saved into [groups]: the name trimmed and cut; an unusable group changes nothing; a
     * group with the same id is replaced, else a new one is appended while there is room.
     */
    fun save(groups: List<CustomGroup>, group: CustomGroup): List<CustomGroup> {
        val clean = group.copy(name = group.name.trim().take(NAME_MAX))
        if (!clean.isUsable) return groups
        val at = groups.indexOfFirst { it.id == clean.id }
        return when {
            at >= 0 -> groups.toMutableList().apply { set(at, clean) }
            groups.size >= MAX -> groups
            else -> groups + clean
        }
    }

    fun delete(groups: List<CustomGroup>, id: String): List<CustomGroup> = groups.filterNot { it.id == id }

    /** A typed year: digits only, at most four. */
    fun year(text: String): Int? = text.filter(Char::isDigit).take(4).toIntOrNull()

    /** A typed rating: at most four characters, a comma as the decimal point; unreadable = none. */
    fun rating(text: String): Double? = text.take(4).replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
}
