package com.sohva.tv.core.net.phone

import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The phone page as the TV shows it (spec 11 PHONE-FR-13). */
sealed interface PhoneState {
    data object Stopped : PhoneState

    /** No site-local IPv4 address to serve on. */
    data object NoNetwork : PhoneState

    /** [url] carries the token in its fragment; show it only on the TV screen. [logoSaved]: a logo arrived (logo mode). */
    data class Running(
        val url: String,
        val received: Int = 0,
        val lastSource: String? = null,
        val lastWasKeys: Boolean = false,
        val logoSaved: Boolean = false,
    ) : PhoneState {
        override fun toString(): String = "Running(received=$received)"
    }
}

/** What the page offers: the Sources forms, or one picture for one channel's logo (spec 21 CHAN-FR-40). */
sealed interface PhoneMode {
    data object Sources : PhoneMode

    class Logo(val channelKey: String, val channelName: String) : PhoneMode {
        override fun toString(): String = "Logo"
    }

    /**
     * Discover's addon list (spec 50 §4.7.1): a plain-text body that [accepts] must pass; the first
     * valid one ends the session. 10 minutes at most.
     */
    class Addons(val accepts: (ByteArray) -> Boolean) : PhoneMode {
        override fun toString(): String = "Addons"
    }

    /**
     * A Trakt list's address or number for Home (spec 02 HOME-FR-100): a short plain-text body that
     * [accepts] must pass. The page stays open, so several lists can be sent one after another.
     */
    class TraktList(val accepts: (String) -> Boolean) : PhoneMode {
        override fun toString(): String = "TraktList"
    }
}

/** The sentences the page answers with, in the TV's interface language (PHONE-FR-32). */
interface PhoneAnswers {
    fun page(): PhonePageTexts

    fun saved(sourceName: String): String

    fun keysSaved(): String

    fun invalid(): String

    fun failed(): String

    fun forbidden(): String

    fun badRequest(): String

    /** The logo page's texts for [channelName] (CHAN-FR-41). */
    fun logoPage(channelName: String): LogoPageTexts

    fun logoSaved(channelName: String): String

    /** The Trakt list page's texts (HOME-FR-100). */
    fun traktListPage(): TraktListPageTexts = TraktListPageTexts(
        "en", "Send a Trakt list to your TV", "Paste a list's trakt.tv address, or its number.", "List address",
        "Send to the TV", "Nothing you type leaves your home network.",
    )

    fun traktListAdded(name: String): String = "Added to your TV's Home: $name."

    fun traktListNotFound(): String = "That list could not be added. It may be private, or Home already has 8 added rows."

    fun traktListInvalid(): String = "That is not a trakt.tv list address or number."
}

/** Saves what the phone sent; called on the server thread, blocking until done (PHONE-FR-31). */
fun interface PhoneReceiver {
    /** True when saved; false when saving failed (secret store, the source limit, a key too long). */
    fun receive(submission: PhoneSubmission): Boolean
}

/**
 * The phone setup server, Sources mode (spec 11 §4.1–4.3, §4.7) with the hardening the rebuild
 * gives it (PHONE-FR-24, plan/09 M1): bound to the TV's own home-network address on a random port,
 * a 128-bit token in the address fragment checked in constant time from a bearer header, the exact
 * `Host`, the `Origin` when present, only `GET /` and `POST /submit`, a 10-second budget per request,
 * no-store and a strict Content-Security-Policy. One daemon thread, one connection at a time, 15
 * minutes at most. Nothing it reads is logged (PHONE-FR-73).
 */
