package com.sohva.tv.addons

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single-use local transfer. No addon calls, saved configuration, external assets or logs. */
class AddonPhoneSession(private val address: InetAddress, private val lifetimeMillis: Long = 600_000) : AutoCloseable {
    init { require(address is java.net.Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress)); require(lifetimeMillis in 1..600_000) }
    private val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = ServerSocket(0, 4, address).apply { soTimeout = 500 }
    private val active = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    @Volatile private var currentClient: Socket? = null
    private val expiry = java.util.Timer("sohva-addon-phone-expiry", true)
    private val received = MutableStateFlow<String?>(null)
    val submission = received.asStateFlow()
    private val open = MutableStateFlow(true)
    val running = open.asStateFlow()
    val origin = "http://${address.hostAddress}:${server.localPort}"
    // Fragment is not sent in HTTP requests/referrers or used as a configured addon URL.
    val pairingUrl = "$origin/#$token"
    init {
        expiry.schedule(object : java.util.TimerTask() { override fun run() { close() } }, lifetimeMillis)
        Thread({ serve() }, "sohva-addon-phone").apply { isDaemon = true; start() }
    }
    override fun toString() = "AddonPhoneSession([redacted])"
    fun takeSubmission(): String? = received.value.also { received.value = null }
    override fun close() {
        closed.set(true); active.set(false); open.value = false; expiry.cancel()
        runCatching { server.close() }; runCatching { currentClient?.close() }; received.value = null
    }
    private fun serve() {
        val until = System.nanoTime() + lifetimeMillis * 1_000_000
        try {
            while (active.get() && System.nanoTime() < until) {
                val client = try { server.accept() } catch (_: SocketTimeoutException) { continue } catch (_: Exception) { break }
                currentClient = client
                client.use { socket -> runCatching { handle(socket) } }
                currentClient = null
                if (received.value != null) break
            }
        } finally { active.set(false); open.value = false; expiry.cancel(); runCatching { server.close() } }
    }
    private fun handle(socket: Socket) {
        socket.soTimeout = 5_000
        val requestDeadline = System.nanoTime() + 10_000_000_000L
        val stream = socket.getInputStream()
        val headers = ByteArrayOutputStream()
        var end = false
        while (headers.size() < 8192) {
            if (System.nanoTime() > requestDeadline || closed.get()) return
            val byte = stream.read()
            if (byte < 0) break
            headers.write(byte)
            if (byte == 10 && headers.toString("ISO-8859-1").endsWith("\r\n\r\n")) { end = true; break }
        }
        if (!end) { respond(socket, 400, "Invalid request"); return }
        val lines = headers.toString("ISO-8859-1").split("\r\n")
        val fields = mutableMapOf<String, String>()
        for (line in lines.drop(1).filter { it.isNotEmpty() }) {
            val name = line.substringBefore(':').lowercase()
            if (':' !in line || fields.put(name, line.substringAfter(':').trim()) != null) { respond(socket, 400, "Invalid request"); return }
        }
        if (fields["host"] != origin.removePrefix("http://") || "transfer-encoding" in fields) { respond(socket, 403, "Request refused"); return }
        when (lines.first()) {
            "GET / HTTP/1.1" -> respond(socket, 200, PAGE, html = true)
            "POST /submit HTTP/1.1" -> {
                val bearer = fields["authorization"].orEmpty().removePrefix("Bearer ")
                if (!active.get() || fields["origin"] != origin || !MessageDigest.isEqual(bearer.toByteArray(), token.toByteArray())) {
                    respond(socket, 403, "Pairing expired or refused"); return
                }
                val size = fields["content-length"]?.toIntOrNull()
                if (size == null || size !in 1..AddonImportText.MAX_BYTES || fields["content-type"]?.substringBefore(';') != "text/plain") {
                    respond(socket, 400, "Use a text list up to 256 KiB"); return
                }
                val body = ByteArray(size)
                var offset = 0
                while (offset < size) {
                    if (System.nanoTime() > requestDeadline || closed.get()) return
                    val count = stream.read(body, offset, size - offset); if (count <= 0) return; offset += count
                }
                val text = try { AddonImportText.read(body.inputStream()) } catch (_: Exception) { respond(socket, 400, "Invalid UTF-8 URL list (maximum 32 lines)"); return }
                if (!active.compareAndSet(true, false)) return
                respond(socket, 200, "Sent. Review and confirm on your TV.")
                if (!closed.get()) received.value = text
            }
            else -> respond(socket, 405, "Request refused")
        }
    }
    private fun respond(socket: Socket, status: Int, body: String, html: Boolean = false) {
        val bytes = body.toByteArray()
        val hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(SCRIPT.toByteArray()))
        val header = "HTTP/1.1 $status Response\r\nContent-Type: ${if (html) "text/html" else "text/plain"}; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'sha256-$hash'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }
    companion object {
        // Standard file input works over local HTTP too. No cloud upload, filename transmission,
        // permissive UTF-8 decoding, or automatic submission when a file is selected.
        private val SCRIPT = """
            let token = location.hash.slice(1);
            history.replaceState(null, '', '/');
            const form = document.querySelector('form'), input = document.querySelector('textarea');
            const file = document.querySelector('#file'), send = document.querySelector('#send');
            const clear = document.querySelector('#clear'), message = document.querySelector('#message');
            const MAX_BYTES = 262144, MAX_ENTRIES = 32;
            let reader = null, reading = false, sending = false, generation = 0;
            function update() {
                input.disabled = reading || sending || !token;
                file.disabled = sending || !token;
                clear.disabled = sending || !token;
                send.disabled = reading || sending || !token || !input.value.trim();
            }
            function listError(value) {
                if (new TextEncoder().encode(value).length > MAX_BYTES) return 'Use a text list up to 256 KiB.';
                const count = value.split(/\r\n|\r|\n/).filter(line => line.trim()).length;
                if (value.includes('\u0000') || count < 1 || count > MAX_ENTRIES) return 'Use 1 to 32 configured URLs, one per line.';
                return '';
            }
            function resetInput() {
                generation++;
                if (reader) reader.abort();
                reader = null; reading = false;
                input.value = ''; file.value = '';
            }
            file.onchange = () => {
                const selected = file.files[0];
                if (!selected) return;
                resetInput();
                if (!selected.size || selected.size > MAX_BYTES) {
                    message.textContent = 'Choose a non-empty UTF-8 text file up to 256 KiB.';
                    update(); return;
                }
                const current = generation;
                const pending = new FileReader();
                reader = pending; reading = true;
                message.textContent = 'Reading file on this phone...'; update();
                pending.onload = () => {
                    if (current !== generation) return;
                    try {
                        const value = new TextDecoder('utf-8', {fatal: true}).decode(pending.result);
                        const error = listError(value);
                        if (error) message.textContent = error;
                        else {
                            input.value = value;
                            const count = value.split(/\r\n|\r|\n/).filter(line => line.trim()).length;
                            message.textContent = count + ' addon URL(s) loaded. Tap Send to TV for review.';
                        }
                    } catch (_) { message.textContent = 'Could not read a UTF-8 text list. Choose a .txt file, not JSON or a backup.'; }
                    reader = null; reading = false; update();
                };
                pending.onerror = () => {
                    if (current !== generation) return;
                    reader = null; reading = false;
                    message.textContent = 'Could not read this file. Choose it again or paste the URLs.'; update();
                };
                pending.readAsArrayBuffer(selected);
            };
            input.oninput = () => { message.textContent = ''; update(); };
            clear.onclick = () => { resetInput(); message.textContent = ''; update(); };
            form.onsubmit = async event => {
                event.preventDefault();
                if (reading || sending || !token) return;
                const value = input.value.replace(/^\uFEFF/, '');
                const error = listError(value);
                if (error) { message.textContent = error; return; }
                sending = true; resetInput(); update(); message.textContent = 'Sending to TV...';
                try {
                    const response = await fetch('/submit', {method: 'POST', headers: {
                        'Authorization': 'Bearer ' + token, 'Content-Type': 'text/plain'
                    }, body: value});
                    message.textContent = await response.text();
                    if (response.ok) { token = ''; form.remove(); }
                } catch (_) { message.textContent = 'Connection closed. Check the TV or start a new session.'; }
                sending = false; update();
            };
            window.addEventListener('pagehide', () => { token = ''; resetInput(); update(); });
            if (!token) message.textContent = 'Scan the QR code on your TV to start a new session.';
            update();
        """.trimIndent()
        val PAGE = """<!doctype html>
            <html lang="en"><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8">
            <title>Sohva TV addon setup</title><style>
            *{box-sizing:border-box}body{font:17px system-ui;background:#101827;color:#eee;max-width:640px;margin:24px auto;padding:20px}
            h1{font-size:28px}p{line-height:1.5;color:#cad3df}form{background:#1b273a;border:1px solid #34445d;border-radius:16px;padding:20px}
            label{display:block;font-weight:600}input,textarea,button{font:inherit}input[type=file]{display:block;width:100%;margin:12px 0 24px}
            textarea{display:block;width:100%;height:160px;margin-top:12px;padding:12px;background:#101827;color:#eee;border:1px solid #63738c;border-radius:8px;-webkit-text-security:disc}
            button{padding:14px 18px;margin-top:16px;border:1px solid #72849f;border-radius:10px;background:#273951;color:#fff}
            #send{background:#2859a6;border-color:#6b9ce8}button:disabled{opacity:.5}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:3px solid #80bdff;outline-offset:3px}
            .hint{font-size:14px}#message{min-height:3em}</style></head><body>
            <h1>Send addons to Sohva TV</h1>
            <p>Trusted Wi-Fi only: this local HTTP connection is not encrypted. Configured URLs may contain credentials. Nothing is installed until you confirm on the TV.</p>
            <form>
            <label for="file">Choose a text file on this phone</label>
            <p class="hint">UTF-8 .txt, one configured URL per line. Up to 32 addons and 256 KiB. Selecting a file replaces the list below; it does not send it.</p>
            <input id="file" type="file" accept=".txt,text/plain" aria-describedby="message">
            <label for="urls">Or paste your configured URLs</label>
            <textarea id="urls" autocomplete="off" autocapitalize="off" spellcheck="false" required maxlength="262144" aria-describedby="message"></textarea>
            <p class="hint">URLs stay masked. Only this list is sent directly to your TV, never to a separate website.</p>
            <button id="send" type="submit" disabled>Send to TV for review</button> <button id="clear" type="button">Clear list</button>
            </form><p id="message" role="status" aria-live="polite"></p><script>$SCRIPT</script></body></html>
        """.trimIndent()
    }
}
