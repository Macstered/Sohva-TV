package com.sohva.tv.core.sync.diff

import com.sohva.tv.core.data.database.ChannelEntity
import com.sohva.tv.core.data.database.EpisodeEntity
import com.sohva.tv.core.data.database.KeyRange
import com.sohva.tv.core.data.database.MovieEntity
import com.sohva.tv.core.data.database.SeriesEntity
import com.sohva.tv.core.data.database.SohvaDatabase

/** The keyed tables of one source, bound to their DAOs. */
internal object Tables {
    fun channels(db: SohvaDatabase, sourceId: String): KeyedTable<ChannelEntity> = db.channelImport().let { dao ->
        KeyedTable(KeyRange.channels(sourceId), dao::hashes, dao::insert, dao::update, dao::keysPage, dao::delete)
    }

    fun movies(db: SohvaDatabase, sourceId: String): KeyedTable<MovieEntity> = db.movieImport().let { dao ->
        KeyedTable(KeyRange.movies(sourceId), dao::hashes, dao::insert, dao::update, dao::keysPage, dao::delete)
    }

    fun series(db: SohvaDatabase, sourceId: String): KeyedTable<SeriesEntity> = db.seriesImport().let { dao ->
        KeyedTable(KeyRange.series(sourceId), dao::hashes, { rows -> dao.insert(rows) }, dao::update, dao::keysPage, dao::delete)
    }

    fun episodes(db: SohvaDatabase, sourceId: String): KeyedTable<EpisodeEntity> = db.episodeImport().let { dao ->
        KeyedTable(KeyRange.episodes(sourceId), dao::hashes, dao::insert, dao::update, dao::keysPage, dao::delete)
    }
}

/**
 * A 64-bit FNV-1a hash of the fields a row stores, each followed by U+001F so adjacent fields
 * cannot run together; null and empty differ. Equal hashes mean "nothing to write".
 */
internal class ContentHash {
    private var h = OFFSET

    fun add(value: String?): ContentHash {
        if (value == null) {
            mix(NULL_MARK)
        } else {
            for (c in value) mix(c.code)
        }
        mix(SEPARATOR)
        return this
    }

    fun add(value: Int?): ContentHash = add(value?.toString())

    fun add(value: Long?): ContentHash = add(value?.toString())

    fun value(): Long = h

    private fun mix(code: Int) {
        h = (h xor code.toLong()) * PRIME
    }

    private companion object {
        const val OFFSET = -0x340d631b7bdddcdbL
        const val PRIME = 0x100000001b3L
        const val SEPARATOR = 0x1F
        const val NULL_MARK = 0x10000
    }
}
