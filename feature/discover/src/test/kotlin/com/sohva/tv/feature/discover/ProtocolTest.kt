package com.sohva.tv.feature.discover

import com.sohva.tv.feature.discover.protocol.AddonEndpoint
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.protocol.AddonFailure
import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.sohva.tv.feature.discover.protocol.AddonMediaParser
import com.sohva.tv.feature.discover.protocol.AddonSourceParser
import com.sohva.tv.feature.discover.protocol.StreamKind
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Spec 50 §11 "Unit": endpoints, manifests, catalog and meta responses, streams and subtitles. */
class ProtocolTest {
    private fun json(text: String) = Buffer().writeUtf8(text)

    private fun failure(block: () -> Unit): AddonFailure = try {
        block()
        fail("expected a failure")
        error("unreachable")
    } catch (e: AddonException) {
        e.failure
    }

    // ---- Endpoints (FR-06…08) ----

    @Test
    fun endpointNormalisation() {
        fun url(input: String, base: Boolean = true) = AddonEndpoint.parse(input, base).manifestUrl.toString()
        assertEquals("https://addon.example/cfg/manifest.json", url("stremio://addon.example/cfg/manifest.json"))
        assertEquals("https://addon.example/cfg/manifest.json", url("  https://addon.example/cfg/  ", base = false))
        assertEquals(
            "https://addon.example/stremio/0f8fad5b-d9cb-469f-a165-70867728950e/manifest.json",
            url("https://addon.example/stremio/0f8fad5b-d9cb-469f-a165-70867728950e", base = false),
        )
        assertEquals("https://addon.example/cfg/manifest.json", url("https://addon.example/cfg"))
        assertEquals("https://addon.example/manifest.json?token=x%2By", url("https://addon.example/manifest.json?token=x%2By"))
        for (bad in listOf(
            "https://addon.example/configure", "https://addon.example/install", "https://addon.example/page.html",
            "https://user:pw@addon.example/manifest.json", "https://addon.example/manifest.json#frag", "https://addon.example\\x",
            "ftp://addon.example/manifest.json", "not a url",
        )) {
            assertEquals(bad, AddonFailure.INVALID_URL, failure { AddonEndpoint.parse(bad, baseAllowed = true) })
        }
        assertEquals(AddonFailure.INSECURE_URL, failure { AddonEndpoint.parse("http://addon.example/manifest.json", true) })
        assertEquals("http://addon.example/manifest.json", AddonEndpoint.parse("http://addon.example/manifest.json", true, allowHttp = true).manifestUrl.toString())
        assertEquals(AddonFailure.INVALID_URL, failure { AddonEndpoint.parse("https://addon.example/cfg", baseAllowed = false) })
    }

    @Test
    fun fingerprintsAndResourceUrls() {
        val a = AddonEndpoint.parse("https://addon.example/a%2Fb/manifest.json", true)
        val b = AddonEndpoint.parse("stremio://addon.example/a%2Fb/manifest.json", true)
        assertEquals(a.fingerprint, b.fingerprint)
        assertFalse(a.fingerprint == AddonEndpoint.parse("https://addon.example/other/manifest.json", true).fingerprint)
        assertEquals("https://addon.example/a%2Fb/meta/series/tt0000001.json", a.resource("meta", "series", "tt0000001").toString())
        assertEquals(
            "https://addon.example/a%2Fb/catalog/movie/top/genre=Sci%2FFi%20%26%20more%2B%3D&skip=40.json",
            a.resource("catalog", "movie", "top", mapOf("skip" to "40", "genre" to "Sci/Fi & more+=")).toString(),
        )
        for ((type, id) in listOf(".." to "x", "movie" to ".", "movie" to "")) {
            assertEquals(AddonFailure.INVALID_REQUEST, failure { a.resource("meta", type, id) })
        }
        assertEquals(AddonFailure.INVALID_REQUEST, failure { a.resource("script", "movie", "x") })
        assertEquals("AddonEndpoint(<redacted>)", a.toString())
    }

    // ---- Manifest (FR-09…15) ----

    private val manifest = """
        {"id":"org.example","version":"1.0.0","name":"Example","types":["movie","series","tv-show"],
         "idPrefixes":["tt"],
         "resources":["catalog","meta",{"name":"stream","types":["movie"]},{"name":"subtitles","types":["movie","series"],"idPrefixes":["kitsu"]}],
         "catalogs":[
           {"type":"movie","id":"top","name":"Top","extra":[{"name":"genre","options":["Drama","Comedy"]},{"name":"skip"}],"pageSize":50},
           {"type":"tv-show","id":"hidden","showInHome":false},
           {"type":"series","id":"legacy","extraSupported":["search","skip"],"extraRequired":["search"]},
           {"type":"movie","id":"choice","extra":[{"name":"genre","isRequired":true,"options":["A"]}]}
         ],
         "behaviorHints":{"configurable":true},"logo":"https://x","future":{"a":[1,2,{"b":null}]}}
    """.trimIndent()

