package com.sohva.tv.core.sync.metadata

import com.sohva.tv.core.data.database.MATCHED
import com.sohva.tv.core.data.database.MetadataMatchEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.metadata.TmdbGenres
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.vod.Genre
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.core.net.metadata.MetadataRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The title a match picker was opened on: its key, the page's lookup, and whether it is a film. */
data class ChoiceTarget(val contentKey: String, val request: MetadataRequest) {
    val film: Boolean get() = request.type == MediaType.MOVIE
}

/**
 * "Wrong details?" (spec 41 §4.14): the viewer's choice of record and its undo. A choice pins the
 * record (fetched by id from its own provider), then writes it into the library like a worker
 * match, for the title and every copy with the same film identity, and marks their queue rows
 * complete (META-FR-72, -75). Undo removes the pin, clears the override and puts the title back
 * at the head of the queue (the rebuild's fix of META-FR-77).
 */
class MatchChoices(
    private val db: SohvaDatabase,
    private val service: MetadataService,
    private val passes: LibraryPasses,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
    private val preferredCopy: suspend () -> PreferredCopy,
) {
    private val dao get() = db.metadata()

    suspend fun isPinned(target: ChoiceTarget): Boolean = service.isPinned(target.contentKey)

    /** Pins [record] for [target]; returns the pinned record as the page shows it. */
    suspend fun choose(target: ChoiceTarget, record: MetadataRecord): MetadataRecord = withContext(io) {
        val oldWork = if (target.film) dao.workKeyOf(target.contentKey) else null
        val full = service.pin(target.contentKey, oldWork, target.request, record)
        val now = clock.wallMillis()
        val keys = listOf(target.contentKey) + oldWork?.let(dao::keysOfWork).orEmpty().filter { it != target.contentKey }
        val type = if (target.film) MediaType.MOVIE else MediaType.SERIES
        val touched = HashSet<String>().apply { oldWork?.let(::add) }
        db.runInTransaction {
            for (key in keys) {
                val match = MetadataMatchEntity(
                    contentKey = key, mediaType = type.wire, status = MATCHED, provider = full.provider.id, externalId = full.externalId,
                    genre = TmdbGenres.primary(type, full.genreIds)?.wire, genresVersion = Genre.VERSION,
                    replacementTitle = full.title.trim().take(160).ifEmpty { null }, replacementPoster = full.poster,
                    // A chosen record's poster always stands in for the provider's (META-FR-75).
                    replaceProviderPoster = full.poster != null, updatedAt = now,
                )
                dao.putMatch(match)
                passes.apply(match)
                if (target.film) dao.workKeyOf(key)?.let(touched::add)
                dao.queueOf(listOf(key)).forEach { dao.putQueue(listOf(it.copy(state = Enrichment.COMPLETE, attempts = 0, nextAttemptAt = 0))) }
            }
            // The copies now share the chosen record's identity (`tmdb:<id>`): the pin follows it,
            // so every copy's lookup finds it (META-FR-76).
            val pin = dao.pin(target.contentKey)
            val newWork = if (target.film) dao.workKeyOf(target.contentKey) else null
            if (pin != null && newWork != null) dao.putPin(pin.copy(workKey = newWork))
        }
        afterWrite(touched)
        full
    }

    /** "Undo my choice": the pin and the override go, and the title is looked up again first. */
    suspend fun undo(target: ChoiceTarget): Unit = withContext(io) {
        val oldWork = if (target.film) dao.workKeyOf(target.contentKey) else null
        service.unpin(target.contentKey, oldWork, target.request)
        val touched = HashSet<String>().apply { oldWork?.let(::add) }
        db.runInTransaction {
            dao.deleteMatch(target.contentKey)
            val type = if (target.film) MediaType.MOVIE else MediaType.SERIES
            // A blank, unstored match resets the row to the provider's title, poster and name key.
            passes.apply(MetadataMatchEntity(target.contentKey, type.wire, "", null, null, null, Genre.VERSION, null, null, false, 0))
            if (target.film) dao.workKeyOf(target.contentKey)?.let(touched::add)
            dao.queueOf(listOf(target.contentKey)).forEach {
                dao.putQueue(listOf(it.copy(state = Enrichment.PENDING, attempts = 0, nextAttemptAt = 0, priority = 0)))
            }
        }
        afterWrite(touched)
    }

    private suspend fun afterWrite(workKeys: Set<String>) {
        if (workKeys.isNotEmpty()) passes.refreshCopies(workKeys.toList(), preferredCopy())
        passes.recountGenres()
    }
}
