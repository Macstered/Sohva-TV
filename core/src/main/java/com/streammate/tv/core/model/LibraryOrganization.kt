package com.streammate.tv.core.model

import java.text.Collator
import java.util.Locale

enum class LibraryRoom { LIVE, MOVIES, SERIES }

enum class LibrarySort {
    PROVIDER, TITLE_ASC, TITLE_DESC, NEWEST, OLDEST, RATING, MANUAL;

    companion object {
        fun parse(value: String?): LibrarySort? = entries.firstOrNull { it.name == value }
    }
}

/** Empty source is an explicit combined-library scope, never an unknown source. */
data class OrganizationKey(
    val room: LibraryRoom,
    val sourceId: String = "",
    val groupKey: String = "",
    val itemKey: String = "",
)

data class OrganizationRule(
    val key: OrganizationKey,
    val enabled: Boolean? = null,
    val sort: LibrarySort? = null,
    val position: Long? = null,
)

data class OrganizationItem(
    val id: String,
    val sourceId: String,
    val title: String,
    val groupName: String?,
    val groupKey: String = organizationGroupKey(groupName),
    val imageUrl: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val providerOrder: Int = Int.MAX_VALUE,
    val identity: String = id,
    val legacyHidden: Boolean = false,
    val legacyPosition: Long? = null,
    val sourceEnabled: Boolean = true,
)

fun organizationGroupKey(name: String?, providerId: String? = null): String =
    providerId?.takeIf(String::isNotBlank)?.let { "id:$it" }
        ?: "name:${name.orEmpty().trim().lowercase(Locale.ROOT)}"

const val ORGANIZATION_GROUP_ORDER = "@groups"
const val ORGANIZATION_HISTORY = "@history"
const val ORGANIZATION_RECENT = "@recent"
const val ORGANIZATION_FAVOURITES = "@favourites"

/** Immutable read model shared by management and browsing; no UI or database work. */
class LibraryOrganization(rules: List<OrganizationRule> = emptyList()) {
    val rules: List<OrganizationRule> = rules.toList()
    private val byKey = rules.associateBy(OrganizationRule::key)

    fun rule(key: OrganizationKey): OrganizationRule? = byKey[key]

    fun defaultSort(room: LibraryRoom): LibrarySort =
        rule(OrganizationKey(room))?.sort ?: if (room == LibraryRoom.LIVE) LibrarySort.PROVIDER else LibrarySort.TITLE_ASC

    fun groupSort(room: LibraryRoom): LibrarySort =
        rule(OrganizationKey(room, groupKey = ORGANIZATION_GROUP_ORDER))?.sort ?: LibrarySort.PROVIDER

    fun shortcutEnabled(room: LibraryRoom, key: String): Boolean =
        rule(OrganizationKey(room, groupKey = key))?.enabled != false

    fun groupRule(room: LibraryRoom, item: OrganizationItem): OrganizationRule {
        val nameKey = organizationGroupKey(item.groupName)
        return firstFields(
            listOf(
                OrganizationKey(room, item.sourceId, item.groupKey),
                OrganizationKey(room, item.sourceId, nameKey),
                OrganizationKey(room, groupKey = item.groupKey),
                OrganizationKey(room, groupKey = nameKey),
            ).distinct(),
        )
    }

    fun memberRule(room: LibraryRoom, item: OrganizationItem, viewKey: String? = null): OrganizationRule {
        val groups = if (viewKey != null) listOf(viewKey) else listOf(item.groupKey, organizationGroupKey(item.groupName)).distinct()
        val keys = buildList {
            for (source in listOf(item.sourceId, "")) {
                for (group in groups) {
                    // A stable film identity takes precedence over its current representative copy.
                    for (id in listOf(item.identity, item.id).distinct()) add(OrganizationKey(room, source, group, id))
                }
            }
        }
        return firstFields(keys)
    }

    fun globallyEnabled(room: LibraryRoom, item: OrganizationItem): Boolean {
        val keys = listOf(item.sourceId, "").flatMap { source ->
            listOf(item.identity, item.id).distinct().map { OrganizationKey(room, source, itemKey = it) }
        }
        return firstFields(keys).enabled ?: !item.legacyHidden
    }

    fun eligible(room: LibraryRoom, item: OrganizationItem): Boolean =
        item.sourceEnabled && globallyEnabled(room, item) && groupRule(room, item).enabled != false && memberRule(room, item).enabled != false

    fun enabledInView(room: LibraryRoom, item: OrganizationItem, viewKey: String? = null): Boolean =
        eligible(room, item) && (viewKey == null || memberRule(room, item, viewKey).enabled != false)

    fun itemSort(room: LibraryRoom, item: OrganizationItem, viewKey: String? = null): LibrarySort =
        (if (viewKey == null) groupRule(room, item).sort else rule(OrganizationKey(room, groupKey = viewKey))?.sort)
            ?: rule(OrganizationKey(room))?.sort
            ?: if (item.legacyPosition != null && room == LibraryRoom.LIVE) LibrarySort.MANUAL else defaultSort(room)

