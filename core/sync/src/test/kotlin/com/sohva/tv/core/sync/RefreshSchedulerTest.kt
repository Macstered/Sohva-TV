package com.sohva.tv.core.sync

import android.content.Context
import android.content.ContextWrapper
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
    private inner class Host(var foreground: Boolean, var neverImported: Int) : ContextWrapper(context), RefreshHost {
        override fun getApplicationContext(): Context = this
        override val importRunner: ImportRunner get() = h.runner
        override fun appInForeground(): Boolean = foreground
        override suspend fun everyLiveSourceImportedOnce(): Boolean = neverImported == 0
        override suspend fun failuresSince(sinceMillis: Long, kinds: Set<RefreshKind>): Int =
            h.query("SELECT COUNT(*) FROM source_status WHERE status = 'failed' AND last_failure_at >= $sinceMillis").single().toInt()
        override fun nowMillis(): Long = h.clock.now
    }

    private fun worker(host: Host, kinds: Set<RefreshKind>, now: Boolean): RefreshWorker =
        TestListenableWorkerBuilder<RefreshWorker>(host).setInputData(RefreshWorker.input(kinds, null, now)).build()

    @Test
    fun theIntervalSetsThePlaylistAndGuidePeriodAndTheCatalogueStaysDaily() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = WorkManager.getInstance(context)
        work.enqueueUniqueWork("streammate-playlist-refresh", androidx.work.ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<RefreshWorker>().setInitialDelay(1, TimeUnit.DAYS).build())
        val scheduler = RefreshScheduler(work)
        scheduler.schedule(RefreshInterval.FOUR_HOURS)
        fun period(name: String): Long = work.getWorkInfosForUniqueWork(name).get().single().periodicityInfo!!.repeatIntervalMillis
        assertEquals(TimeUnit.HOURS.toMillis(4), period(RefreshScheduler.LIVE_WORK))
        assertEquals(TimeUnit.HOURS.toMillis(24), period(RefreshScheduler.CATALOGUE_WORK))
        scheduler.schedule(RefreshInterval.ONE_HOUR)
        assertEquals(TimeUnit.HOURS.toMillis(1), period(RefreshScheduler.LIVE_WORK))
        assertEquals(WorkInfo.State.CANCELLED, work.getWorkInfosForUniqueWork("streammate-playlist-refresh").get().single().state)
    }

    @Test
    fun anAutomaticRunInTheForegroundWaitsUnlessAFirstImportIsPending() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n")
        val host = Host(foreground = true, neverImported = 0)
        assertEquals(ListenableWorker.Result.retry(), worker(host, setOf(RefreshKind.PLAYLIST), now = false).doWork())
        assertTrue("nothing was fetched", h.requests.isEmpty())

        host.neverImported = 1
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = false).doWork())
        assertEquals(1, h.requests.size)

        host.neverImported = 0
        assertEquals("Sync now never waits", ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = true).doWork())
        assertEquals(2, h.requests.size)
    }

    @Test
    fun aFailedAutomaticRunRetriesAndAFailedSyncNowDoesNot() = runBlocking {
        h.addM3u()
        h.serve("/list.m3u", "", code = 500)
        val host = Host(foreground = false, neverImported = 1)
        assertEquals(ListenableWorker.Result.retry(), worker(host, setOf(RefreshKind.PLAYLIST), now = false).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(host, setOf(RefreshKind.PLAYLIST), now = true).doWork())
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
}
