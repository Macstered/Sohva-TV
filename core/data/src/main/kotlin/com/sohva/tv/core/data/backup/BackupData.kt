package com.sohva.tv.core.data.backup

import com.sohva.tv.core.data.database.ChannelCustomEntity
import com.sohva.tv.core.data.database.ChannelListEntity
import com.sohva.tv.core.data.database.ChannelListMemberEntity
import com.sohva.tv.core.data.database.FavouriteChannelEntity
import com.sohva.tv.core.data.database.LockedChannelEntity
import com.sohva.tv.core.data.database.OrganizationRuleEntity
import com.sohva.tv.core.data.database.ProfileAllowedGroupEntity
import com.sohva.tv.core.data.database.RecentChannelEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.org.LegacyCategories
import com.sohva.tv.core.model.channel.ChannelEdits
import com.sohva.tv.core.model.channel.ChannelPositions
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.profile.Profiles

/** What a restore changed in the database, for the passes that apply it afterwards. */
data class RestoredRows(
    /** Channels whose edits were or are now customised: their shown values are applied again. */
    val channelKeys: Set<String>,
    /** Rules before and after: the organisation is resolved again for them. */
    val ruleKeys: Set<RuleKey>,
    /** Phone logos to recreate from their bytes once the transaction has committed (BACKUP-06). */
    val logos: Map<String, ByteArray>,
    /** Addresses to keep or drop after the commit: `file:` ones only when this TV has the file. */
    val logoAddresses: Map<String, String>,
)

/**
 * The backup's database side (spec 71 §4.2, §4.5): the household's channel edits, lists,
 * organisation rules and each profile's channels. Blocking: callers run it on the database's
 * write dispatcher.
 */
class BackupData(private val db: SohvaDatabase) {
    private val dao get() = db.backup()

    /** Channel edits of [sources] with beta 23's dense order; [logo] gives a phone logo's bytes. */
    fun channels(sources: Set<String>, logo: (String) -> ByteArray?): List<BackupChannel> {
        val rows = dao.customs().filter { it.sourceId in sources }
        // Beta 23 keeps a dense index; the order of the sparse positions is what matters to it.
        val order = HashMap<String, Int>()
        for ((_, ofSource) in rows.filter { it.position != null }.groupBy { it.sourceId }) {
            ofSource.sortedBy { it.position }.forEachIndexed { i, row -> order[row.channelKey] = i }
        }
        return rows.map { r ->
            BackupChannel(
                channelId = r.channelKey, sourceId = r.sourceId, customName = r.customName, customGroupTitle = r.customGroupTitle,
                hidden = r.hidden, sortOrder = order[r.channelKey], manualXmltvChannelId = r.manualEpgId, updatedAt = r.updatedAt,
                customLogoUrl = r.customLogoUrl, channelNumber = r.customNumber, customLogoData = r.customLogoUrl?.let(logo),
            )
        }
    }

    fun lists(): List<BackupList> = dao.lists().map { BackupList(it.id, it.name, it.sortOrder, it.updatedAt) }

    fun members(): List<BackupMember> = dao.members().map { BackupMember(it.listId, it.channelKey, it.sortOrder) }

    /**
     * Every rule. A film rule names the film's work key (`work:<key>`); an alias that points the
     * key at itself lets beta 23 keep the rule on its own identity after its next identity pass.
     */
    fun organisation(): Pair<List<BackupRule>, List<BackupAlias>> {
        val rules = dao.rules().map { BackupRule(it.room, it.sourceId, it.groupKey, it.itemKey, it.enabled, it.sortMode, it.position?.coerceAtLeast(0)) }
        val aliases = rules.filter { it.room == OrgRoom.MOVIES.wire && it.itemKey.startsWith(OrgKeys.WORK_PREFIX) }
            .map { it.itemKey }.distinct().sorted().map { BackupAlias(it, it) }
        return rules to aliases
    }

    fun profileRows(profileId: String): ProfileKept = ProfileKept(
        favouriteChannelIds = dao.favourites(profileId),
        recentChannelIds = dao.recents(profileId),
        lockedChannelIds = dao.locks(profileId),
        allowedLive = dao.allowed(profileId, OrgRoom.LIVE.wire),
        allowedMovies = dao.allowed(profileId, OrgRoom.MOVIES.wire),
        allowedSeries = dao.allowed(profileId, OrgRoom.SERIES.wire),
    )

