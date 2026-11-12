package com.sohva.tv.app

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.update.AndroidUpdateInstaller
import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The updater's package check on a real Android (spec 72 ABOUT-FR-09): a downloaded file is
 * installed only when it is this application in a newer build. The accepting case needs a newer
 * build of the debug app pushed to `/data/local/tmp/sohva-newer.apk`
 * (`./gradlew :app:assembleDebug -Psohva.versionCode=999`); without it that case is skipped.
 */
@RunWith(AndroidJUnit4::class)
class UpdatePackageCheckTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val lines = ArrayList<String>()
    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) {
            lines += "$event: $message"
        }

        override fun error(event: String, message: String?, error: Throwable?) {
            lines += "$event: $message"
        }

        override fun snapshot(): List<String> = lines
    }
    private val installer = AndroidUpdateInstaller(app, log, Dispatchers.IO)

    @Test
    fun theInstalledBuildItselfIsNotNewer() {
        assertFalse(installer.accepts(File(app.packageCodePath)))
        assertTrue(lines.toString(), "update: download refused: not newer" in lines)
    }

    @Test
    fun anotherPackageIsRefused() {
        assertFalse(installer.accepts(File(instrumentation.context.packageCodePath)))
        assertTrue(lines.toString(), "update: download refused: another package" in lines)
    }

    @Test
    fun aFileThatIsNotAPackageIsRefused() {
        val junk = File(app.cacheDir, "not-a-package.apk").apply { writeText("not a package") }
        try {
            assertFalse(installer.accepts(junk))
        } finally {
            junk.delete()
        }
    }

    @Test
    fun aNewerBuildFromTheSameSignerIsAccepted() {
        val copy = File(app.cacheDir, "newer-check.apk")
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/sohva-newer.apk")).use { input ->
            copy.outputStream().use { input.copyTo(it) }
        }
        try {
            assumeTrue("no newer build was pushed to /data/local/tmp/sohva-newer.apk", copy.length() > 0)
            assertTrue(lines.toString(), installer.accepts(copy))
            assertFalse(lines.toString(), lines.any { "refused" in it })
        } finally {
            copy.delete()
        }
    }
}
