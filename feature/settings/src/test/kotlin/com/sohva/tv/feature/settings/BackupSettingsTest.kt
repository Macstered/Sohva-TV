package com.sohva.tv.feature.settings

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 71 §4.1, §8: the password rules and the question before a restore removes sources. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupSettingsTest {
    private var restored = 0
    private var discarded = 0
    private val passwords = ArrayList<CharArray>()

    private val services = object : BackupSettingsServices {
        override suspend fun save(target: Uri, password: CharArray): BackupOutcome {
            passwords += password
            return BackupOutcome.Saved
        }

        override suspend fun open(target: Uri, password: CharArray): BackupOutcome {
            passwords += password
            return BackupOutcome.Opened(listOf("Extra playlist"))
        }

        override suspend fun restore(): BackupOutcome {
            restored++
            return BackupOutcome.Restored
        }

        override fun discard() {
            discarded++
        }

        override suspend fun unfinished(): Boolean = true

        override suspend fun clearGuide() = Unit
    }

    private val backup = BackupSettings(services, CoroutineScope(UnconfinedTestDispatcher()))
    private val file = Uri.parse("content://documents.example/backup")

    @Test
    fun thePasswordNeedsEightCharactersAndALongerPasteIsRefused() {
        assertEquals(BackupStatus.Unfinished, backup.state.value.status)
        backup.type("1234567")
        assertFalse(backup.state.value.canAct)
        backup.type("12345678")
        assertTrue(backup.state.value.canAct)
        backup.type("x".repeat(129))
        assertEquals("12345678", backup.state.value.password)
    }

    @Test
    fun aSaveEmptiesTheFieldAndWipesThePasswordItPassed() {
        backup.type("fictional-pass")
        backup.save(null)
        assertEquals("fictional-pass", backup.state.value.password)
        backup.save(file)
        assertEquals("", backup.state.value.password)
        assertEquals(BackupStatus.Done(BackupOutcome.Saved), backup.state.value.status)
        assertTrue(passwords.single().all { it == '\u0000' })
    }

    @Test
    fun aRestoreThatRemovesSourcesAsksFirst() {
        backup.type("fictional-pass")
        backup.open(file)
        assertEquals(listOf("Extra playlist"), backup.state.value.removes)
        assertEquals(0, restored)
        assertFalse(backup.state.value.canAct)
        backup.cancelRestore()
        assertEquals(1, discarded)
        assertNull(backup.state.value.busy)
        backup.type("fictional-pass")
        backup.open(file)
        backup.confirmRestore()
        assertEquals(1, restored)
        assertEquals(BackupStatus.Done(BackupOutcome.Restored), backup.state.value.status)
    }
}
