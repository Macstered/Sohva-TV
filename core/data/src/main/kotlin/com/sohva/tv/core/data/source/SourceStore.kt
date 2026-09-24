package com.sohva.tv.core.data.source

import com.sohva.tv.core.data.database.SourceDao
import com.sohva.tv.core.data.database.SourceEntity
import com.sohva.tv.core.data.security.SecretValues
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.source.ImportScope
import com.sohva.tv.core.model.source.Source
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.source.SourceSecretsCodec
import com.sohva.tv.core.model.source.SourceType
import com.sohva.tv.core.model.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The viewer's sources (spec 10 §4.1, plan/04 §15.2): the non-secret fields in the `source` table,
 * the addresses and credentials encrypted in the secret store under `source:<id>`. The only place
 * that writes either; saves and removals are serialised (SRC-FR-106).
 *
 * A secret that cannot be read or decoded is reported, never treated as "no source" and never
 * overwritten by a later load (SRC-FR-109 rebuild rule).
 */
class SourceStore(
    private val sources: SourceDao,
    private val secrets: SecretValues,
    private val clock: Clock,
) {
    private val writes = Mutex()

    /** Sources by priority then name; a row written by a newer build with an unknown type is left out. */
    fun observe(): Flow<List<Source>> = sources.observeAll().map { rows -> rows.mapNotNull(::toSource) }

    suspend fun all(): List<Source> = sources.all().mapNotNull(::toSource)

    suspend fun source(id: String): Source? = sources.get(id)?.let(::toSource)

    /** The source with its secrets; `Ok(null)` when there is no such source. */
    suspend fun load(id: String): Outcome<SourceConfig?> {
        val source = source(id) ?: return Outcome.Ok(null)
        return when (val stored = secrets.read(secretKey(id))) {
            is Outcome.Failed -> stored
            is Outcome.Ok -> {
                val text = stored.value ?: return Outcome.Failed(AppError.SecretsUnreadable)
                val decoded = SourceSecretsCodec.decode(text) ?: return Outcome.Failed(AppError.SecretsUnreadable)
                Outcome.Ok(SourceConfig(source, decoded))
            }
        }
    }

    /** Validates and saves; the normalised config on success (SRC-FR-20…23, SRC-FR-07). */
    suspend fun save(config: SourceConfig): Outcome<SourceConfig> = writes.withLock {
        if (!SourceRules.isValidId(config.source.id)) return@withLock Outcome.Failed(AppError.Unknown)
        val valid = when (val result = SourceRules.validate(config)) {
            is SourceRules.Result.Invalid -> return@withLock Outcome.Failed(result.error)
            is SourceRules.Result.Valid -> result.config
        }
        val existing = sources.get(valid.source.id)
        if (existing == null && sources.count() >= SourceRules.MAX_SOURCES) {
            return@withLock Outcome.Failed(AppError.SourceLimitReached(SourceRules.MAX_SOURCES))
        }
        if (existing != null && existing.type != valid.source.type.name) return@withLock Outcome.Failed(AppError.Unknown)
        // Secret first: a row without its secret would look like a damaged store.
        val written = secrets.write(secretKey(valid.source.id), SourceSecretsCodec.encode(valid.secrets))
        if (written is Outcome.Failed) return@withLock written
        val now = clock.wallMillis()
        sources.upsert(toEntity(valid.source, createdAt = existing?.createdAt ?: now, updatedAt = now))
        Outcome.Ok(valid)
    }

    /**
     * Removes the configuration: the row, then the secret. The runner calls this last, after it
     * has stopped the source's imports and deleted its content (SRC-FR-41 rebuild rule).
     */
    suspend fun removeConfiguration(id: String): Outcome<Unit> = writes.withLock {
        sources.delete(id)
        secrets.write(secretKey(id), null)
    }

    private fun toSource(row: SourceEntity): Source? {
        val type = SourceType.entries.firstOrNull { it.name == row.type } ?: return null
        return Source(
            id = row.id,
            name = row.name,
            type = type,
            enabled = row.enabled,
            connectionLimit = row.connectionLimit,
            priority = row.priority,
            importScope = ImportScope.entries.firstOrNull { it.name == row.importScope } ?: ImportScope.BOTH,
            epgOffsetMinutes = row.epgOffsetMinutes,
        )
    }

    private fun toEntity(source: Source, createdAt: Long, updatedAt: Long) = SourceEntity(
        id = source.id,
        name = source.name,
        type = source.type.name,
        enabled = source.enabled,
        priority = source.priority,
        connectionLimit = source.connectionLimit,
        importScope = source.importScope.name,
        epgOffsetMinutes = source.epgOffsetMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun secretKey(sourceId: String): String = "source:$sourceId"
    }
}
