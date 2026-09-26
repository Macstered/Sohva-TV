package com.sohva.tv.core.net.update

import com.squareup.moshi.JsonReader
import com.sohva.tv.core.model.update.Release
import com.sohva.tv.core.model.update.ReleaseAsset
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.buffer
import okio.sink

/** Why a feed request failed; the updater maps it to its phase (spec 72 §4.2, §4.3). */
class UpdateHttpException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The update feed's HTTP (spec 72 §7.1, ABOUT-NFR-02, -06): the app's shared client, no own pool.
 * The release list is read with a pull parser; the APK is streamed to a file through a 64 KiB
 * buffer that also feeds the SHA-256 digest, never held in memory. Cancelling the caller cancels
 * the call.
 */
class UpdateHttp(private val client: OkHttpClient) {
    /** The release list (§7.1): newest first, unknown fields skipped, incomplete entries dropped. */
    suspend fun releases(url: HttpUrl): List<Release> = call(url, ACCEPT_GITHUB) { response ->
        val body = bounded(response, MAX_FEED)
        JsonReader.of(body).use(::readReleases)
    }

    /** A small text file such as `SHA256SUMS.txt`, refused past [max] bytes. */
    suspend fun text(url: HttpUrl, max: Long = MAX_TEXT): String = call(url, null) { bounded(it, max).readUtf8() }

    /** A small file (the install profile, ≤ 1 MiB, ABOUT-FR-10) whole, refused past [max] bytes. */
    suspend fun bytes(url: HttpUrl, max: Long): ByteArray = call(url, null) { response ->
        val length = response.body.contentLength()
        if (length > max) throw UpdateHttpException("too large")
        bounded(response, max).readByteArray()
    }

    /**
     * Streams [url] into [target] and returns the lower-case hex SHA-256 of the bytes written
     * (ABOUT-FR-09 steps 3–4). [progress] gets whole percents, only when they change; [expected]
     * is the release list's size, used when the server sends no length.
     */
    suspend fun download(url: HttpUrl, target: File, expected: Long, progress: (Int) -> Unit): String = call(url, null) { response ->
        val total = response.body.contentLength().takeIf { it > 0 } ?: expected
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK)
        var received = 0L
        var percent = -1
        response.body.byteStream().use { input ->
            target.sink().buffer().use { out ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                    received += n
                    val now = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0
                    if (now != percent) {
                        percent = now
                        progress(now)
                    }
                }
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> call(url: HttpUrl, accept: String?, read: (Response) -> T): T = coroutineScope {
        val request = Request.Builder().url(url).get().apply { if (accept != null) header("Accept", accept) }.build()
        val call: Call = client.newCall(request)
        val canceller = launch(Dispatchers.Unconfined) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw UpdateHttpException("HTTP ${response.code}")
                read(response)
            }
        } catch (e: IOException) {
            ensureActive()
            // The cause's own words go to diagnostics (redacted there): "unexpected end of stream" says more than a class name.
            throw e as? UpdateHttpException ?: UpdateHttpException("${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            canceller.cancel()
        }
    }

    private fun bounded(response: Response, max: Long): Buffer {
        val source = response.body.source()
        val buffer = Buffer()
        while (buffer.size <= max) {
            if (source.read(buffer, 8_192) == -1L) break
        }
        if (buffer.size > max) throw UpdateHttpException("too large")
        return buffer
    }

    private fun readReleases(reader: JsonReader): List<Release> {
        val out = ArrayList<Release>()
        reader.beginArray()
        while (reader.hasNext()) readRelease(reader)?.let(out::add)
        reader.endArray()
        return out
    }

    private fun readRelease(reader: JsonReader): Release? {
        var tag: String? = null
        var body = ""
        var draft = false
        var prerelease = false
        var assets: List<ReleaseAsset> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tag_name" -> tag = string(reader)
                "body" -> body = string(reader).orEmpty()
                "draft" -> draft = boolean(reader)
                "prerelease" -> prerelease = boolean(reader)
                "assets" -> assets = readAssets(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return tag?.takeIf { it.isNotBlank() }?.let { Release(it, body, draft, prerelease, assets) }
    }

    private fun readAssets(reader: JsonReader): List<ReleaseAsset> {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            reader.skipValue()
            return emptyList()
        }
        val out = ArrayList<ReleaseAsset>()
        reader.beginArray()
        while (reader.hasNext()) {
            var name: String? = null
            var url: String? = null
            var size = 0L
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = string(reader)
                    "browser_download_url" -> url = string(reader)
                    "size" -> size = if (reader.peek() == JsonReader.Token.NUMBER) reader.nextLong() else 0L.also { reader.skipValue() }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (!name.isNullOrBlank() && !url.isNullOrBlank()) out += ReleaseAsset(name, url, size)
        }
        reader.endArray()
        return out
    }

    private fun string(reader: JsonReader): String? = if (reader.peek() == JsonReader.Token.STRING) reader.nextString() else null.also { reader.skipValue() }

    private fun boolean(reader: JsonReader): Boolean = if (reader.peek() == JsonReader.Token.BOOLEAN) reader.nextBoolean() else false.also { reader.skipValue() }

    companion object {
        /** The public feed (§7.1): the ten newest releases. */
        const val FEED: String = "https://api.github.com/repos/Macstered/Sohva-TV/releases?per_page=10"
        private const val ACCEPT_GITHUB = "application/vnd.github+json"
        private const val CHUNK = 64 * 1024

        /** About 90 KB for ten releases today; a list past 4 MiB is refused. */
        private const val MAX_FEED = 4L * 1024 * 1024
        private const val MAX_TEXT = 64L * 1024
        const val MAX_PROFILE: Long = 1_048_576
    }
}
