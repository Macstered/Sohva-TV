package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.streammate.tv.core.model.LibraryOrganization
import com.streammate.tv.core.model.LibraryRoom
import com.streammate.tv.core.model.LibrarySort
import com.streammate.tv.core.model.OrganizationKey
import com.streammate.tv.core.model.OrganizationRule
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "organization_rules", primaryKeys = ["room", "sourceId", "groupKey", "itemKey"], indices = [Index("itemKey")])
data class OrganizationRuleEntity(
    val room: String,
    val sourceId: String,
    val groupKey: String,
    val itemKey: String,
    val enabled: Boolean?,
    val sortMode: String?,
    val position: Long?,
) {
    fun toRule(): OrganizationRule = OrganizationRule(
        OrganizationKey(LibraryRoom.valueOf(room), sourceId, groupKey, itemKey), enabled, LibrarySort.parse(sortMode), position,
    )
}

fun OrganizationRule.toEntity() = OrganizationRuleEntity(key.room.name, key.sourceId, key.groupKey, key.itemKey, enabled, sort?.name, position)

/** Aliases are detached from import snapshots so preferred copies and refreshes cannot erase identity. */
@Entity(tableName = "organization_aliases", primaryKeys = ["alias"], indices = [Index("identity")])
data class OrganizationAliasEntity(val alias: String, val identity: String)

data class OrganizationSnapshot(
    val rules: List<OrganizationRuleEntity> = emptyList(),
    val aliases: List<OrganizationAliasEntity> = emptyList(),
)

data class OrganizationCatalogueRow(
    val sourceId: String,
    val sourceName: String,
    val itemId: String,
    val name: String,
    val categoryName: String?,
    val organizationGroupKey: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: String?,
    val externalId: String?,
    val sourceEnabled: Boolean = true,
)

data class OrganizationGroupRow(val room: String, val sourceId: String, val groupKey: String, val nameKey: String, val name: String?)

data class OrganizationRecord(
    val kind: Int, val room: String, val sourceId: String, val groupKey: String, val itemKey: String,
    val enabled: Boolean?, val sortMode: String?, val position: Long?, val alias: String, val identity: String,
)

/** Partial mutation distinguishes clearing a field from leaving another field alone. */
data class OrganizationChange(
    val key: OrganizationKey,
    val enabled: Boolean? = null,
    val changeEnabled: Boolean = false,
    val sort: LibrarySort? = null,
    val changeSort: Boolean = false,
    val position: Long? = null,
    val changePosition: Boolean = false,
)

/** Under SQLite's classic 999-variable limit, with room for the query's own parameters. */
internal const val ALIAS_QUERY_CHUNK = 900

/** Past this many aliases, one full read is cheaper than the chunked lookups. */
internal const val FULL_ALIAS_READ_THRESHOLD = 5_000

@Dao
abstract class OrganizationDao {
    // One query gives an atomic revision even when aliases and preferences merge together.
    @Query("SELECT 0 AS kind,room,sourceId,groupKey,itemKey,enabled,sortMode,position,'' AS alias,'' AS identity FROM organization_rules UNION ALL SELECT 1,'','','','',NULL,NULL,NULL,alias,identity FROM organization_aliases")
    abstract fun observeRecords(): Flow<List<OrganizationRecord>>

    @Query("""
        SELECT 'MOVIES' AS room, m.sourceId, m.organizationGroupKey AS groupKey, m.organizationNameKey AS nameKey, MIN(TRIM(m.categoryName)) AS name
        FROM vod_movies m JOIN import_state s ON s.sourceId=m.sourceId AND s.kind='catalogue' AND s.activeSnapshotId=m.snapshotId
        GROUP BY m.sourceId,m.organizationGroupKey,m.organizationNameKey
        UNION ALL
        SELECT 'SERIES',m.sourceId,m.organizationGroupKey,m.organizationNameKey,MIN(TRIM(m.categoryName))
        FROM vod_series m JOIN import_state s ON s.sourceId=m.sourceId AND s.kind='catalogue' AND s.activeSnapshotId=m.snapshotId
        GROUP BY m.sourceId,m.organizationGroupKey,m.organizationNameKey
    """)
    abstract fun observeGroups(): Flow<List<OrganizationGroupRow>>
    @Query("""
        SELECT m.sourceId, s.name AS sourceName, s.enabled AS sourceEnabled, m.movieId AS itemId, m.name,
            m.categoryName, m.organizationGroupKey, m.posterUrl, m.year, m.rating, NULL AS externalId
        FROM vod_movies m JOIN iptv_source_state s ON s.sourceId=m.sourceId
        WHERE m.sourceId=:sourceId AND m.snapshotId=:snapshotId
    """)
    abstract suspend fun importedMovies(sourceId: String, snapshotId: String): List<OrganizationCatalogueRow>

    @Query("SELECT * FROM organization_rules")
    abstract fun observeRules(): Flow<List<OrganizationRuleEntity>>