    @Test
    fun manifestParsingAndCapabilities() {
        val m = AddonManifest.parse(json(manifest))
        assertEquals(listOf("top", "hidden", "legacy", "choice"), m.catalogs.map { it.id })
        assertEquals(50, m.catalog("movie", "top")!!.pageSize)
        assertEquals("hidden", m.catalog("tv-show", "hidden")!!.name)
        assertFalse(m.catalog("tv-show", "hidden")!!.showInHome)
        // Catalog ids are not media ids: prefixes never filter catalogs.
        assertTrue(m.supports("catalog", "movie", "top"))
        assertFalse(m.supports("catalog", "movie", "nope"))
        assertTrue(m.supports("meta", "series", "tt123"))
        assertFalse(m.supports("meta", "series", "kitsu:1"))
        // An object resource keeps its own types and does not inherit the manifest's prefixes.
        assertTrue(m.supports("stream", "movie", "anything"))
        assertFalse(m.supports("stream", "series", "tt1"))
        assertTrue(m.supports("subtitles", "series", "kitsu:1"))
        assertFalse(m.supports("subtitles", "series", "tt1"))
        val legacy = m.catalog("series", "legacy")!!
        assertTrue(legacy.extra("search")!!.required)
        assertTrue(legacy.searchable)
        assertFalse(legacy.onLanding)
        assertTrue(m.catalog("movie", "top")!!.onLanding)
        assertFalse(m.catalog("movie", "choice")!!.onLanding)
        m.checkExtras(m.catalog("movie", "top")!!, mapOf("genre" to "Drama", "skip" to "40"))
        assertEquals(AddonFailure.INVALID_REQUEST, failure { m.checkExtras(m.catalog("movie", "top")!!, mapOf("year" to "2020")) })
        assertEquals(AddonFailure.INVALID_REQUEST, failure { m.checkExtras(m.catalog("movie", "top")!!, mapOf("skip" to "-1")) })
        assertEquals(AddonFailure.INVALID_REQUEST, failure { m.checkExtras(m.catalog("movie", "choice")!!, emptyMap()) })
    }

    @Test
    fun malformedManifestsAndBounds() {
        for (bad in listOf(
            """{"version":"1","name":"x","types":[],"resources":[]}""",
            """{"id":"a","version":"1","name":"x","types":"movie","resources":[]}""",
            """{"id":"a","version":"1","name":" ","types":[],"resources":[]}""",
            """{"id":"a","version":"1","name":"x","types":[],"resources":[],"catalogs":[{"type":"movie","id":"a"},{"type":"movie","id":"a"}]}""",
            """{"id":"a","version":"1","name":"x","types":[],"resources":[],"catalogs":[{"type":"movie","id":"a","pageSize":0}]}""",
            """{"id":"a","version":"1","name":"x","types":[],"resources":[],"catalogs":[{"type":"movie","id":"a","extra":[{"name":"g"},{"name":"g"}]}]}""",
            """[1,2]""",
        )) {
            assertEquals(bad, AddonFailure.INVALID_MANIFEST, failure { AddonManifest.parse(json(bad)) })
        }
        val deep = "[".repeat(65) + "]".repeat(65)
        assertEquals(AddonFailure.INVALID_MANIFEST, failure { AddonManifest.parse(json(deep)) })
        val big = Buffer().writeUtf8("{\"a\":\"").writeUtf8("x".repeat(2 * 1024 * 1024)).writeUtf8("\"}")
        assertEquals(AddonFailure.RESPONSE_TOO_LARGE, failure { AddonManifest.parse(big) })
        val configured = """{"id":"a","version":"1","name":"x","types":[],"resources":[],"behaviorHints":{"configurationRequired":true}}"""
        assertTrue(AddonManifest.parse(json(configured)).configurationRequired)
    }

    // ---- Media (FR-16…20) ----

    @Test
    fun catalogEntriesAreBoundedDeduplicatedAndCounted() {
        val metas = (1..30).joinToString(",") { i -> if (i == 3) """{"type":"movie"}""" else """{"type":"movie","id":"tt$i","name":"Title $i","poster":"https://img.example/$i.jpg"}""" } +
            """,{"type":"movie","id":"tt1","name":"Duplicate"},{"type":"movie","id":"tt99","name":"Bad art","poster":"https://u:p@img.example/x.jpg","imdbRating":"7.4","releaseInfo":2024,"videos":[{"id":"x"}]}"""
        val page = AddonMediaParser.catalog(json("""{"metas":[$metas]}"""), 20)
        assertEquals(32, page.receivedCount)
        assertEquals(20, page.items.size)
        assertEquals("tt1", page.items.first().id)
        val all = AddonMediaParser.catalog(json("""{"metas":[$metas]}"""), 1_000)
        assertEquals(30, all.items.size)
        val bad = all.items.last()
        assertNull(bad.poster)
        assertEquals("7.4", bad.imdbRating)
        assertEquals("2024", bad.releaseInfo)
        val tooMany = (1..1_001).joinToString(",") { """{"type":"movie","id":"$it","name":"n"}""" }
        assertEquals(AddonFailure.RESPONSE_TOO_LARGE, failure { AddonMediaParser.catalog(json("""{"metas":[$tooMany]}"""), 20) })
        assertEquals(AddonFailure.INVALID_RESPONSE, failure { AddonMediaParser.catalog(json("""{"nothing":[]}"""), 20) })
    }

