package com.streammate.tv.app

import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the updater's entry points, not just its UI or the policy flag. */
@RunWith(AndroidJUnit4::class)
class LabUpdateIsolationTest {
    @Test fun developmentPackageCannotCheckDownloadOrLaunchInstaller() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        // Debug is also forbidden from using the production updater. Test with
        // its real package manager and private preferences, without needing Lab installed.
        check(target.packageName != "com.streammate.tv")
        val context = object : ContextWrapper(target) {
            override fun startActivity(intent: Intent) = error("Development updater launched an activity")
        }
        val client = OkHttpClient.Builder().addInterceptor {
            error("Development updater made a network request")
        }.build()
        val checker = AppUpdateChecker(context, client, clock = { error("Update scheduling ran") })
        val update = AvailableUpdate(
            "future", 999, "fixture", ReleaseAsset("fixture.apk", "https://example.invalid/fixture.apk", 1),
            ReleaseAsset("SHA256SUMS.txt", "https://example.invalid/SHA256SUMS.txt", 1),
        )
        checker.checkIfDue()
        checker.check()
        checker.download(update)
        checker.install(update, File(target.cacheDir, "nonexistent-lab-update.apk"))
        checker.openInstallPermissionSettings()
        checker.retryInstall()
        assertEquals(AppUpdateState.Disabled, checker.state.value)
    }
}