    /** Input order is the stable provider/default order, independent of manual item ranks. */
    fun orderedGroups(room: LibraryRoom, groups: List<Pair<String, List<OrganizationItem>>>): List<String> {
        val collator = titleCollator()
        val mode = groupSort(room)
        val comparator = Comparator<Pair<String, List<OrganizationItem>>> { a, b ->
            val compared = when (mode) {
                LibrarySort.TITLE_ASC -> collator.compare(a.first, b.first)
                LibrarySort.TITLE_DESC -> collator.compare(b.first, a.first)
                LibrarySort.MANUAL -> groupPosition(room, a.second).compareTo(groupPosition(room, b.second))
                else -> 0
            }
            compared
        }
        return groups.sortedWith(comparator).map { it.first }
    }

    fun orderedItems(
        room: LibraryRoom,
        items: List<OrganizationItem>,
        viewKey: String? = null,
        includeDisabled: Boolean = false,
        chronological: Boolean = false,
    ): List<OrganizationItem> {
        val available = if (includeDisabled) items else items.filter { enabledInView(room, it, viewKey) }
        if (chronological || available.isEmpty()) return available
        val collator = titleCollator()
        if (viewKey != null) return available.sortedWith(itemComparator(room, available.first(), viewKey, collator))
        val groups = available.groupBy { it.groupName.orEmpty() }
        val names = orderedGroups(room, groups.entries.map { it.key to it.value })
        return names.flatMap { name ->
            // Source-specific overrides can coexist within a combined group. Each source's
            // own order stays contiguous; combined actions write the same mode to every source.
            groups.getValue(name).let { members ->
                val legacyManual = room == LibraryRoom.LIVE && members.any { it.legacyPosition != null }
                members.groupBy { if (legacyManual && groupRule(room, it).sort == null && rule(OrganizationKey(room))?.sort == null) LibrarySort.MANUAL else itemSort(room, it) }
            }.values.flatMap { sameMode ->
                sameMode.sortedWith(itemComparator(room, sameMode.firstOrNull { it.legacyPosition != null } ?: sameMode.first(), null, collator))
            }
        }
    }

    private fun itemComparator(room: LibraryRoom, sample: OrganizationItem, viewKey: String?, collator: Collator): Comparator<OrganizationItem> {
        val mode = itemSort(room, sample, viewKey)
        val ranks = mutableMapOf<String, Long>()
        fun rank(item: OrganizationItem): Long = ranks.getOrPut(item.id) { memberRule(room, item, viewKey).position ?: item.legacyPosition ?: Long.MAX_VALUE }
        return Comparator { a, b ->
            val compared = when (mode) {
                LibrarySort.PROVIDER -> a.providerOrder.compareTo(b.providerOrder)
                LibrarySort.TITLE_ASC -> collator.compare(a.title, b.title)
                LibrarySort.TITLE_DESC -> collator.compare(b.title, a.title)
                LibrarySort.NEWEST -> nullableNumber(b.year?.toDouble(), a.year?.toDouble(), a.year == null, b.year == null)
                LibrarySort.OLDEST -> nullableNumber(a.year?.toDouble(), b.year?.toDouble(), a.year == null, b.year == null)
                LibrarySort.RATING -> nullableNumber(rating(b.rating), rating(a.rating), rating(a.rating) == null, rating(b.rating) == null)
                LibrarySort.MANUAL -> rank(a).compareTo(rank(b))
            }
            if (compared != 0) compared else if (mode == LibrarySort.PROVIDER || mode == LibrarySort.MANUAL) {
                a.providerOrder.compareTo(b.providerOrder).takeIf { it != 0 }
                    ?: collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.identity.compareTo(b.identity)
            } else collator.compare(a.title, b.title).takeIf { it != 0 } ?: a.identity.compareTo(b.identity)
        }
    }

    private fun groupPosition(room: LibraryRoom, items: List<OrganizationItem>): Long =
        items.minOfOrNull { groupRule(room, it).position ?: Long.MAX_VALUE } ?: Long.MAX_VALUE

    private fun firstFields(keys: List<OrganizationKey>): OrganizationRule {
        val matches = keys.mapNotNull(byKey::get)
        return OrganizationRule(
            key = keys.first(),
            enabled = matches.firstNotNullOfOrNull { it.enabled },
            sort = matches.firstNotNullOfOrNull { it.sort },
            position = matches.firstNotNullOfOrNull { it.position },
        )
    }

    private fun nullableNumber(a: Double?, b: Double?, aMissing: Boolean, bMissing: Boolean): Int =
        if (aMissing != bMissing) aMissing.compareTo(bMissing) else (a ?: 0.0).compareTo(b ?: 0.0)

    private fun rating(value: String?): Double? = value?.trim()?.let { RATING.find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }

    private fun titleCollator(): Collator = Collator.getInstance(Locale.forLanguageTag("fi")).apply { strength = Collator.PRIMARY }

    companion object { private val RATING = Regex("^\\d+(?:[.,]\\d+)?") }
}

/** Moves only a known item. Newly imported members outside this list are never deleted. */
fun movedOrganizationIds(ids: List<String>, id: String, destination: Int): List<String> {
    val current = ids.indexOf(id)
    if (current < 0 || ids.size < 2) return ids
    return ids.toMutableList().apply { add(destination.coerceIn(0, lastIndex), removeAt(current)) }
}