    @Test
    fun metaWithVideosCastAndPlayability() {
        val meta = """{"meta":{"type":"series","id":"tt9","name":"Show","links":[{"name":"Dee","category":"Actors","url":"https://x"}],
            "cast":["Ann",{"name":"Bob","character":"Hero"}],
            "app_extras":{"cast":[{"name":"bob","photo":"https://img.example/bob.jpg"}]},
            "videos":[{"id":"tt9:1:1","name":"Pilot","season":1,"episode":1},{"id":"tt9:1:1","name":"Dup"},{"id":"tt9:0:1","season":0,"number":1},{"season":2}]}}"""
        val d = AddonMediaParser.meta(json(meta))
        assertEquals(listOf("tt9:1:1", "tt9:0:1"), d.videos.map { it.id })
        assertEquals("Pilot", d.videos[0].title)
        assertEquals(1, d.videos[1].episode)
        // Rich extras first (photo), then cast fills the character; links last.
        assertEquals(listOf("bob", "Ann", "Dee"), d.cast.map { it.name })
        assertEquals("https://img.example/bob.jpg", d.cast[0].photo)
        assertEquals("Hero", d.cast[0].character)
        assertFalse(d.preview.singleVideo)
        val movie = AddonMediaParser.catalog(json("""{"metas":[{"type":"movie","id":"tt1","name":"M"},{"type":"series","id":"tt2","name":"S","behaviorHints":{"defaultVideoId":"tt2:1:1"}}]}"""), 20)
        assertEquals("tt1", movie.items[0].videoId)
        assertEquals("tt2:1:1", movie.items[1].videoId)
    }

    // ---- Streams and subtitles (FR-21…23) ----

    @Test
    fun streamKindsHeadersAndLocalBridge() {
        val body = """{"streams":[
            {"name":"Direct","url":"https://cdn.example/a.mp4","behaviorHints":{"proxyHeaders":{"request":{"User-Agent":"X","Referer":"https://r.example"}},"videoHash":"0123456789abcdef","videoSize":1234,"filename":"a.mkv"}},
            {"name":"Torrent","infoHash":"abc"},
            {"name":"Web","externalUrl":"https://web.example"},
            {"name":"Yt","ytId":"x"},
            {"name":"Local","url":"http://127.0.0.1:11470/x"},
            {"name":"BadHeader","url":"https://cdn.example/b.mp4","behaviorHints":{"proxyHeaders":{"request":{"Host":"evil"}}}},
            {"name":"BadContainer","url":"https://cdn.example/c.mp4","behaviorHints":{"proxyHeaders":"nope"}},
            {"title":"Untitled","url":"https://cdn.example/d.mp4","subtitles":[{"id":"1","lang":"fin","url":"https://subs.example/1.srt"},{"id":"1","lang":"fin","url":"https://subs.example/1.srt"},{"lang":"x"}]},
            {"description":"nothing playable"}
        ]}"""
        val streams = AddonSourceParser.streams(json(body))
        assertEquals(listOf("Direct", "Torrent", "Web", "Yt", "Local", "Stream"), streams.map { it.name })
        assertEquals(listOf(StreamKind.HTTP, StreamKind.TORRENT, StreamKind.EXTERNAL, StreamKind.UNSUPPORTED, StreamKind.UNSUPPORTED, StreamKind.HTTP), streams.map { it.kind })
        val direct = streams[0]
        assertEquals(mapOf("User-Agent" to "X", "Referer" to "https://r.example"), direct.headers)
        assertEquals("0123456789abcdef", direct.videoHash)
        assertEquals(1234L, direct.videoSize)
        assertEquals("a.mkv", direct.filename)
        assertNull(streams[4].url)
        assertEquals("Untitled", streams[5].description)
        assertEquals(1, streams[5].subtitles.size)
        assertEquals("AddonStream(HTTP)", direct.toString())
        val subs = AddonSourceParser.subtitles(json("""{"subtitles":[{"id":"a","lang":"eng","url":"https://s.example/a.vtt"},{"id":"b","lang":"eng","url":"https://localhost/b"}]}"""))
        assertEquals(listOf("a"), subs.map { it.id })
        val tooMany = (1..501).joinToString(",") { """{"url":"https://cdn.example/$it"}""" }
        assertEquals(AddonFailure.RESPONSE_TOO_LARGE, failure { AddonSourceParser.streams(json("""{"streams":[$tooMany]}""")) })
    }
}
