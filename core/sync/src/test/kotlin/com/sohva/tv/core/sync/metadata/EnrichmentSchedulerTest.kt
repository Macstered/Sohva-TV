package com.sohva.tv.core.sync.metadata

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SHELL-28, spec 41 META-FR-62: maintenance waits while the app is in front and starts 30 s after it leaves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnrichmentSchedulerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun start() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    private fun work(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(EnrichmentScheduler.NAME).get()

    @Test
    fun leavingQueuesARunThirtySecondsLaterAndReturningCancelsIt() {
        val scheduler = EnrichmentScheduler { WorkManager.getInstance(context) }
        scheduler.onLeave()
        val queued = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, queued.state)
        assertEquals(EnrichmentScheduler.LEAVE_DELAY_MS, queued.initialDelayMillis)
        scheduler.onReturn()
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun beforeAnythingWasScheduledReturningLeavesWorkManagerAlone() {
        EnrichmentScheduler { error("WorkManager must not be reached at start-up") }.onReturn()
    }
}
