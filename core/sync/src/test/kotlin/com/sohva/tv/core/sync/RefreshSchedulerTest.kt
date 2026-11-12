package com.sohva.tv.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.sohva.tv.core.model.concurrent.WorkOrigin
import com.sohva.tv.core.model.settings.RefreshInterval
import com.sohva.tv.core.model.source.RefreshKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefreshSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val h = SyncHarness()

    @After
    fun close() = h.close()

    /** The application as the worker sees it, with the facts under test's control. */
    private inner class Host(var foreground: Boolean, var neverImported: Int) : RefreshHost {
        override val importRunner: ImportRunner get() = h.runner
        override fun appInForeground(): Boolean = foreground
        override suspend fun everyLiveSourceImportedOnce(): Boolean = neverImported == 0
        override suspend fun failuresSince(sinceMillis: Long, kinds: Set<RefreshKind>): Int =
            h.query("SELECT COUNT(*) FROM source_status WHERE status = 'failed' AND last_failure_at >= $sinceMillis").single().toInt()
        override fun nowMillis(): Long = h.clock.now
        val deferred = ArrayList<Pair<Set<RefreshKind>, Long>>()
        override fun refreshDeferred(kinds: Set<RefreshKind>, freshMs: Long) {
            deferred += kinds to freshMs
        }
    }

    private fun worker(host: Host, kinds: Set<RefreshKind>, now: Boolean, freshMs: Long = 0): RefreshWorker =
        TestListenableWorkerBuilder<RefreshWorker>(context)
            .setWorkerFactory(RefreshWorkerFactory(host))
            .setInputData(RefreshWorker.input(kinds, null, now, freshMs))
            .build()

    @Test
    fun theIntervalSetsThePlaylistAndGuidePeriodAndTheCatalogueStaysDaily() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = WorkManager.getInstance(context)
        // Beta 23's jobs, tagged with its worker classes as WorkManager does (seen on a real upgrade).
        for ((name, tag) in listOf(
            "streammate-playlist-refresh" to "com.streammate.tv.app.GuideRefreshWorker",
            "sohva-sync-now-all" to "com.streammate.tv.app.GuideRefreshWorker",
            "streammate-catalogue-metadata-enrichment-v2" to "com.streammate.tv.app.CatalogueMetadataWorker",
        )) {
            work.enqueueUniqueWork(name, androidx.work.ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RefreshWorker>().addTag(tag).setInitialDelay(1, TimeUnit.DAYS).build())
        }
        val scheduler = RefreshScheduler(work)
        scheduler.schedule(RefreshInterval.FOUR_HOURS)
        fun period(name: String): Long = work.getWorkInfosForUniqueWork(name).get().single().periodicityInfo!!.repeatIntervalMillis
        assertEquals(TimeUnit.HOURS.toMillis(4), period(RefreshScheduler.LIVE_WORK))
        assertEquals(TimeUnit.HOURS.toMillis(24), period(RefreshScheduler.CATALOGUE_WORK))
        scheduler.schedule(RefreshInterval.ONE_HOUR)
        assertEquals(TimeUnit.HOURS.toMillis(1), period(RefreshScheduler.LIVE_WORK))
        for (name in listOf("streammate-playlist-refresh", "sohva-sync-now-all", "streammate-catalogue-metadata-enrichment-v2")) {
            assertEquals(name, WorkInfo.State.CANCELLED, work.getWorkInfosForUniqueWork(name).get().single().state)
        }
        scheduler.syncNow(null)
        assertEquals("the rebuild's sync-now is its own", 1, work.getWorkInfosForUniqueWork("sohva-refresh-now-all").get().size)
    }

    @Test
    fun anAutomaticRunInTheForegroundWaitsUnlessAFirstImportIsPending() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        val host = Host(foreground = true, neverImported = 0)
        // Not a retry: WorkManager's backoff grows with every attempt, up to five hours. The scheduler
        // is told instead and runs the refresh when the viewer leaves.
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = false, freshMs = 7).doWork())
        assertTrue("nothing was fetched", h.requests.isEmpty())
        assertEquals(listOf(setOf(RefreshKind.PLAYLIST) to 7L), host.deferred)

        host.neverImported = 1
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = false).doWork())
        assertEquals(1, h.requests.size)

        host.neverImported = 0
        assertEquals("Sync now never waits", ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = true).doWork())
        assertEquals(2, h.requests.size)
    }

    @Test
    fun aRefreshDeferredWhileTheViewerWatchedRunsWhenTheyLeave() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = WorkManager.getInstance(context)
        val scheduler = RefreshScheduler(work)
        scheduler.onLeave()
        assertTrue("nothing was deferred, nothing is queued", work.getWorkInfosForUniqueWork(RefreshScheduler.DEFERRED_WORK).get().isEmpty())
        scheduler.deferred(setOf(RefreshKind.PLAYLIST, RefreshKind.EPG), freshMs = 1_000)
        scheduler.deferred(setOf(RefreshKind.CATALOGUE), freshMs = 9_000)
        scheduler.onLeave()
        val queued = work.getWorkInfosForUniqueWork(RefreshScheduler.DEFERRED_WORK).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, queued.state)
        assertEquals(RefreshScheduler.LEAVE_DELAY_MS, queued.initialDelayMillis)
        // Handed over once: leaving again queues nothing new.
        work.cancelUniqueWork(RefreshScheduler.DEFERRED_WORK).result.get()
        scheduler.onLeave()
        assertTrue(work.getWorkInfosForUniqueWork(RefreshScheduler.DEFERRED_WORK).get().none { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun aFailedAutomaticRunRetriesAndAFailedSyncNowDoesNot() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "", code = 500)
        val host = Host(foreground = false, neverImported = 1)
        assertEquals(ListenableWorker.Result.retry(), worker(host, setOf(RefreshKind.PLAYLIST), now = false).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = true).doWork())
    }

    /**
     * Seen on the owner's Shield after the upgrade: the first scheduled run waited while the viewer was
     * in the app, then redid every source the first sync had just imported. A scheduled run now skips
     * what succeeded within half its period; the next cycle imports again.
     */
    @Test
    fun aScheduledRunSkipsWhatTheFirstSyncJustImported() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        val host = Host(foreground = false, neverImported = 0)
        val half = TimeUnit.HOURS.toMillis(2)
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = true).doWork())
        assertEquals(1, h.requests.size)
        h.clock.now += TimeUnit.MINUTES.toMillis(12)
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = false, freshMs = half).doWork())
        assertEquals("imported 12 minutes ago: skipped", 1, h.requests.size)
        h.clock.now += TimeUnit.HOURS.toMillis(4)
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = false, freshMs = half).doWork())
        assertEquals("the next cycle imports", 2, h.requests.size)
    }

    @Test
    fun noAutomaticImportStartsWhileVideoPlays() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        h.playing.value = true
        val run = async { h.runner.syncAll(setOf(RefreshKind.PLAYLIST), WorkOrigin.AUTOMATIC) }
        delay(500)
        assertTrue("waiting for playback to stop", h.requests.isEmpty())
        h.playing.value = false
        run.await()
        assertEquals(1, h.requests.size)
    }

    /** A viewer-started import keeps going during playback, but its closing statistics wait for the video to end. */
    @Test
    fun theStatisticsAfterAnImportWaitWhileVideoPlays() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        h.playing.value = true
        val sync = h.runner.sync("m3u-1", setOf(RefreshKind.PLAYLIST), WorkOrigin.VIEWER)
        kotlinx.coroutines.withTimeout(10_000) {
            while (h.query("SELECT status FROM source_status WHERE kind = 'playlist'").singleOrNull() != "success") delay(50)
        }
        delay(400)
        assertTrue("the import is in, the statistics wait", sync.isActive)
        h.playing.value = false
        kotlinx.coroutines.withTimeout(10_000) { sync.join() }
    }
}
