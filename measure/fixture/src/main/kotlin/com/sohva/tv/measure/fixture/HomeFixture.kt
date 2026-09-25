package com.sohva.tv.measure.fixture

import com.sohva.tv.core.data.database.SohvaDatabase

/**
 * Home's rows for the benchmarks (spec 02 §11 "Low-end performance"): twelve films of the film
 * fixture paused at different places, newest first, and six recently watched channels of the
 * guide fixture. Written every run (idempotent), so a benchmark that removes a card starts over.
 */
object HomeFixture {
    fun seed(db: SohvaDatabase, now: Long): Int {
        val sql = db.openHelper.writableDatabase
        db.runInTransaction {
            for (i in 0 until FILMS) {
                sql.execSQL(
                    "INSERT OR REPLACE INTO watch_progress (profile_id, content_key, source_id, content_type, work_key, series_key, " +
                        "position_ms, duration_ms, completed, updated_at) VALUES ('default', ?, ?, 'MOVIE', NULL, NULL, ?, ?, 0, ?)",
                    arrayOf<Any>("vod:movie:${FilmFixture.SOURCE}:${i * STRIDE}", FilmFixture.SOURCE, (10 + i) * MINUTE, 100 * MINUTE, now - i * MINUTE),
                )
            }
            for (i in 0 until CHANNELS) {
                sql.execSQL(
                    "INSERT OR REPLACE INTO recent_channel (profile_id, channel_key, watched_at) VALUES ('default', ?, ?)",
                    arrayOf<Any>("owner-fixture:$i", now - i * MINUTE),
                )
            }
        }
        return FILMS + CHANNELS
    }

    private const val FILMS = 12
    private const val CHANNELS = 6
    private const val STRIDE = 997
    private const val MINUTE = 60_000L
}
