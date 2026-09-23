package com.streammate.tv.lab

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.StreamMateApplication
import com.streammate.tv.core.database.PlaybackProgressEntity
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fills a fresh Lab install with a library the size of the owner's, through
 * the app's own importers, for performance captures of the release code.
 *
 * Opt-in and for a disposable emulator only. The library comes from
 * `.local/slowbox`: `make_fixture.py` writes it and `serve_fixture.py` serves
 * it to the emulator at 10.0.2.2. Run with `-e sohvaBenchmarkLibrary
 * http://10.0.2.2:8765` against the unshrunk Lab build; a shrunk one installed
 * over it with `adb install -r` keeps the data. Nothing is cleared.
 */
@RunWith(AndroidJUnit4::class)
class LabBenchmarkLibrarySeed {
    @Test fun seed(): Unit = runBlocking {
        val base = InstrumentationRegistry.getArguments().getString("sohvaBenchmarkLibrary")
        assumeTrue("Opt-in: -e sohvaBenchmarkLibrary <fixture server>", base != null)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StreamMateApplication
        check(app.packageName == "com.streammate.tv.lab")
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator only" }
        val container = app.container
        container.awaitReady()
        check(container.secretSettingsStore.loadSources().isEmpty()) { "Lab already has a source; seed a fresh install" }

        val source = IptvSourceConfiguration(
            id = SOURCE,
            name = "Benchmark provider",
            type = IptvSourceType.M3U,
            m3uUrl = "$base/playlist.m3u",
            xmlTvUrl = "$base/guide.xml",
        )
        container.secretSettingsStore.saveSources(listOf(source))
        container.guideRepository.upsertSourceState(source)
        val started = SystemClock.elapsedRealtime()
        val live = container.guideImportService.refreshPlaylist(source)
        val playlistMillis = SystemClock.elapsedRealtime() - started
        val guide = container.guideImportService.refreshEpg(source.id, source.xmlTvUrl!!)
        val guideMillis = SystemClock.elapsedRealtime() - started - playlistMillis
        val catalogue = container.m3uCatalogueImportService.refresh(source)
        val catalogueMillis = SystemClock.elapsedRealtime() - started - playlistMillis - guideMillis

        // What Home shows once a viewer has watched something: recent
        // channels with programmes on, and films part-way through.
        val database = StreamMateDatabase.create(app)
        try {
            val sql = database.openHelper.readableDatabase
            val channels = sql.query(
                "SELECT c.channelId FROM iptv_channels c JOIN import_state s ON s.sourceId = c.sourceId " +
                    "AND s.kind = 'playlist' AND c.snapshotId = s.activeSnapshotId " +
                    "WHERE c.sourceId = ? AND c.tvgId IS NOT NULL ORDER BY c.playlistOrder LIMIT 40",
                arrayOf(SOURCE),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            channels.take(30).forEach { container.preferencesRepository.setFavouriteChannel(it, true) }
            channels.take(12).reversed().forEach { container.preferencesRepository.recordRecentChannel(it) }
            val movies = sql.query(
                "SELECT movieId FROM vod_movies WHERE sourceId = ? ORDER BY movieId LIMIT 12",
                arrayOf(SOURCE),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            val now = System.currentTimeMillis()
            movies.forEachIndexed { index, movie ->
                database.catalogueDao().upsertProgress(
                    PlaybackProgressEntity(
                        contentKey = "vod:movie:$SOURCE:$movie", sourceId = SOURCE, contentType = "movie", itemId = movie,
                        positionMillis = 600_000L + index * 60_000L, durationMillis = 6_000_000L, completed = false,
                        lastWatchedEpochMillis = now - index * 3_600_000L,
                    ),
                )
            }
            Log.i(
                TAG,
                "seeded live=${live.channels} in ${playlistMillis}ms guide=${guide.programmes} in ${guideMillis}ms " +
                    "catalogue=$catalogue in ${catalogueMillis}ms favourites=${channels.take(30).size} " +
                    "recent=${channels.take(12).size} resume=${movies.size}",
            )
        } finally {
            database.close()
        }
    }

    private companion object {
        const val SOURCE = "benchmark"
        const val TAG = "SohvaBenchmarkSeed"
    }
}
