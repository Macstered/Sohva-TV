package com.sohva.tv.core.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 10 §11 "Instrumented": two imports of one source at once, against the device's SQLite. */
@RunWith(AndroidJUnit4::class)
class ConcurrentImportDeviceTest {
    @Test
    fun twoImportsOfOneSourceLeaveItIntact() = ImportScenarios.twoGuideImportsOfOneSourceOverlap()
}