    @Query("SELECT * FROM organization_rules")
    abstract suspend fun rules(): List<OrganizationRuleEntity>

    @Query("SELECT * FROM organization_aliases")
    abstract fun observeAliases(): Flow<List<OrganizationAliasEntity>>

    @Query("SELECT * FROM organization_aliases")
    abstract suspend fun aliases(): List<OrganizationAliasEntity>

    @Query("SELECT * FROM organization_aliases WHERE alias IN (:aliases)")
    protected abstract suspend fun aliasesFor(aliases: List<String>): List<OrganizationAliasEntity>

    @Query("SELECT * FROM organization_aliases WHERE identity IN (:identities)")
    protected abstract suspend fun aliasesWithIdentity(identities: List<String>): List<OrganizationAliasEntity>

    /**
     * Re-emits on every alias write. Screens that need a list's identities
     * combine with this and look the identities up again, instead of holding
     * the whole table: one row per film copy, hundreds of thousands for a
     * large provider, which used to be read in full for every guide read.
     */
    @Query("SELECT COUNT(*) FROM organization_aliases")
    abstract fun observeAliasCount(): Flow<Long>

    /** The identities of [aliases] only, read in chunks the parameter limit allows. */
    open suspend fun identities(aliases: Collection<String>): Map<String, String> {
        if (aliases.isEmpty()) return emptyMap()
        if (aliases.size > FULL_ALIAS_READ_THRESHOLD) {
            val wanted = aliases.toHashSet()
            return aliases().filter { it.alias in wanted }.associate { it.alias to it.identity }
        }
        return aliases.distinct().chunked(ALIAS_QUERY_CHUNK)
            .flatMap { chunk -> aliasesFor(chunk) }
            .associate { it.alias to it.identity }
    }

    @Upsert
    abstract suspend fun upsertRules(rules: List<OrganizationRuleEntity>)

    @Upsert
    abstract suspend fun upsertAliases(aliases: List<OrganizationAliasEntity>)

    @Query("DELETE FROM organization_rules WHERE room = :room AND sourceId = :sourceId AND groupKey = :groupKey")
    abstract suspend fun resetGroup(room: String, sourceId: String, groupKey: String)

    @Query("DELETE FROM organization_rules")
    protected abstract suspend fun clearRules()

    @Query("DELETE FROM organization_aliases")
    protected abstract suspend fun clearAliases()

    @Transaction
    open suspend fun snapshot() = OrganizationSnapshot(rules(), aliases())

    @Transaction
    open suspend fun restore(snapshot: OrganizationSnapshot) {
        validateOrganizationSnapshot(snapshot)
        clearRules()
        clearAliases()
        upsertRules(snapshot.rules)
        upsertAliases(snapshot.aliases)
    }

    @Transaction
    open suspend fun change(changes: List<OrganizationChange>) {
        require(changes.size <= 100_000)
        val current = rules().associateBy { it.toRule().key }.toMutableMap()
        val updated = changes.map { change ->
            val old = current[change.key]?.toRule() ?: OrganizationRule(change.key)
            old.copy(
                enabled = if (change.changeEnabled) change.enabled else old.enabled,
                sort = if (change.changeSort) change.sort else old.sort,
                position = if (change.changePosition) change.position else old.position,
            ).toEntity().also { current[change.key] = it }
        }
        validateOrganizationSnapshot(OrganizationSnapshot(updated.distinctBy { it.toRule().key }))
        upsertRules(updated)
    }

    /**
     * Deterministic union of proven same-film aliases, retaining all
     * customization fields. Only the aliases of the given groups are read, plus
     * every alias of an identity a group merges away; an import registers a
     * batch at a time and used to read the whole table for each.
     */
    @Transaction
    open suspend fun registerFilmAliases(groups: List<List<String>>) {
        val known = identities(groups.flatten()).toMutableMap()
        val existingRules = rules().toMutableList()
        var rulesChanged = false
        val writes = mutableMapOf<String, OrganizationAliasEntity>()
        groups.forEach { group ->
            if (group.isEmpty()) return@forEach
            val identities = group.mapNotNull(known::get).distinct().sorted()
            val identity = identities.firstOrNull() ?: "film:${group.sorted().first()}"
            val merged = identities.filter { it != identity }.toSet()
            if (merged.isNotEmpty() || existingRules.any { it.room == "MOVIES" && it.itemKey in group && it.itemKey != identity }) {
                // Aliases already moved in this call keep their new identity;
                // the table still says the old one for them.
                merged.toList().chunked(ALIAS_QUERY_CHUNK).flatMap { aliasesWithIdentity(it) }
                    .forEach { known.putIfAbsent(it.alias, it.identity) }
                known.filterValues { it in merged }.keys.toList().forEach { alias ->
                    known[alias] = identity
                    writes[alias] = OrganizationAliasEntity(alias, identity)
                }
                val moved = existingRules.filter { it.room == "MOVIES" && (it.itemKey in merged || (it.itemKey in group && it.itemKey != identity)) }
                moved.forEach { old ->
                    val target = old.copy(itemKey = identity)
                    val winner = existingRules.firstOrNull { it.room == target.room && it.sourceId == target.sourceId && it.groupKey == target.groupKey && it.itemKey == identity }
                    existingRules.remove(old)
                    if (winner != null) existingRules.remove(winner)
                    existingRules.add(target.copy(
                        enabled = if (old.enabled == false || winner?.enabled == false) false else winner?.enabled ?: old.enabled,
                        sortMode = winner?.sortMode ?: old.sortMode,
                        position = listOfNotNull(winner?.position, old.position).minOrNull(),
                    ))
                    rulesChanged = true
                }
            }
            group.forEach { alias ->
                if (known[alias] != identity) {
                    known[alias] = identity
                    writes[alias] = OrganizationAliasEntity(alias, identity)
                }
            }
        }
        if (rulesChanged) { clearRules(); upsertRules(existingRules) }
        if (writes.isNotEmpty()) upsertAliases(writes.values.toList())
    }