    /**
     * Replaces the household's rows with the backup's in one transaction (BACKUP-FR-18 step 6,
     * BACKUP-FR-21): channel edits, lists and members, organisation rules, and every profile's
     * favourites, recents, locks (only with a PIN) and allowed groups. [now] orders the recents.
     */
    fun replace(payload: BackupPayload, now: Long): RestoredRows {
        val logos = HashMap<String, ByteArray>()
        val addresses = HashMap<String, String>()
        var before: Set<String> = emptySet()
        var oldRules: List<OrganizationRuleEntity> = emptyList()
        val newRules = rules(payload)
        db.runInTransaction {
            before = dao.customs().mapTo(HashSet()) { it.channelKey }
            oldRules = dao.rules()
            listOf(dao::clearCustoms, dao::clearLists, dao::clearMembers, dao::clearRules, dao::clearFavourites, dao::clearRecents, dao::clearLocks, dao::clearAllowed)
                .forEach { it() }
            payload.channelPreferences.chunked(BATCH).forEach { page ->
                dao.putCustoms(
                    page.map { c ->
                        if (c.customLogoData != null) logos[c.channelId] = c.customLogoData else c.customLogoUrl?.let { addresses[c.channelId] = it }
                        ChannelCustomEntity(
                            channelKey = c.channelId, sourceId = c.sourceId, customName = c.customName, customGroupTitle = c.customGroupTitle,
                            customGroupKey = c.customGroupTitle?.takeIf { it.isNotBlank() }?.let(ChannelEdits::groupKey),
                            hidden = c.hidden, position = c.sortOrder?.let(ChannelPositions::initial), manualEpgId = c.manualXmltvChannelId,
                            // A logo's address is settled after the commit, when its file exists or is made.
                            customLogoUrl = null, customNumber = c.channelNumber, updatedAt = c.updatedAt,
                        )
                    },
                )
            }
            dao.putLists(payload.channelLists.map { ChannelListEntity(it.listId, it.name, it.sortOrder, it.updatedAt) })
            payload.channelListMembers.chunked(BATCH).forEach { page -> dao.putMembers(page.map { ChannelListMemberEntity(it.listId, it.channelId, it.sortOrder) }) }
            newRules.chunked(BATCH).forEach(dao::putRules)
            for ((profile, kept) in payload.profileData) {
                if (profile != Profiles.DEFAULT_ID && payload.preferences.profiles.none { it.id == profile }) continue
                dao.putFavourites(kept.favouriteChannelIds.mapIndexed { i, key -> FavouriteChannelEntity(profile, key, now - (kept.favouriteChannelIds.size - i)) })
                dao.putRecents(kept.recentChannelIds.take(RECENTS).mapIndexed { i, key -> RecentChannelEntity(profile, key, now - i * SECOND) })
                // Locks count only with a PIN, and a backup without one restores none (PROF-FR-42).
                if (payload.parentalPin != null) dao.putLocks(kept.lockedChannelIds.map { LockedChannelEntity(profile, it) })
                dao.putAllowed(
                    kept.allowedLive.map { ProfileAllowedGroupEntity(profile, OrgRoom.LIVE.wire, it) } +
                        kept.allowedMovies.map { ProfileAllowedGroupEntity(profile, OrgRoom.MOVIES.wire, it) } +
                        kept.allowedSeries.map { ProfileAllowedGroupEntity(profile, OrgRoom.SERIES.wire, it) },
                )
            }
        }
        val ruleKeys = (oldRules + newRules).mapNotNullTo(HashSet()) { r -> OrgRoom.of(r.room)?.let { RuleKey(it, r.sourceId, r.groupKey, r.itemKey) } }
        return RestoredRows(before + payload.channelPreferences.map { it.channelId }, ruleKeys, logos, addresses)
    }

    fun setLogo(channelKey: String, url: String?) = dao.setLogo(channelKey, url)

    /**
     * The backup's rules in the rebuild's keys (spec 71 §4.4): a film rule naming beta 23's
     * identity `film:X` takes the work key an alias gives X, else the copy X names (by its work key
     * when this TV has the film); beta 23's hidden categories become rules as its own migration made
     * them (§4.5 step 11). Duplicates after the mapping keep the first.
     */
    private fun rules(payload: BackupPayload): List<OrganizationRuleEntity> {
        val workOf = HashMap<String, String>()
        for (a in payload.aliases) if (a.alias.startsWith(OrgKeys.WORK_PREFIX)) workOf.putIfAbsent(a.identity, a.alias)
        fun film(itemKey: String): String {
            if (itemKey.isEmpty() || itemKey.startsWith(OrgKeys.WORK_PREFIX) || itemKey.startsWith("@")) return itemKey
            workOf[itemKey]?.let { return it }
            val copy = itemKey.removePrefix(FILM_PREFIX)
            return dao.workKeyOf(copy)?.let { OrgKeys.film(it) } ?: copy
        }
        val out = LinkedHashMap<List<String>, OrganizationRuleEntity>()
        for (r in payload.rules) {
            val item = if (r.room == OrgRoom.MOVIES.wire) film(r.itemKey) else r.itemKey
            out.putIfAbsent(listOf(r.room, r.sourceId, r.groupKey, item), OrganizationRuleEntity(r.room, r.sourceId, r.groupKey, item, r.enabled, r.sortMode, r.position))
        }
        val marker = LegacyCategories.MARKER
        if (out.keys.none { it == listOf(marker.room.wire, marker.sourceId, marker.groupKey, marker.itemKey) }) {
            val p = payload.preferences
            val hidden = listOf(OrgRoom.LIVE to p.hiddenLive, OrgRoom.MOVIES to p.hiddenMovies, OrgRoom.SERIES to p.hiddenSeries)
            for ((room, names) in hidden) {
                for (name in names.filter { it.isNotBlank() }) {
                    val key = listOf(room.wire, "", OrgKeys.nameKey(name), "")
                    out.putIfAbsent(key, OrganizationRuleEntity(room.wire, "", OrgKeys.nameKey(name), "", false, null, null))
                }
            }
            if (hidden.any { it.second.isNotEmpty() }) {
                out[listOf(marker.room.wire, "", marker.groupKey, "")] = OrganizationRuleEntity(marker.room.wire, "", marker.groupKey, "", true, null, null)
            }
        }
        return out.values.toList()
    }

    private companion object {
        const val BATCH = 500
        const val RECENTS = 20
        const val SECOND = 1_000L
        const val FILM_PREFIX = "film:"
    }
}
