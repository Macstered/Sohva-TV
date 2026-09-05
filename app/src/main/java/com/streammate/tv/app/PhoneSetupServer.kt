package com.streammate.tv.app

import android.content.Context
import com.streammate.tv.iptv.R as IptvR
import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvSourceType
import com.streammate.tv.feature.settings.IptvConfigurationValidator
import com.streammate.tv.feature.settings.XtreamConfigurationValidator
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** What a phone posted: a source to save, or keys on their own. */
data class PhoneSetupSubmission(
    val source: IptvSourceConfiguration?,
    val tmdbToken: String?,
    val apiSportsKey: String?,
)

/**
 * The request line, headers and body of one HTTP request, parsed just far
 * enough for a form post from a phone's browser. Pure, so it can be tested
 * without sockets.
 */
data class PhoneSetupRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: String,
)

object PhoneSetupProtocol {
    const val MAX_BODY_BYTES = 16 * 1024
    private const val MAX_HEADER_LINES = 64

    fun parseRequest(input: InputStream): PhoneSetupRequest? {
        val reader = BufferedReader(InputStreamReader(input, Charsets.ISO_8859_1))
        val requestLine = reader.readLine()?.takeIf { it.isNotBlank() } ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = parseForm(target.substringAfter('?', ""))
        val headers = mutableMapOf<String, String>()
        var headerLines = 0
        while (headerLines < MAX_HEADER_LINES) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headerLines += 1
            val name = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (name.isNotEmpty()) headers[name] = value
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length > MAX_BODY_BYTES) return null
        val body = if (length > 0) {
            val buffer = CharArray(length)
            var read = 0
            while (read < length) {
                val count = reader.read(buffer, read, length - read)
                if (count < 0) break
                read += count
            }
            String(buffer, 0, read)
        } else {
            ""
        }
        return PhoneSetupRequest(method, path, query, headers, body)
    }

    /** `a=b&c=d` with percent-decoding; later keys win. */
    fun parseForm(encoded: String): Map<String, String> = encoded
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(name) to decode(value)
        }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /** Turns a posted form into a source, with the same rules the settings screen applies. */
    fun submissionFrom(form: Map<String, String>): Result<PhoneSetupSubmission> = runCatching {
        val tmdbToken = form["tmdb_token"]?.trim()?.takeIf { it.isNotEmpty() }
        val apiSportsKey = form["api_sports_key"]?.trim()?.takeIf { it.isNotEmpty() }
        val type = when (form["type"]?.lowercase()) {
            "xtream" -> IptvSourceType.XTREAM
            "m3u" -> IptvSourceType.M3U
            "keys" -> {
                require(tmdbToken != null || apiSportsKey != null) { "keys" }
                return@runCatching PhoneSetupSubmission(source = null, tmdbToken = tmdbToken, apiSportsKey = apiSportsKey)
            }
            else -> throw IllegalArgumentException("type")
        }
        val name = form["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("name")
        val base = IptvSourceConfiguration(
            id = when (type) {
                IptvSourceType.M3U -> "m3u-${UUID.randomUUID()}"
                IptvSourceType.XTREAM -> "xtream-${UUID.randomUUID()}"
            },
            name = name,
            type = type,
        )
        val source = when (type) {
            IptvSourceType.M3U -> base.copy(
                m3uUrl = IptvConfigurationValidator.validateM3uUrl(form["m3u_url"].orEmpty()),
                xmlTvUrl = IptvConfigurationValidator.validateOptionalXmlTvUrl(form["xmltv_url"].orEmpty()),
            )
            IptvSourceType.XTREAM -> {
                val validated = XtreamConfigurationValidator.validate(
                    form["xtream_url"].orEmpty(),
                    form["xtream_username"].orEmpty(),
                    form["xtream_password"].orEmpty(),
                ).getOrThrow()
                base.copy(
                    xtreamBaseUrl = validated.baseUrl,
                    xtreamUsername = validated.username,
                    xtreamPassword = validated.password,
                )
            }
        }
        PhoneSetupSubmission(source = source, tmdbToken = tmdbToken, apiSportsKey = apiSportsKey)
    }

    fun newToken(random: SecureRandom = SecureRandom()): String =
        (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
}

sealed interface PhoneSetupState {
    data object Stopped : PhoneSetupState
    data object NoNetwork : PhoneSetupState
    data class Running(val url: String, val receivedCount: Int, val lastSourceName: String?) : PhoneSetupState
}

/**
 * A page on the home network where a playlist can be typed from a phone.
 *
 * Alive only while the settings screen shows its QR code. Bound to the TV's
 * own LAN address on a random port, and every request must carry the token
 * in the QR code, so nothing else on the network can put a source into the
 * TV. Plain HTTP, on the LAN, for a token that lives minutes. The posted body
 * is parsed and forgotten; it is never logged.
 */
class PhoneSetupServer(
    private val context: Context,
    private val onSubmission: suspend (PhoneSetupSubmission) -> Unit,
) {
    private val mutableState = MutableStateFlow<PhoneSetupState>(PhoneSetupState.Stopped)
    val state: StateFlow<PhoneSetupState> = mutableState

    private var socket: ServerSocket? = null
    private var token: String = ""
    private val running = AtomicBoolean(false)

    @Synchronized
    fun start() {
        if (running.get()) return
        val address = lanAddress()
        if (address == null) {
            mutableState.value = PhoneSetupState.NoNetwork
            return
        }
        token = PhoneSetupProtocol.newToken()
        val server = ServerSocket(0, 4, address).apply { soTimeout = ACCEPT_TIMEOUT_MILLIS }
        socket = server
        running.set(true)
        mutableState.value = PhoneSetupState.Running(
            url = "http://${address.hostAddress}:${server.localPort}/?t=$token",
            receivedCount = 0,
            lastSourceName = null,
        )
        Thread({ serve(server) }, "sohva-phone-setup").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        mutableState.value = PhoneSetupState.Stopped
    }

    private fun serve(server: ServerSocket) {
        val startedAt = System.currentTimeMillis()
        while (running.get()) {
            if (System.currentTimeMillis() - startedAt > IDLE_LIFETIME_MILLIS) break
            val client = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            client.use { handle(it) }
        }
        if (running.get()) stop()
    }

    private fun handle(client: Socket) {
        client.soTimeout = CLIENT_TIMEOUT_MILLIS
        val request = runCatching { PhoneSetupProtocol.parseRequest(client.getInputStream()) }.getOrNull()
        val response = when {
            request == null -> html(400, page(context.getString(IptvR.string.phone_setup_page_bad_request), form = false))
            request.query["t"] != token && PhoneSetupProtocol.parseForm(request.body)["t"] != token ->
                html(403, page(context.getString(IptvR.string.phone_setup_page_forbidden), form = false))
            request.method == "GET" -> html(200, page(null, form = true))
            request.method == "POST" -> submit(request)
            else -> html(405, page(context.getString(IptvR.string.phone_setup_page_bad_request), form = false))
        }
        runCatching {
            client.getOutputStream().apply {
                write(response)
                flush()
            }
        }
    }

    private fun submit(request: PhoneSetupRequest): ByteArray {
        val submission = PhoneSetupProtocol.submissionFrom(PhoneSetupProtocol.parseForm(request.body))
        return submission.fold(
            onSuccess = { received ->
                runCatching { runBlocking { onSubmission(received) } }.fold(
                    onSuccess = {
                        val current = mutableState.value as? PhoneSetupState.Running
                        if (current != null) {
                            mutableState.value = current.copy(
                                receivedCount = current.receivedCount + 1,
                                lastSourceName = received.source?.name ?: current.lastSourceName,
                            )
                        }
                        val saved = received.source?.let { context.getString(IptvR.string.phone_setup_page_saved, it.name) }
                            ?: context.getString(IptvR.string.phone_setup_page_keys_saved)
                        html(200, page(saved, form = true))
                    },
                    onFailure = { html(500, page(context.getString(IptvR.string.phone_setup_page_failed), form = true)) },
                )
            },
            onFailure = { html(400, page(context.getString(IptvR.string.phone_setup_page_invalid), form = true)) },
        )
    }

    private fun html(status: Int, body: String): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.ISO_8859_1) + bytes
    }

    private fun page(message: String?, form: Boolean): String {
        fun s(id: Int) = context.getString(id).escape()
        val notice = message?.let { "<p class=\"notice\">${it.escape()}</p>" }.orEmpty()
        val forms = if (!form) "" else """
            <form method="post" action="/submit">
              <input type="hidden" name="t" value="${token.escape()}">
              <input type="hidden" name="type" value="xtream">
              <h2>${s(IptvR.string.phone_setup_page_xtream)}</h2>
              <label>${s(IptvR.string.phone_setup_page_name)}<input name="name" required maxlength="60"></label>
              <label>${s(IptvR.string.phone_setup_page_xtream_url)}<input name="xtream_url" type="url" required inputmode="url" autocapitalize="off"></label>
              <label>${s(IptvR.string.phone_setup_page_username)}<input name="xtream_username" required autocapitalize="off" autocomplete="off"></label>
              <label>${s(IptvR.string.phone_setup_page_password)}<input name="xtream_password" type="password" required autocomplete="off"></label>
              <button type="submit">${s(IptvR.string.phone_setup_page_send)}</button>
            </form>
            <form method="post" action="/submit">
              <input type="hidden" name="t" value="${token.escape()}">
              <input type="hidden" name="type" value="m3u">
              <h2>${s(IptvR.string.phone_setup_page_m3u)}</h2>
              <label>${s(IptvR.string.phone_setup_page_name)}<input name="name" required maxlength="60"></label>
              <label>${s(IptvR.string.phone_setup_page_m3u_url)}<input name="m3u_url" type="url" required inputmode="url" autocapitalize="off"></label>
              <label>${s(IptvR.string.phone_setup_page_xmltv_url)}<input name="xmltv_url" type="url" inputmode="url" autocapitalize="off"></label>
              <button type="submit">${s(IptvR.string.phone_setup_page_send)}</button>
            </form>
            <form method="post" action="/submit">
              <input type="hidden" name="t" value="${token.escape()}">
              <input type="hidden" name="type" value="keys">
              <h2>${s(IptvR.string.phone_setup_page_keys)}</h2>
              <p>${s(IptvR.string.phone_setup_page_keys_help)}</p>
              <label>${s(IptvR.string.phone_setup_page_tmdb)}<input name="tmdb_token" autocapitalize="off" autocomplete="off"></label>
              <label>${s(IptvR.string.phone_setup_page_api_sports)}<input name="api_sports_key" autocapitalize="off" autocomplete="off"></label>
              <button type="submit">${s(IptvR.string.phone_setup_page_send)}</button>
            </form>
        """.trimIndent()
        return """
            <!doctype html><html lang="${context.resources.configuration.locales[0].language}"><head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${s(IptvR.string.phone_setup_page_title)}</title>
            <style>
              body{font-family:system-ui,sans-serif;margin:0;padding:20px;background:#12151c;color:#f2f4f8}
              h1{font-size:1.4rem;margin:0 0 4px}h2{font-size:1.1rem;margin:22px 0 8px}
              p{color:#aab1c0;line-height:1.4}form{background:#1c2130;border-radius:12px;padding:14px;margin-top:14px}
              label{display:block;margin:10px 0 4px;color:#aab1c0;font-size:.9rem}
              input{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #2f3648;background:#0f1218;color:#fff;font-size:1rem}
              button{margin-top:14px;width:100%;padding:14px;border:0;border-radius:10px;background:#ff8a3d;color:#1a0d02;font-weight:700;font-size:1rem}
              .notice{background:#26304a;color:#fff;padding:12px;border-radius:10px}
            </style></head><body>
            <h1>${s(IptvR.string.phone_setup_page_title)}</h1>
            <p>${s(IptvR.string.phone_setup_page_intro)}</p>
            $notice
            $forms
            <p>${s(IptvR.string.phone_setup_page_privacy)}</p>
            </body></html>
        """.trimIndent()
    }

    private fun String.escape(): String = buildString(length) {
        for (char in this@escape) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    /** A site-local IPv4 address on a live interface: Wi-Fi or Ethernet, whichever the box uses. */
    private fun lanAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it.isSiteLocalAddress && it.address.size == 4 }
    }.getOrNull()

    private companion object {
        const val ACCEPT_TIMEOUT_MILLIS = 1_000
        const val CLIENT_TIMEOUT_MILLIS = 5_000
        const val IDLE_LIFETIME_MILLIS = 15L * 60 * 1_000
    }
}
