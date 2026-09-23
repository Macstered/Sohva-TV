package com.streammate.tv.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An install session that carries the install-time profile, run for real.
 *
 * Opt-in and for a disposable emulator only: it installs another package (the
 * release build, from files a script has pushed) and needs that script to
 * press the system's confirmation, which no test may do on a television
 * someone is watching. `-e updateSessionApk <path> -e updateSessionProfile
 * <path> -e updateSessionPackage <package>`.
 */
@RunWith(AndroidJUnit4::class)
class UpdateSessionInstallerDeviceTest {
    @Test
    fun theSystemAcceptsTheApkWithItsProfileAndInstallsIt() {
        val arguments = InstrumentationRegistry.getArguments()
        val apkPath = arguments.getString("updateSessionApk")
        val profilePath = arguments.getString("updateSessionProfile")
        val targetPackage = arguments.getString("updateSessionPackage")
        assumeTrue("Opt-in: needs the files and a script to confirm", apkPath != null && profilePath != null && targetPackage != null)
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Session files are read through this app, as a downloaded update's are.
        val apk = File(context.cacheDir, "session-test.apk").also { File(apkPath!!).copyTo(it, overwrite = true) }
        val profile = File(context.cacheDir, "session-test.dm").also { File(profilePath!!).copyTo(it, overwrite = true) }
        val outcome = AtomicReference<UpdateSessionInstaller.Outcome?>()

        assertTrue(
            "The session could not be set up",
            UpdateSessionInstaller(context).install(apk, profile, targetPackage!!) { outcome.set(it) },
        )

        // This package cannot see the other one; its own session going
        // away, with no failure reported, is the install having finished.
        // The script that ran this test reads the result off the package.
        val installer = context.packageManager.packageInstaller
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline && outcome.get() == null && installer.mySessions.isNotEmpty()) {
            Thread.sleep(500)
        }
        assertNull("The system refused the session", outcome.get())
        assertTrue("The session was still waiting after ninety seconds", installer.mySessions.isEmpty())
    }
}
