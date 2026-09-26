package com.sohva.tv.core.sync.update

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.update.UpdateFailure
import com.sohva.tv.core.model.update.UpdatePhase
import com.sohva.tv.core.net.update.UpdateHttp
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 72 §4.2–§4.4 and §8: the updater's phases against a local feed and a fake installer. */
class UpdaterTest {
    private val server = MockWebServer()
    private val apkBytes = ByteArray(200_000) { (it % 97).toByte() }
    private val dmBytes = ByteArray(9_000) { (it % 13).toByte() }
    private var sums = ""
    private var feedCalls = 0
    private val folder: File = Files.createTempDirectory("updates").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lines = ArrayList<String>()
    private var now = 1_790_000_000_000L

    private val memory = object : UpdateMemory {
        var last: Long? = null
        val notes = HashMap<Int, String>()
        var kept: DownloadedUpdate? = null

        override suspend fun lastCheck(): Long? = last

        override suspend fun setLastCheck(millis: Long) {
            last = millis
        }

        override suspend fun notes(versionCode: Int): String? = notes[versionCode]

        override suspend fun setNotes(versionCode: Int, notes: String) {
            this.notes[versionCode] = notes
        }

        override suspend fun downloaded(): DownloadedUpdate? = kept

        override suspend fun setDownloaded(update: DownloadedUpdate?) {
            kept = update
        }
    }

    private val installer = object : UpdateInstaller {
        var allowed = true
        var session = SessionOutcome.SUCCESS
        val calls = ArrayList<String>()

        override fun mayInstall(): Boolean = allowed

        override suspend fun installWithProfile(apk: File, profile: File): SessionOutcome {
            calls += "session ${apk.name} ${profile.name}"
            return session
        }

        override fun installPlain(apk: File): Boolean {
            calls += "plain ${apk.name}"
            return true
        }

        override fun openPermission(): Boolean = true
    }

    private val log = object : DiagnosticsLog {
        override fun info(event: String, message: String) {
            lines += "$event: $message"
        }

        override fun error(event: String, message: String?, error: Throwable?) {
            lines += "$event: $message"
        }

        override fun snapshot(): List<String> = lines
    }

    private val clock = object : Clock {
        override fun wallMillis(): Long = now

        override fun monotonicNanos(): Long = 0
    }

    private fun hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun feed(checksums: Boolean = true) = """
        [{"tag_name":"v0.2.0-beta.2","draft":false,"prerelease":true,"body":"# New\n\nAndroid build **101**.\n\n## Changed since beta 1\n- Faster guide.",
          "assets":[${if (checksums) """{"name":"SHA256SUMS.txt","browser_download_url":"${server.url("/sums")}","size":300},""" else ""}
            {"name":"sohva.api28.dm","browser_download_url":"${server.url("/dm")}","size":9000},
            {"name":"sohva.apk","browser_download_url":"${server.url("/apk")}","size":200000}]},
         {"tag_name":"v0.2.0-beta.1","draft":false,"prerelease":true,"body":"Android build **100**.\n\n## Changed\n- First.","assets":[]}]
    """.trimIndent()

    private var withChecksums = true

    @Before
    fun start() {
        sums = "${hex(apkBytes)}  sohva.apk\n${hex(dmBytes)}  sohva.api28.dm\n"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.target) {
                "/releases" -> {
                    feedCalls++
                    MockResponse.Builder().body(feed(withChecksums)).build()
                }
                "/sums" -> MockResponse.Builder().body(sums).build()
                "/apk" -> MockResponse.Builder().body(Buffer().write(apkBytes)).build()
                "/dm" -> MockResponse.Builder().body(Buffer().write(dmBytes)).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.close()
        folder.deleteRecursively()
    }

    private fun updater(enabled: Boolean = true, sdk: Int = 30) = Updater(
        UpdaterConfig(enabled, "0.2.0-beta.1", 100, sdk, server.url("/releases")),
        UpdateHttp(OkHttpClient()), memory, folder, installer, log, clock, scope, language = { "en" },
    )

    private fun Updater.await(phase: UpdatePhase) = runBlocking { withTimeout(10_000) { state.first { it.phase == phase } } }

    @Test
    fun aCheckOffersTheNewerBuildWithItsNotesAndKeepsTheInstalledNotes() {
        val updater = updater()
        updater.check()
        val available = updater.await(UpdatePhase.AVAILABLE)
        assertEquals("0.2.0-beta.2", available.update!!.version)
        assertEquals("0.2.0-beta.2", available.notesVersion)
        assertEquals("• Faster guide.", available.notes)
        assertEquals("• First.", memory.notes[100])
        assertEquals(now, memory.last)
        assertTrue("update: available: 0.2.0-beta.2" in lines)
    }

