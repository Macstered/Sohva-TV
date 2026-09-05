package com.streammate.tv.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.core.model.IptvSourceType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The phone page on a real socket: the token gates it, a form post lands as a source. */
@RunWith(AndroidJUnit4::class)
class PhoneSetupServerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val received = mutableListOf<PhoneSetupSubmission>()
    private val server = PhoneSetupServer(context) { received += it }
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    @After
    fun stopServer() = server.stop()

    @Test
    fun theTokenGatesThePageAndAFormPostBecomesASource() = runBlocking {
        server.start()
        val running = server.state.value as? PhoneSetupState.Running
            ?: return@runBlocking // No LAN address on this device: nothing to test against.
        val base = running.url.substringBefore("/?")
        val token = running.url.substringAfter("t=")

        client.newCall(Request.Builder().url("$base/").get().build()).execute().use { response ->
            assertEquals(403, response.code)
        }
        client.newCall(Request.Builder().url(running.url).get().build()).execute().use { response ->
            assertEquals(200, response.code)
            assertTrue(response.body.string().contains("name=\"xtream_url\""))
        }

        val form = FormBody.Builder()
            .add("t", token)
            .add("type", "m3u")
            .add("name", "Phone list")
            .add("m3u_url", "http://provider.example/list.m3u")
            .add("xmltv_url", "http://provider.example/guide.xml")
            .build()
        client.newCall(Request.Builder().url("$base/submit").post(form).build()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(1, received.size)
        val source = received.single().source!!
        assertEquals(IptvSourceType.M3U, source.type)
        assertEquals("Phone list", source.name)
        assertEquals("http://provider.example/guide.xml", source.xmlTvUrl)
        assertNull(received.single().tmdbToken)
        assertEquals(1, (server.state.value as PhoneSetupState.Running).receivedCount)

        val wrongToken = FormBody.Builder().add("t", "nope").add("type", "m3u").add("name", "x")
            .add("m3u_url", "http://provider.example/list.m3u").build()
        client.newCall(Request.Builder().url("$base/submit").post(wrongToken).build()).execute().use { response ->
            assertEquals(403, response.code)
        }
        assertEquals(1, received.size)
    }
}
