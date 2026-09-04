package com.streammate.tv.iptv.metadata

import com.streammate.tv.core.database.CatalogueMetadataWorkEntity
import com.streammate.tv.core.model.CatalogueGenre
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueMetadataWorkQueueTest {
    @Test
    fun `synchronization settles current overrides and preserves an unchanged retry`() {
        val retryAt = 123_456L
        val candidates = listOf(
            candidate("complete", CatalogueGenre.VERSION),
            candidate("retry", 0),
            candidate("changed", 0, title = "New title"),
        )
        val existing = listOf(
            work("retry", CatalogueMetadataWorkEntity.STATE_RETRY, attempts = 3, retryAt = retryAt),
            work("changed", CatalogueMetadataWorkEntity.STATE_RETRY, title = "Old title"),
            work("obsolete", CatalogueMetadataWorkEntity.STATE_PENDING),
        )

        val rows = synchronizedCatalogueMetadataWork(
            candidates = candidates,
            existing = existing,
            targetGenresVersion = CatalogueGenre.VERSION,
            nowEpochMillis = 999,
        ).associateBy(CatalogueMetadataWorkEntity::contentKey)

        assertEquals(CatalogueMetadataWorkEntity.STATE_COMPLETE, rows.getValue("complete").state)
        assertEquals(CatalogueMetadataWorkEntity.STATE_RETRY, rows.getValue("retry").state)
        assertEquals(3, rows.getValue("retry").attemptCount)
        assertEquals(retryAt, rows.getValue("retry").nextAttemptAtEpochMillis)
        assertEquals(CatalogueMetadataWorkEntity.STATE_PENDING, rows.getValue("changed").state)
        assertEquals(false, "obsolete" in rows)
    }

    @Test
    fun `a settled no-match remains distinguishable from a successful match`() {
        val rows = synchronizedCatalogueMetadataWork(
            candidates = listOf(candidate("missing", CatalogueGenre.VERSION)),
            existing = listOf(work("missing", CatalogueMetadataWorkEntity.STATE_NO_MATCH)),
            targetGenresVersion = CatalogueGenre.VERSION,
            nowEpochMillis = 999,
        )

        assertEquals(CatalogueMetadataWorkEntity.STATE_NO_MATCH, rows.single().state)
    }

    @Test
    fun `retry delay grows exponentially and caps at one day`() {
        assertEquals(15L * 60 * 1_000, catalogueMetadataRetryDelayMillis(1))
        assertEquals(30L * 60 * 1_000, catalogueMetadataRetryDelayMillis(2))
        assertEquals(24L * 60 * 60 * 1_000, catalogueMetadataRetryDelayMillis(99))
    }

    private fun candidate(
        key: String,
        genresVersion: Int,
        title: String = "Title $key",
    ) = CatalogueMetadataCandidate(
        contentKey = key,
        mediaType = MetadataMediaType.MOVIE,
        title = title,
        year = 2020,
        providerPosterUrl = null,
        genresVersion = genresVersion,
    )

    private fun work(
        key: String,
        state: String,
        title: String = "Title $key",
        attempts: Int = 0,
        retryAt: Long = 0,
    ) = CatalogueMetadataWorkEntity(
        contentKey = key,
        mediaType = MetadataMediaType.MOVIE.wireValue,
        title = title,
        year = 2020,
        providerPosterUrl = null,
        targetGenresVersion = CatalogueGenre.VERSION,
        state = state,
        attemptCount = attempts,
        nextAttemptAtEpochMillis = retryAt,
        updatedAtEpochMillis = 1,
    )
}