class PhoneServer(
    private val answers: PhoneAnswers,
    private val receiver: PhoneReceiver,
    private val lifetimeMillis: Long = LIFETIME_MILLIS,
) {
    private val stateFlow = MutableStateFlow<PhoneState>(PhoneState.Stopped)
    val state: StateFlow<PhoneState> = stateFlow.asStateFlow()

    @Volatile private var socket: ServerSocket? = null

    @Volatile private var mode: PhoneMode = PhoneMode.Sources

    private fun sameMode(other: PhoneMode): Boolean {
        val current = mode
        return when {
            current is PhoneMode.Logo && other is PhoneMode.Logo -> current.channelKey == other.channelKey
            else -> current::class == other::class
        }
    }

    /**
     * Starts serving [mode] on [address]; a running page in the same mode keeps running (PHONE-FR-05),
     * another mode restarts it. Call off the main thread.
     */
    @Synchronized
    fun start(address: Inet4Address?, mode: PhoneMode = PhoneMode.Sources) {
        if (stateFlow.value is PhoneState.Running && sameMode(mode)) return
        stop()
        this.mode = mode
        if (address == null) {
            stateFlow.value = PhoneState.NoNetwork
            return
        }
        val server = ServerSocket(0, BACKLOG, address).apply { soTimeout = ACCEPT_TIMEOUT_MS }
        val token = newToken()
        val host = "${address.hostAddress}:${server.localPort}"
        socket = server
        stateFlow.value = PhoneState.Running("http://$host/#$token")
        val session = Session(server, host, token, mode)
        thread(isDaemon = true, name = "sohva-phone-setup") { session.serve() }
    }

    /** Idempotent (PHONE-FR-16). */
    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        stateFlow.value = PhoneState.Stopped
    }

    private inner class Session(private val server: ServerSocket, private val host: String, token: String, private val mode: PhoneMode) {
        private val bearer = "Bearer $token".toByteArray(Charsets.US_ASCII)
        private val origin = "http://$host"
        private val saved = HashSet<PhoneSubmission>()

        fun serve() {
            val end = System.nanoTime() + (if (mode is PhoneMode.Addons) minOf(lifetimeMillis, ADDON_LIFETIME_MILLIS) else lifetimeMillis) * 1_000_000
            try {
                while (!server.isClosed && System.nanoTime() < end) {
                    val client = try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    client.use(::handle)
                }
            } catch (_: IOException) {
                // The socket closed: stopped from the TV.
            } finally {
                synchronized(this@PhoneServer) { if (socket === server) stop() }
            }
        }

        private fun handle(client: Socket) {
            client.soTimeout = READ_TIMEOUT_MS
            val out = client.getOutputStream()
            val request = try {
                val max = when (mode) {
                    is PhoneMode.Logo -> MAX_LOGO_BODY
                    is PhoneMode.Addons -> MAX_ADDON_BODY
                    is PhoneMode.TraktList -> MAX_LIST_BODY
                    PhoneMode.Sources -> MAX_BODY
                }
                PhoneRequest.read(client.getInputStream(), max, System.nanoTime() + REQUEST_BUDGET_NANOS)
            } catch (_: IOException) {
                return respond(out, 400, answers.badRequest())
            }
            if (request.headers["host"] != host) return respond(out, 403, answers.forbidden())
            when {
                request.path == "/" && request.method == "GET" -> respond(out, 200, page(), html = true)
                request.path == "/submit" && request.method == "POST" -> submit(out, request)
                request.path == "/" || request.path == "/submit" -> respond(out, 405, answers.badRequest())
                else -> respond(out, 404, answers.badRequest())
            }
        }

        private fun submit(out: OutputStream, request: PhoneRequest) {
            if (mode is PhoneMode.Addons) return addons(out, request, mode)
            if (mode is PhoneMode.TraktList) return traktList(out, request, mode)
            val authorization = request.headers["authorization"].orEmpty().toByteArray(Charsets.US_ASCII)
            val originOk = request.headers["origin"]?.let { it == origin } ?: true
            if (!originOk || !MessageDigest.isEqual(authorization, bearer)) return respond(out, 403, answers.forbidden())
            val type = request.headers["content-type"].orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
            if (type != FORM) return respond(out, 400, answers.badRequest())
            val submission = PhoneSubmission.parse(PhoneRequest.form(request.body)) ?: return respond(out, 400, answers.invalid())
            // Each mode takes only its own kind: a logo only for the channel the page was opened for (CHAN-FR-42).
            val fits = when (mode) {
                PhoneMode.Sources -> submission !is PhoneSubmission.Logo
                is PhoneMode.Logo -> submission is PhoneSubmission.Logo && submission.channelKey == mode.channelKey
                is PhoneMode.Addons, is PhoneMode.TraktList -> false
            }
            if (!fits) return respond(out, 403, answers.forbidden())
            // Sending the same thing twice in a session saves it once (PHONE-FR-31 rebuild rule).
            if (submission !in saved) {
                if (!receiver.receive(submission)) return respond(out, 500, answers.failed())
                saved += submission
                stateFlow.update { s ->
                    if (s !is PhoneState.Running) {
                        s
                    } else {
                        when (submission) {
                            is PhoneSubmission.NewSource -> s.copy(received = s.received + 1, lastSource = submission.config.source.name, lastWasKeys = false)
                            is PhoneSubmission.Keys -> s.copy(received = s.received + 1, lastWasKeys = true)
                            is PhoneSubmission.Logo -> s.copy(received = s.received + 1, logoSaved = true)
                            is PhoneSubmission.AddonList, is PhoneSubmission.TraktList -> s
                        }
                    }
                }
            }
            val answer = when (submission) {
                is PhoneSubmission.NewSource -> answers.saved(submission.config.source.name)
                is PhoneSubmission.Keys -> answers.keysSaved()
                is PhoneSubmission.Logo -> answers.logoSaved((mode as PhoneMode.Logo).channelName)
                is PhoneSubmission.AddonList, is PhoneSubmission.TraktList -> answers.failed()
            }
            respond(out, 200, answer)
        }

        /**
         * ADDON-FR-45: `Origin` required and exact, the bearer token, `text/plain`, 1–262,144 bytes
         * that pass the list rules. The first valid list ends the session; invalid ones do not.
         */
        private fun addons(out: OutputStream, request: PhoneRequest, mode: PhoneMode.Addons) {
            val authorization = request.headers["authorization"].orEmpty().toByteArray(Charsets.US_ASCII)
            if (request.headers["origin"] != origin || !MessageDigest.isEqual(authorization, bearer)) return respond(out, 403, answers.forbidden())
            val type = request.headers["content-type"].orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
            if (type != TEXT || request.raw.isEmpty() || !mode.accepts(request.raw)) return respond(out, 400, PhonePage.ADDONS_INVALID)
            if (!receiver.receive(PhoneSubmission.AddonList(request.raw))) return respond(out, 500, answers.failed())
            respond(out, 200, PhonePage.ADDONS_SENT)
            synchronized(this@PhoneServer) { if (socket === server) stop() }
        }

        /**
         * HOME-FR-100: as the addon list's rules (exact `Origin`, the bearer token, `text/plain`), a body
         * of 1–1,024 bytes that [PhoneMode.TraktList.accepts] passes. The receiver adds the list and the
         * page says so, or says why not; the session stays open for another list.
         */
        private fun traktList(out: OutputStream, request: PhoneRequest, mode: PhoneMode.TraktList) {
            val authorization = request.headers["authorization"].orEmpty().toByteArray(Charsets.US_ASCII)
            if (request.headers["origin"] != origin || !MessageDigest.isEqual(authorization, bearer)) return respond(out, 403, answers.forbidden())
            val type = request.headers["content-type"].orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
            val text = request.raw.toString(Charsets.UTF_8).trim()
            if (type != TEXT || text.isEmpty() || !mode.accepts(text)) return respond(out, 400, answers.traktListInvalid())
            val added = TraktListAdded()
            if (!receiver.receive(PhoneSubmission.TraktList(text, added))) return respond(out, 400, answers.traktListNotFound())
            stateFlow.update { s -> if (s is PhoneState.Running) s.copy(received = s.received + 1, lastSource = added.name) else s }
            respond(out, 200, answers.traktListAdded(added.name.orEmpty()))
        }

        private fun page(): String = when (val m = mode) {
            PhoneMode.Sources -> PhonePage.sources(answers.page())
            is PhoneMode.Logo -> PhonePage.logo(answers.logoPage(m.channelName), m.channelKey)
            is PhoneMode.Addons -> PhonePage.addons()
            is PhoneMode.TraktList -> PhonePage.traktList(answers.traktListPage())
        }

        private fun respond(out: OutputStream, code: Int, body: String, html: Boolean = false) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ").append(code).append(' ').append(REASONS[code] ?: "Error").append("\r\n")
                append("Content-Type: ").append(if (html) "text/html" else "text/plain").append("; charset=utf-8\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n")
                append("Content-Security-Policy: ").append(PhonePage.contentSecurityPolicy).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            runCatching {
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(bytes)
                out.flush()
            }
        }
    }

    companion object {
        const val LIFETIME_MILLIS: Long = 15 * 60 * 1_000L
        const val MAX_BODY: Int = 16_384

        /** A picture posted as a PNG data URL, already shrunk by the phone (CHAN-FR-42). */
        const val MAX_LOGO_BODY: Int = 1_500_000

        /** Spec 50 ADDON-FR-34, -45: a list of at most 256 KiB, and a session of at most 10 minutes. */
        const val MAX_ADDON_BODY: Int = 262_144

        /** A Trakt list address is short (HOME-FR-100). */
        const val MAX_LIST_BODY: Int = 1_024

        const val ADDON_LIFETIME_MILLIS: Long = 10 * 60 * 1_000L
        private const val TEXT = "text/plain"
        private const val BACKLOG = 4
        private const val ACCEPT_TIMEOUT_MS = 1_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val REQUEST_BUDGET_NANOS = 10_000_000_000L
        private const val FORM = "application/x-www-form-urlencoded"
        private val REASONS = mapOf(200 to "OK", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed")
        private val random = SecureRandom()

        /** 128 bits as 32 lower-case hex characters (plan/09 M1). */
        fun newToken(): String {
            val bytes = ByteArray(16).also(random::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