    /**
     * The active catalogue snapshot of every source, as a cheap signal that the
     * film list changed; a batch written to an inactive snapshot does not fire it.
     */
    @Query("SELECT sourceId || ':' || COALESCE(activeSnapshotId, '') FROM import_state WHERE kind = 'catalogue' ORDER BY sourceId")
    abstract fun observeActiveCatalogueSnapshots(): Flow<List<String>>

    /** How many titles the metadata worker has matched to a record so far. */
    @Query("SELECT COUNT(*) FROM catalogue_metadata_overrides WHERE externalId IS NOT NULL")
    abstract fun observeMatchedMetadataCount(): Flow<Long>

    @Query("""
        SELECT movie.sourceId, source.name AS sourceName, source.enabled AS sourceEnabled, movie.movieId AS itemId,
            movie.name, movie.categoryName, movie.organizationGroupKey, movie.posterUrl,
            movie.year, movie.rating, metadata.externalId
        FROM vod_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        ORDER BY movie.name, movie.sourceId, movie.movieId
    """)
    abstract fun observeMovies(): Flow<List<OrganizationCatalogueRow>>

    @Query("""
        SELECT movie.sourceId, source.name AS sourceName, source.enabled AS sourceEnabled, movie.movieId AS itemId,
            movie.name, movie.categoryName, movie.organizationGroupKey, movie.posterUrl,
            movie.year, movie.rating, metadata.externalId
        FROM vod_movies movie
        INNER JOIN iptv_source_state source ON source.sourceId = movie.sourceId
        INNER JOIN import_state state ON state.sourceId = movie.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = movie.snapshotId
        LEFT JOIN catalogue_metadata_overrides metadata ON metadata.contentKey = 'vod:movie:' || movie.sourceId || ':' || movie.movieId
        ORDER BY movie.name, movie.sourceId, movie.movieId
    """)
    abstract suspend fun movies(): List<OrganizationCatalogueRow>

    @Query("""
        SELECT item.sourceId, source.name AS sourceName, source.enabled AS sourceEnabled, item.seriesId AS itemId,
            item.name, item.categoryName, item.organizationGroupKey, item.posterUrl,
            item.year, item.rating, NULL AS externalId
        FROM vod_series item
        INNER JOIN iptv_source_state source ON source.sourceId = item.sourceId
        INNER JOIN import_state state ON state.sourceId = item.sourceId
            AND state.kind = 'catalogue' AND state.activeSnapshotId = item.snapshotId
        ORDER BY item.name, item.sourceId, item.seriesId
    """)
    abstract fun observeSeries(): Flow<List<OrganizationCatalogueRow>>
}

fun validateOrganizationSnapshot(snapshot: OrganizationSnapshot) {
    require(snapshot.rules.size <= 300_000 && snapshot.aliases.size <= 500_000) { "Organization backup is too large" }
    require(snapshot.rules.all {
        LibraryRoom.entries.any { room -> room.name == it.room } &&
            (it.sortMode == null || LibrarySort.parse(it.sortMode) != null) &&
            (it.position == null || it.position >= 0) &&
            listOf(it.sourceId, it.groupKey, it.itemKey).all { key -> key.length <= 2048 }
    }) { "Invalid organization preference" }
    require(snapshot.rules.distinctBy { listOf(it.room, it.sourceId, it.groupKey, it.itemKey) }.size == snapshot.rules.size) { "Duplicate organization preference" }
    require(snapshot.aliases.all { it.alias.isNotBlank() && it.identity.isNotBlank() && it.alias.length <= 2048 && it.identity.length <= 2048 }) { "Invalid organization alias" }
    require(snapshot.aliases.distinctBy { it.alias }.size == snapshot.aliases.size) { "Duplicate organization alias" }
}
