package com.sohva.tv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.guide.GuideWindow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the player's info box, its channel list and Home's "on now" row read must not wait for a
 * long database write (a guide, playlist or metadata import): programme titles went stale on the
 * owner's Shield while the guide, which reads without registering a new observer, stayed current.
 */
@RunWith(AndroidJUnit4::class)
class ReadsDuringImportTest {
    @get:Rule val clear = ClearStateRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    /** Runs [block] while another thread holds the database's write transaction open, as an import does. */
    private fun <T> duringAWrite(block: suspend () -> T): T {
        val db = graph.data.database
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = thread(name = "held-write") {
            db.runInTransaction {
                held.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }
        assertTrue(held.await(10, TimeUnit.SECONDS))
        try {
            return runBlocking { block() }
        } finally {
            release.countDown()
            writer.join(10_000)
        }
    }

    @Test
    fun nowAndNextReadsDoNotWaitForAWrite() {
        GuideFixture.seed(graph, groups = 1, perGroup = 4)
        val reads = graph.data.live
        val sourceId = runBlocking { reads.sources.first().first().id }
        val now = System.currentTimeMillis()
        val result = duringAWrite {
            withTimeoutOrNull(5_000) {
                val source = reads.source(sourceId)
                assertNotNull(source)
                reads.schedules(source!!, listOf("fixture-0-epg-0"), GuideWindow.anchor(now))
                graph.data.home.recentChannels(now)
                true
            }
        }
        assertNotNull("a now/next read waited for the write transaction", result)
    }

    /** The write of "just watched" waits for the import; Home's row must not (HOME-FR-34). */
    @Test
    fun aChannelWatchedDuringAWriteLeadsHomesRowAtOnceAndIsSavedAfterwards() {
        GuideFixture.seed(graph, groups = 1, perGroup = 4)
        val reads = graph.data.live
        val now = System.currentTimeMillis()
        runBlocking { reads.recordWatched("fixture-0:c0") }
        var pendingWrite: Job? = null
        val during = duringAWrite {
            pendingWrite = graph.appScope.launch { reads.recordWatched("fixture-0:c2") }
            withTimeoutOrNull(5_000) {
                // The recent write is queued behind the held transaction by now or soon after.
                var keys: List<String>
                do {
                    keys = graph.data.home.recentChannels(now).map { it.key }
                } while (keys.firstOrNull() != "fixture-0:c2" && run { delay(50); true })
                keys
            }
        }
        assertEquals(listOf("fixture-0:c2", "fixture-0:c0"), during)
        runBlocking {
            withTimeout(10_000) { pendingWrite!!.join() }
            assertEquals(listOf("fixture-0:c2", "fixture-0:c0"), graph.data.home.recentChannels(now).map { it.key })
            assertEquals("fixture-0:c2", graph.data.database.viewer().recentKeys(com.sohva.tv.core.data.database.DEFAULT_PROFILE).first())
        }
    }
}
