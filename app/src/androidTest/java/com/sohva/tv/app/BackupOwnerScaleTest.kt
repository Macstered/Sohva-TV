package com.sohva.tv.app

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.settings.AppBackupSettings
import com.sohva.tv.core.data.database.OrganizationRuleEntity
import com.sohva.tv.feature.settings.BackupOutcome
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 71 BACKUP-13, §9: a TV with a 200,000-film library saves a backup of tens of kilobytes; the
 * export reads the household's rows only, never the catalogue. Logged as `BackupScale` for the
 * performance log.
 */
@RunWith(AndroidJUnit4::class)
class BackupOwnerScaleTest {
    @get:Rule
    val clear = ClearStateRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph

    @Test
    fun aLargeLibrarySavesASmallBackup() {
        LibraryFixture.seed(graph, groups = listOf("Drama", "Comedy", "Action", "Family"), perGroup = 50_000)
        // Film rules on work keys, as the library manager writes them (spec 42).
        val rules = (0 until 300).map { OrganizationRuleEntity("MOVIES", "", "", "work:tmdb:$it", false, null, null) }
        graph.data.database.backup().putRules(rules)
        val file = File(instrumentation.targetContext.cacheDir, "scale.smbak")
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val started = SystemClock.elapsedRealtime()
        val outcome = runBlocking { AppBackupSettings(graph) {}.save(Uri.fromFile(file), "fictional-pass".toCharArray()) }
        val ms = SystemClock.elapsedRealtime() - started
        val after = runtime.totalMemory() - runtime.freeMemory()
        Log.i("BackupScale", "200000 films, 300 rules: ${file.length()} bytes in $ms ms; heap ${before / 1_048_576} -> ${after / 1_048_576} MB of ${runtime.maxMemory() / 1_048_576}")
        try {
            assertEquals(BackupOutcome.Saved, outcome)
            assertTrue("${file.length()} bytes", file.length() < 100_000)
        } finally {
            file.delete()
        }
    }
}
