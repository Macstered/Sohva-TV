package com.sohva.tv.core.net.phone

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.Locale

/** One HTTP request as the phone page server reads it (spec 11 PHONE-FR-20, -24, -61). */
class PhoneRequest(
    val method: String,
    val path: String,
    /** Header names lower-cased; each name at most once. */
    val headers: Map<String, String>,
    val body: String,
) {
    /** Never the body or the headers, which carry the token and credentials (PHONE-FR-73). */
    override fun toString(): String = "PhoneRequest($method $path, ${body.length} chars)"

    /** Why a request was refused before routing. */
    enum class Problem { MALFORMED, TOO_LARGE, TIMED_OUT }

    class Refused(val problem: Problem) : IOException(problem.name)

    companion object {
        const val MAX_LINE_BYTES: Int = 8 * 1024
        const val MAX_HEADERS: Int = 64

        /**
         * Reads a request from [input]: the request line and headers byte by byte (each line at most
         * [MAX_LINE_BYTES], at most [MAX_HEADERS] headers, no repeated names, no `Transfer-Encoding`),
         * then exactly `Content-Length` bytes of body, refused from the headers alone when above
         * [maxBody]. [deadline] (monotonic nanos) bounds the whole request; the socket's read timeout
         * bounds each read.
         */
        fun read(input: InputStream, maxBody: Int, deadline: Long, now: () -> Long = System::nanoTime): PhoneRequest {
            val requestLine = readLine(input, deadline, now) ?: throw Refused(Problem.MALFORMED)
            val parts = requestLine.split(' ')
            if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) throw Refused(Problem.MALFORMED)
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input, deadline, now) ?: throw Refused(Problem.MALFORMED)
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0 || headers.size >= MAX_HEADERS) throw Refused(Problem.MALFORMED)
                val name = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                if (headers.put(name, line.substring(colon + 1).trim()) != null) throw Refused(Problem.MALFORMED)
            }
            if ("transfer-encoding" in headers) throw Refused(Problem.MALFORMED)
            val length = headers["content-length"]?.let { it.toIntOrNull() ?: throw Refused(Problem.MALFORMED) } ?: 0
            if (length < 0) throw Refused(Problem.MALFORMED)
            if (length > maxBody) throw Refused(Problem.TOO_LARGE)
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                if (now() > deadline) throw Refused(Problem.TIMED_OUT)
                val n = input.read(body, read, length - read)
                if (n < 0) throw Refused(Problem.MALFORMED)
                read += n
            }
            val target = parts[1]
            return PhoneRequest(parts[0].uppercase(Locale.ROOT), target.substringBefore('?'), headers, String(body, Charsets.UTF_8))
        }

        /** A CRLF- or LF-terminated line as ISO-8859-1; null at a premature end of stream. */
        private fun readLine(input: InputStream, deadline: Long, now: () -> Long): String? {
            val line = ByteArrayOutputStream()
            while (true) {
                if (now() > deadline) throw Refused(Problem.TIMED_OUT)
                val b = input.read()
                if (b < 0) return null
                if (b == '\n'.code) break
                if (line.size() >= MAX_LINE_BYTES) throw Refused(Problem.MALFORMED)
                line.write(b)
            }
            val text = line.toString(Charsets.ISO_8859_1.name())
            return text.removeSuffix("\r")
        }

        /** `a=b&c=d`, empty pairs skipped, URL-decoded as UTF-8 (raw when that fails), a later duplicate wins (PHONE-FR-21). */
        fun form(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            for (pair in text.split('&')) {
                if (pair.isEmpty()) continue
                val name = decode(pair.substringBefore('='))
                out[name] = decode(pair.substringAfter('=', ""))
            }
            return out
        }

        private fun decode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }
    }
}