    @Test
    fun downloadVerifiesThenInstallsWithTheProfileInOneSession() {
        val updater = updater()
        updater.check()
        updater.await(UpdatePhase.AVAILABLE)
        updater.download()
        updater.await(UpdatePhase.DOWNLOADED)
        assertEquals(setOf("sohva.apk", "sohva.api28.dm"), folder.list()!!.toSet())
        updater.install()
        runBlocking { withTimeout(5_000) { while (installer.calls.isEmpty()) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("session sohva.apk sohva.api28.dm"), installer.calls)
    }

    @Test
    fun aFailedSessionFallsBackToTheInstallerScreenNextTime() {
        installer.session = SessionOutcome.FAILED
        val updater = updater()
        updater.check()
        updater.await(UpdatePhase.AVAILABLE)
        updater.download()
        updater.await(UpdatePhase.DOWNLOADED)
        updater.install()
        runBlocking { withTimeout(5_000) { while (installer.calls.size < 1) kotlinx.coroutines.delay(10) } }
        updater.install()
        runBlocking { withTimeout(5_000) { while (installer.calls.size < 2) kotlinx.coroutines.delay(10) } }
        assertEquals("plain sohva.apk", installer.calls[1])
    }

    @Test
    fun aWrongDigestDiscardsTheFileAndNoChecksumFileStartsNothing() {
        sums = "${"0".repeat(64)}  sohva.apk\n"
        val updater = updater()
        updater.check()
        updater.await(UpdatePhase.AVAILABLE)
        updater.download()
        val failed = updater.await(UpdatePhase.FAILED)
        assertEquals(UpdateFailure.CHECKSUM_MISMATCH, failed.failure)
        assertTrue(folder.list()!!.isEmpty())
        // The update's notes stay visible after a failure.
        assertEquals("• Faster guide.", failed.notes)

        withChecksums = false
        val second = updater()
        second.check()
        second.await(UpdatePhase.AVAILABLE)
        second.download()
        assertEquals(UpdateFailure.NO_CHECKSUMS, second.await(UpdatePhase.FAILED).failure)
    }

    @Test
    fun withoutThePermissionInstallAsksForItAndAVanishedFileOffersDownloadAgain() {
        installer.allowed = false
        val updater = updater()
        updater.check()
        updater.await(UpdatePhase.AVAILABLE)
        updater.download()
        updater.await(UpdatePhase.DOWNLOADED)
        updater.install()
        updater.await(UpdatePhase.NEEDS_PERMISSION)
        folder.listFiles()!!.forEach { it.delete() }
        updater.install()
        updater.await(UpdatePhase.AVAILABLE)
        assertTrue(installer.calls.isEmpty())
    }

    @Test
    fun theAutomaticCheckRunsOnceADayAndNeverWhenDisabled() {
        memory.last = now - 3_600_000L
        updater().start(automatic = true)
        runBlocking { kotlinx.coroutines.delay(300) }
        assertEquals(0, feedCalls)
        // A clock set back counts as due (§8).
        memory.last = now + 5 * 86_400_000L
        updater().apply { start(automatic = true) }.await(UpdatePhase.AVAILABLE)
        assertEquals(1, feedCalls)
        val disabled = updater(enabled = false)
        disabled.start(automatic = true)
        disabled.check()
        runBlocking { kotlinx.coroutines.delay(300) }
        assertEquals(UpdatePhase.DISABLED, disabled.state.value.phase)
        assertEquals(1, feedCalls)
    }

    /** Android kills the app when installs are allowed: the verified download is still there after. */
    @Test
    fun aVerifiedDownloadSurvivesTheProcessAndATamperedOneDoesNot() {
        val first = updater()
        first.check()
        first.await(UpdatePhase.AVAILABLE)
        first.download()
        first.await(UpdatePhase.DOWNLOADED)
        val again = updater()
        again.start(automatic = true)
        val restored = again.await(UpdatePhase.DOWNLOADED)
        assertEquals("0.2.0-beta.2", restored.update!!.version)
        assertEquals("• Faster guide.", restored.notes)
        assertEquals(1, feedCalls)
        again.install()
        runBlocking { withTimeout(5_000) { while (installer.calls.isEmpty()) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("session sohva.apk sohva.api28.dm"), installer.calls)
        // A changed file is not installed: it is removed and the start checks again.
        File(folder, "sohva.apk").appendBytes(byteArrayOf(1))
        memory.last = null
        val third = updater()
        third.start(automatic = true)
        third.await(UpdatePhase.AVAILABLE)
        assertNull(memory.kept)
    }

    @Test
    fun aFailedCheckKeepsTheLastTimeSoTheNextStartTriesAgain() {
        server.close()
        val updater = updater()
        updater.check()
        assertEquals(UpdateFailure.NETWORK, updater.await(UpdatePhase.FAILED).failure)
        assertNull(memory.last)
        assertFalse(lines.none { it.startsWith("update: check failed") })
    }
}
