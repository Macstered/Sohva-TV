package com.streammate.tv.feature.home

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.*
import com.sohva.tv.addons.storage.*
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.database.PlaybackProgressEntity
import com.streammate.tv.iptv.repository.CatalogueRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeResumeDatabaseTest {
    @Test fun largeCachedLibraryAndDiscoverProgressReachOneRetainedRow() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.streammate.tv.debug")
        val database = Room.inMemoryDatabaseBuilder(context, StreamMateDatabase::class.java).build()
        val addonDb = Room.inMemoryDatabaseBuilder(context, AddonProgressDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("INSERT INTO iptv_source_state(sourceId,name,type,enabled,connectionLimit,priority,updatedAtEpochMillis,epgOffsetMinutes) VALUES('fixture','Synthetic','xtream',1,1,0,1,0)")
                db.execSQL("INSERT INTO import_state VALUES('fixture','catalogue','current',1,44000)")
                db.execSQL(numbers(40000) + "INSERT INTO vod_movies(sourceId,snapshotId,movieId,name,normalizedName,categoryName,categoryKey,organizationGroupKey,organizationNameKey,encryptedStreamUrl) SELECT 'fixture','current',x,'Movie '||x,'movie '||x,'Films','films','films','films','synthetic' FROM n")
                db.execSQL(numbers(4000) + "INSERT INTO vod_series(sourceId,snapshotId,seriesId,name,normalizedName,categoryName,categoryKey,organizationGroupKey,organizationNameKey) SELECT 'fixture','current',x,'Series '||x,'series '||x,'Series','series','series','series' FROM n")
                db.execSQL(numbers(80000) + "INSERT INTO vod_episodes(sourceId,seriesId,episodeId,name,seasonNumber,episodeNumber,encryptedStreamUrl,durationSeconds) SELECT 'fixture',x/20,'e'||x,'Episode '||x,1,1+x%20,'synthetic',3600 FROM n")
                db.execSQL("INSERT INTO catalogue_metadata_overrides(contentKey,replaceProviderPoster,replacementTitle,externalId,updatedAtEpochMillis) SELECT 'vod:movie:fixture:'||movieId,0,name,100000+movieId,1 FROM vod_movies")
                db.execSQL("INSERT INTO catalogue_metadata_overrides(contentKey,replaceProviderPoster,replacementTitle,externalId,updatedAtEpochMillis) SELECT 'series:fixture:'||seriesId,0,name,200000+seriesId,1 FROM vod_series")
                db.execSQL("INSERT INTO metadata_cache(lookupKey,provider,status,externalId,mediaType,attributionName,attributionUrl,confidence,cachedAtEpochMillis,expiresAtEpochMillis) SELECT 'series-'||seriesId,'tmdb','positive',200000+seriesId,'series','synthetic','https://example.invalid',1,1,9999999999999 FROM vod_series")
                db.execSQL(numbers(1000) + "INSERT INTO trakt_state SELECT 'fixture','movie:'||x,'movie',100000+x,NULL,NULL,NULL,CASE WHEN x<10 THEN 35 ELSE 0 END,1,1,10000+x FROM n")
                db.execSQL(numbers(100) + "INSERT INTO trakt_state SELECT 'fixture','episode:'||x,'episode',200000+x,NULL,1,1,CASE WHEN x<10 THEN 45 ELSE 0 END,1,1,20000+x FROM n")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            db.execSQL("UPDATE trakt_state SET imdb='tt1234567', updatedAtMillis=40000 WHERE kind='movie' AND tmdb=100009")
            db.execSQL("ANALYZE")
            val cipher = object : AddonSecretCipher {
                override fun encrypt(plaintext: String) = plaintext
                override fun decrypt(ciphertext: String) = ciphertext
            }
            val progress = AddonProgressRepository(RoomAddonProgressPersistence(addonDb.progress()), cipher, AddonManagementAccess { it == "fixture" })
            repeat(4) { i ->
                val session = progress.begin("fixture", identity(i), "Discover $i")
                progress.save(session, 1, 1000, 5000)
            }
            val profile = MutableStateFlow("fixture")
            val catalogue = CatalogueRepository(database.catalogueDao(), trakt = database.traktStateDao(), activeProfile = profile)
            val subscriptions = AtomicInteger()
            val started = SystemClock.elapsedRealtime()
            val store = HomeResumeStore(scope, flowOf(HomeResumeProfile("fixture", true)),
                { subscriptions.incrementAndGet(); catalogue.observeContinueWatching(it) }, progress::observeRecent)
            val initial = withTimeout(10_000) { store.awaitInitial("fixture") }
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i("HomePerf", "Synthetic 40000 movies / 4000 series / 80000 episodes: complete cached resume read $elapsed ms")
            assertEquals(HomeResumeStatus.READY, initial.status)
            assertEquals(12, initial.entries.size)
            assertTrue(initial.entries.any { it is HomeResumeEntry.Vod })
            assertTrue(initial.entries.any { it is HomeResumeEntry.Discover })
            assertTrue("Complete cached read took $elapsed ms", elapsed < 1000)
            // Simulate two providers adding the already-watched Discover film overnight.
            database.withTransaction {
                repeat(2) { copy ->
                    val provider = "new-provider-$copy"
                    db.execSQL("INSERT INTO iptv_source_state(sourceId,name,type,enabled,connectionLimit,priority,updatedAtEpochMillis,epgOffsetMinutes) VALUES('$provider','Synthetic','xtream',1,1,0,1,0)")
                    db.execSQL("INSERT INTO import_state VALUES('$provider','catalogue','current',1,1)")
                    db.execSQL("INSERT INTO vod_movies(sourceId,snapshotId,movieId,name,normalizedName,categoryName,categoryKey,organizationGroupKey,organizationNameKey,encryptedStreamUrl) VALUES('$provider','current','copy','Overnight movie','overnight movie','Films','films','films','films','synthetic')")
                    db.execSQL("INSERT INTO catalogue_metadata_overrides(contentKey,replaceProviderPoster,replacementTitle,externalId,updatedAtEpochMillis) VALUES('vod:movie:$provider:copy',0,'Overnight movie','100009',1)")
                }
            }
            val copies = withTimeout(5000) { database.traktStateDao().observeVodContinueWatching("fixture").first() }
            assertEquals(1, copies.count { it.tmdbId == 100009L })
            val afterImport = withTimeout(5000) { store.state.first { it.settled } }
            assertEquals(1, afterImport.entries.count { it is HomeResumeEntry.Discover && it.progress.identity.media.id == "tt1234567" })
            assertEquals(0, afterImport.entries.count { it is HomeResumeEntry.Vod && it.item.tmdbId == 100009L })
            val session = progress.begin("fixture", identity(9), "Updated Discover")
            progress.save(session, 1, 2000, 5000)
            withTimeout(5000) { store.state.first { it.entries.firstOrNull()?.title == "Updated Discover" } }
            // Returning Home reads this up-to-date value; the source subscription is retained.
            assertEquals("Updated Discover", store.state.value.entries.first().title)
            assertEquals(1, subscriptions.get())
            // Reuse the populated library to exercise scoped details and cross-copy precedence.
            val movieKey = "vod:movie:fixture:0"
            val movieProgress = catalogue.observeMovieProgress(movieKey)
            val remoteRewatch = withTimeout(5000) { movieProgress.first() }!!
            assertFalse(remoteRewatch.completed)
            assertEquals(0.35f, remoteRewatch.fraction, 0.001f)
            val copy = PlaybackProgressEntity("vod:movie:other:copy", "other", "movie", "copy",
                700, 1000, false, 50000, "tmdb:100000", "fixture")
            database.catalogueDao().upsertProgress(copy)
            assertEquals(700L, withTimeout(5000) { movieProgress.first() }!!.positionMillis)
            database.catalogueDao().upsertProgress(copy.copy(lastWatchedEpochMillis = 5000))
            assertEquals(0.35f, withTimeout(5000) { movieProgress.first() }!!.fraction, 0.001f)
            assertEquals(350L, withTimeout(5000) { movieProgress.first() }!!.resumePositionMillis)
            assertTrue(withTimeout(5000) { catalogue.observeMovieProgress("vod:movie:fixture:50").first() }!!.completed)
            val episodes = withTimeout(5000) { catalogue.observeSeriesProgress("fixture", "1").first() }
            assertEquals(setOf("vod:episode:fixture:e20"), episodes.keys)
            assertEquals(0.45f, episodes.values.single().fraction, 0.001f)
            val retainedMovie = movieProgress.stateIn(scope, SharingStarted.Eagerly, null)
            withTimeout(5000) { retainedMovie.first { it != null } }
            profile.value = "other-viewer"
            withTimeout(5000) { retainedMovie.first { it == null } }
            assertTrue(withTimeout(5000) { catalogue.observeSeriesProgress("fixture", "1").first() }.isEmpty())
            val traktDao = database.traktStateDao()
            val paused = traktDao.observe("fixture").first().first { it.key == "movie:9" }
            traktDao.replace("other-viewer", listOf(paused.copy(profileId = "other-viewer")))
            traktDao.replacePlayback("fixture", listOf(paused.copy(progress = 65.0, watched = false, plays = 0)))
            val refreshed = traktDao.observe("fixture").first()
            assertEquals(1100, refreshed.size)
            assertEquals(65.0, refreshed.first { it.key == paused.key }.progress, 0.0)
            assertTrue(refreshed.first { it.key == paused.key }.watched)
            assertEquals(1, refreshed.first { it.key == paused.key }.plays)
            assertEquals(0.0, refreshed.first { it.key == "movie:0" }.progress, 0.0)
            assertEquals(paused.progress, traktDao.observe("other-viewer").first().single().progress, 0.0)
        } finally {
            scope.cancel()
            database.close()
            addonDb.close()
        }
    }

    private fun numbers(count: Int) = "WITH RECURSIVE n(x) AS (VALUES(0) UNION ALL SELECT x+1 FROM n WHERE x<${count - 1}) "
    private fun identity(id: Int): AddonWatchIdentity {
        val key = if (id == 0) "tt1234567" else "title-$id"
        return AddonWatchIdentity("synthetic", AddonMediaKey("movie", key), AddonMediaKey("movie", key))
    }
}
