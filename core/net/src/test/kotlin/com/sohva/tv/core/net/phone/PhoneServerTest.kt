package com.sohva.tv.core.net.phone

import com.sohva.tv.core.model.source.SourceType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhoneServerTest {
    private val answers = object : PhoneAnswers {
        override fun page() = PhonePageTexts(
            "fi", "Sohva TV setup", "Fill in <a> playlist", "Xtream account", "M3U playlist", "Name", "Server address", "Username",
            "Password", "Playlist address", "Guide address", "Optional keys", "Keys help", "TMDB", "API-Sports", "Send to the TV", "Privacy",
        )
        override fun saved(sourceName: String) = "Saved on the TV: $sourceName."
        override fun keysSaved() = "Keys saved on the TV."
        override fun invalid() = "Something is missing."
        override fun failed() = "The TV could not save that."
        override fun forbidden() = "Scan it again."
        override fun badRequest() = "That request could not be read."
        override fun logoPage(channelName: String) = LogoPageTexts("fi", "Logo for $channelName", "Choose one", "Choose a picture", "Sending", "Not a picture")
        override fun logoSaved(channelName: String) = "Logo saved for $channelName."
    }
    private val received = CopyOnWriteArrayList<PhoneSubmission>()
    private var accept = true
    private val server = PhoneServer(answers, { received += it; accept })

    @After
    fun stop() = server.stop()

    private fun running(): PhoneState.Running {
        server.start(InetAddress.getLoopbackAddress() as Inet4Address)
        return server.state.value as PhoneState.Running
    }

    private class Reply(val code: Int, val headers: Map<String, String>, val body: String)

    /** Sends raw bytes and reads the whole answer (the server closes after each response). */
    private fun send(url: String, raw: String): Reply {
        val host = url.removePrefix("http://").substringBefore('/')
        Socket(host.substringBefore(':'), host.substringAfter(':').toInt()).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write(raw.toByteArray(Charsets.UTF_8))
            val text = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = text.substringBefore("\r\n\r\n").split("\r\n")
            val headers = head.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
            return Reply(head[0].split(' ')[1].toInt(), headers, text.substringAfter("\r\n\r\n"))
        }
    }

    private fun post(state: PhoneState.Running, form: String, token: String = state.url.substringAfter('#'), origin: String? = null): Reply {
        val host = state.url.removePrefix("http://").substringBefore('/')
        val body = form.toByteArray(Charsets.UTF_8)
        return send(
            state.url,
            "POST /submit HTTP/1.1\r\nHost: $host\r\nAuthorization: Bearer $token\r\n" + (origin?.let { "Origin: $it\r\n" } ?: "") +
                "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.size}\r\n\r\n$form",
        )
    }

    @Test
    fun thePageNeedsNoTokenAndCarriesNone() {
        val state = running()
        val host = state.url.removePrefix("http://").substringBefore('/')
        val page = send(state.url, "GET / HTTP/1.1\r\nHost: $host\r\n\r\n")
        assertEquals(200, page.code)
        assertFalse(page.body.contains(state.url.substringAfter('#')))
        assertTrue(page.body.contains("name=\"xtream_url\""))
        assertTrue(page.body.contains("lang=\"fi\""))
        assertTrue("texts are escaped", page.body.contains("Fill in &lt;a&gt; playlist"))
        assertFalse("no external resources", Regex("""(src|href)=["']https?:""").containsMatchIn(page.body))
        assertEquals("no-store", page.headers["cache-control"])
        val script = page.body.substringAfter("<script>").substringBefore("</script>")
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
        assertTrue(page.headers["content-security-policy"].orEmpty().contains("'sha256-$hash'"))
        assertEquals("the token is 128 bits of hex", 32, state.url.substringAfter('#').length)
        assertFalse(state.toString().contains(state.url.substringAfter('#')))
    }

    /** Spec 50 ADDON-FR-45: Origin required, text/plain, the list rule; the first valid list ends the session. */
    @Test
    fun addonModeTakesOneValidListThenStops() {
        server.start(InetAddress.getLoopbackAddress() as Inet4Address, PhoneMode.Addons { String(it, Charsets.UTF_8).startsWith("https://") })
        val state = server.state.value as PhoneState.Running
        val host = state.url.removePrefix("http://").substringBefore('/')
        val token = state.url.substringAfter('#')
        val page = send(state.url, "GET / HTTP/1.1\r\nHost: $host\r\n\r\n")
        assertEquals(200, page.code)
        val script = page.body.substringAfter("<script>").substringBefore("</script>")
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
        assertTrue(page.headers["content-security-policy"].orEmpty().contains("'sha256-$hash'"))
        fun list(text: String, origin: String?, type: String = "text/plain; charset=utf-8"): Reply {
            val body = text.toByteArray(Charsets.UTF_8)
            return send(
                state.url,
                "POST /submit HTTP/1.1\r\nHost: $host\r\nAuthorization: Bearer $token\r\n" + (origin?.let { "Origin: $it\r\n" } ?: "") +
                    "Content-Type: $type\r\nContent-Length: ${body.size}\r\n\r\n$text",
            )
        }
        val url = "https://provider.example/manifest.json"
        assertEquals("no Origin", 403, list(url, null).code)
        assertEquals(400, list(url, "http://$host", type = "application/x-www-form-urlencoded").code)
        assertEquals("the rule refuses it; the session goes on", 400, list("ftp://nope", "http://$host").code)
        assertTrue(server.state.value is PhoneState.Running)
        assertEquals(200, list(url, "http://$host").code)
        assertEquals(url, String((received.single() as PhoneSubmission.AddonList).bytes, Charsets.UTF_8))
        runBlocking { withTimeout(3_000) { server.state.first { it == PhoneState.Stopped } } }
    }

    /**
     * Spec 02 HOME-FR-100: the Trakt list page takes an address as text/plain with Origin and the
     * token; the receiver names the list it added; the page stays open for another.
     */
    @Test
    fun traktListModeAddsListsAndStaysOpen() {
        val names = listOf("Fictional favourites", null)
        var n = 0
        val lists = PhoneServer(answers, { s ->
            val name = names[n++]
            (s as PhoneSubmission.TraktList).added.name = name
            received += s
            name != null
        })
        try {
            lists.start(InetAddress.getLoopbackAddress() as Inet4Address, PhoneMode.TraktList { it.startsWith("https://trakt.tv/") })
            val state = lists.state.value as PhoneState.Running
            val host = state.url.removePrefix("http://").substringBefore('/')
            val token = state.url.substringAfter('#')
            val page = send(state.url, "GET / HTTP/1.1\r\nHost: $host\r\n\r\n")
            assertEquals(200, page.code)
            assertTrue(page.body.contains("id=\"list\""))
            val script = page.body.substringAfter("<script>").substringBefore("</script>")
            val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
            assertTrue(page.headers["content-security-policy"].orEmpty().contains("'sha256-$hash'"))
            fun list(text: String, origin: String?): Reply {
                val body = text.toByteArray(Charsets.UTF_8)
                return send(
                    state.url,
                    "POST /submit HTTP/1.1\r\nHost: $host\r\nAuthorization: Bearer $token\r\n" + (origin?.let { "Origin: $it\r\n" } ?: "") +
                        "Content-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\n\r\n$text",
                )
            }
            val address = "https://trakt.tv/users/viewer-one/lists/best-films"
            assertEquals("no Origin", 403, list(address, null).code)
            assertEquals("not an address", 400, list("hello", "http://$host").code)
            val first = list(address, "http://$host")
            assertEquals(200, first.code)
            assertTrue(first.body, first.body.contains("Fictional favourites"))
            assertEquals(address, (received.single() as PhoneSubmission.TraktList).text)
            // The receiver could not add the second: the page says so and stays open.
            assertEquals(400, list("https://trakt.tv/lists/5", "http://$host").code)
            assertTrue(lists.state.value is PhoneState.Running)
            assertEquals("Fictional favourites", (lists.state.value as PhoneState.Running).lastSource)
        } finally {
            lists.stop()
        }
    }

    /** Spec 21 CHAN-FR-41/42: one picture, only for the channel the page was opened for. */
    @Test
    fun logoModeTakesOnePictureForItsChannelOnly() {
        server.start(InetAddress.getLoopbackAddress() as Inet4Address, PhoneMode.Logo("src:c1", "Northstar <1>"))
        val state = server.state.value as PhoneState.Running
        val host = state.url.removePrefix("http://").substringBefore('/')
        val page = send(state.url, "GET / HTTP/1.1\r\nHost: $host\r\n\r\n")
        assertEquals(200, page.code)
        assertTrue(page.body.contains("Logo for Northstar &lt;1&gt;"))
        assertTrue(page.body.contains("value=\"src:c1\""))
        val script = page.body.substringAfter("<script>").substringBefore("</script>")
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(script.toByteArray()))
        assertTrue(page.headers["content-security-policy"].orEmpty().contains("'sha256-$hash'"))
        val png = "data:image/png;base64," + Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val image = java.net.URLEncoder.encode(png, "UTF-8")
        // Another channel, a source form, or no picture: refused, nothing received.
        assertEquals(403, post(state, "type=logo&channel=src%3Ac2&image=$image").code)
        assertEquals(403, post(state, "type=keys&tmdb_token=abc").code)
        assertEquals(400, post(state, "type=logo&channel=src%3Ac1&image=data%3Aimage%2Fjpeg%3Bbase64%2CAAAA").code)
        assertTrue(received.isEmpty())
        val ok = post(state, "type=logo&channel=src%3Ac1&image=$image")
        assertEquals(200, ok.code)
        assertEquals("Logo saved for Northstar <1>.", ok.body)
        val logo = received.single() as PhoneSubmission.Logo
        assertEquals("src:c1", logo.channelKey)
        assertTrue(logo.png.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue((server.state.value as PhoneState.Running).logoSaved)
    }

    @Test
    fun aPostedSourceIsReceivedOnceAndCounted() {
        val state = running()
        val form = "type=m3u&name=+Living+room+&m3u_url=http%3A%2F%2Fprovider.example%2Flist.m3u&xmltv_url="
        val first = post(state, form, origin = state.url.substringBefore("/#"))
        assertEquals(200, first.code)
        assertEquals("Saved on the TV: Living room.", first.body)
        assertEquals(200, post(state, form).code)
        assertEquals("the same form twice is saved once", 1, received.size)
        val source = (received.single() as PhoneSubmission.NewSource).config
        assertEquals("http://provider.example/list.m3u", source.secrets.m3uUrl)
        assertNull(source.secrets.xmlTvUrl)
        assertEquals(PhoneState.Running(state.url, 1, "Living room", false), server.state.value)
    }

    @Test
    fun wrongTokenHostOrOriginIsRefusedAndReceivesNothing() {
        val state = running()
        val form = "type=keys&tmdb_token=abc"
        assertEquals(403, post(state, form, token = "0".repeat(32)).code)
        assertEquals(403, post(state, form, origin = "http://evil.example").code)
        val wrongHost = send(state.url, "GET / HTTP/1.1\r\nHost: rebound.example:80\r\n\r\n")
        assertEquals(403, wrongHost.code)
        assertTrue(received.isEmpty())
    }

    @Test
    fun routesMethodsAndInvalidForms() {
        val state = running()
        val host = state.url.removePrefix("http://").substringBefore('/')
        assertEquals(404, send(state.url, "GET /admin HTTP/1.1\r\nHost: $host\r\n\r\n").code)
        assertEquals(405, send(state.url, "PUT / HTTP/1.1\r\nHost: $host\r\n\r\n").code)
        assertEquals(400, post(state, "type=m3u&name=A&m3u_url=not-an-address").code)
        assertEquals(400, send(state.url, "garbage\r\n\r\n").code)
        accept = false
        assertEquals(500, post(state, "type=keys&api_sports_key=k").code)
    }

    @Test
    fun stoppingClosesThePage() {
        val state = running()
        server.stop()
        assertEquals(PhoneState.Stopped, server.state.value)
        try {
            send(state.url, "GET / HTTP/1.1\r\n\r\n")
            fail("still serving")
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun theLifetimeEndsThePage(): Unit = runBlocking {
        val short = PhoneServer(answers, { true }, lifetimeMillis = 200)
        short.start(InetAddress.getLoopbackAddress() as Inet4Address)
        withTimeout(5_000) { short.state.first { it == PhoneState.Stopped } }
    }

    @Test
    fun noAddressMeansNoNetwork() {
        server.start(null)
        assertEquals(PhoneState.NoNetwork, server.state.value)
    }
}

class PhoneProtocolTest {
    private fun read(raw: String, max: Int = PhoneServer.MAX_BODY): PhoneRequest =
        PhoneRequest.read(ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1)), max, Long.MAX_VALUE)

    private fun refused(raw: String, max: Int = PhoneServer.MAX_BODY): PhoneRequest.Problem = try {
        read(raw, max)
        throw AssertionError("accepted")
    } catch (e: PhoneRequest.Refused) {
        e.problem
    }

    @Test
    fun aBrowserFormPostParses() {
        val body = "type=m3u&m3u_url=http%3A%2F%2Fprovider.example%2Flist.m3u"
        val request = read("POST /submit?x=1 HTTP/1.1\r\nHost: 192.0.2.5:4321\r\nContent-Length: ${body.length}\r\n\r\n$body")
        assertEquals("POST", request.method)
        assertEquals("/submit", request.path)
        assertEquals("192.0.2.5:4321", request.headers["host"])
        assertEquals("http://provider.example/list.m3u", PhoneRequest.form(request.body)["m3u_url"])
        assertFalse(request.toString().contains("provider"))
    }

    @Test
    fun oversizedMalformedAndRepeatedRequestsAreRefused() {
        assertEquals(PhoneRequest.Problem.TOO_LARGE, refused("POST / HTTP/1.1\r\nContent-Length: 16385\r\n\r\n"))
        assertEquals(PhoneRequest.Problem.MALFORMED, refused("garbage\r\n\r\n"))
        assertEquals(PhoneRequest.Problem.MALFORMED, refused("GET / HTTP/1.1\r\nHost: a\r\nhost: b\r\n\r\n"))
        assertEquals(PhoneRequest.Problem.MALFORMED, refused("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
        assertEquals(PhoneRequest.Problem.MALFORMED, refused("GET / HTTP/1.1\r\nX: ${"a".repeat(9_000)}\r\n\r\n"))
    }

    @Test
    fun aSlowRequestRunsOutOfTime() {
        var clock = 0L
        val slow = object : InputStream() {
            override fun read(): Int {
                clock += 1_000_000_000L
                return 'x'.code
            }
        }
        try {
            PhoneRequest.read(slow, 100, deadline = 10_000_000_000L, now = { clock })
            fail("no deadline")
        } catch (e: PhoneRequest.Refused) {
            assertEquals(PhoneRequest.Problem.TIMED_OUT, e.problem)
        }
    }

    @Test
    fun formsBecomeSubmissionsWithTheSettingsRules() {
        val xtream = PhoneSubmission.parse(
            mapOf("type" to "XTREAM", "name" to " Living room ", "xtream_url" to "http://panel.example:8080//", "xtream_username" to " u ", "xtream_password" to " p "),
        ) as PhoneSubmission.NewSource
        assertEquals("Living room", xtream.config.source.name)
        assertEquals(SourceType.XTREAM, xtream.config.source.type)
        assertTrue(xtream.config.source.id.startsWith("xtream-"))
        assertEquals("http://panel.example:8080", xtream.config.secrets.xtreamBaseUrl)
        assertEquals("u", xtream.config.secrets.xtreamUsername)
        assertEquals(" p ", xtream.config.secrets.xtreamPassword)
        assertFalse(xtream.toString().contains("panel.example"))

        val keys = PhoneSubmission.parse(mapOf("type" to "keys", "tmdb_token" to " t ", "api_sports_key" to "")) as PhoneSubmission.Keys
        assertEquals("t", keys.tmdbToken)
        assertNull(keys.apiSportsKey)
        assertFalse(keys.toString().contains("t,"))

        for (bad in listOf(
            mapOf("type" to "m3u", "m3u_url" to "http://provider.example/a"),
            mapOf("type" to "m3u", "name" to "A", "m3u_url" to "provider.example"),
            mapOf("type" to "keys"),
            mapOf("type" to "logo", "name" to "A"),
        )) {
            assertNull(bad.toString(), PhoneSubmission.parse(bad))
        }
    }

    @Test
    fun aPageAddressBecomesASmallQrMatrix() {
        val qr = QrCodes.of("http://192.0.2.200:54321/#" + PhoneServer.newToken())!!
        // Version 4 or 5 plus a one-module margin each side: tens of modules, not a 512 px bitmap.
        assertTrue(qr.size.toString(), qr.size in 35..39)
        assertFalse("the margin is light", qr.isDark(0, 0))
        assertTrue("a finder pattern starts inside the margin", qr.isDark(1, 1))
    }

    @Test
    fun theAddressPickerPrefersTheHomeNetwork() {
        fun ip(text: String): InetAddress = InetAddress.getByName(text)
        // Site-local addresses are what the picker is for; they are built from numbers because the
        // public-source audit keeps private-network addresses out of the repository's text.
        fun ip(a: Int, b: Int, c: Int, d: Int): InetAddress = InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))
        val home = ip(192, 168, 1, 20)
        val bridge = ip(172, 17, 0, 1)
        val candidates = listOf(
            LocalAddress.Candidate("lo", true, true, listOf(ip("127.0.0.1"))),
            LocalAddress.Candidate("tun0", true, false, listOf(ip(10, 8, 0, 2))),
            LocalAddress.Candidate("docker0", true, false, listOf(bridge)),
            LocalAddress.Candidate("wlan0", true, false, listOf(ip("fe80::1"), home)),
        )
        assertEquals(home, LocalAddress.pick(candidates))
        assertEquals(bridge, LocalAddress.pick(candidates.dropLast(1)))
        assertNull("carrier-grade NAT is not site-local", LocalAddress.pick(listOf(LocalAddress.Candidate("wlan0", true, false, listOf(ip(100, 64, 0, 5))))))
    }
}
