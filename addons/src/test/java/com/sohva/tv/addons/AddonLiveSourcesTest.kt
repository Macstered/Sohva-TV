package com.sohva.tv.addons

import com.sohva.tv.addons.storage.EncryptedAddonStore
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/** Live resource GETs only: never opens a returned video/subtitle URL or prints media/provider values. */
class AddonLiveSourcesTest {
    @Test fun resolveConfiguredMovieAndEpisode(): Unit = runBlocking {
        val path = System.getProperty("sohva.addon.liveInput").orEmpty()
        assumeTrue(path.isNotEmpty())
        val file = File(path)
        val output = mutableListOf<String>()
        var stage = "import"
        try {
            val client = AddonClient(timeoutMillis = 30_000)
            val store = EncryptedAddonStore(MemoryAddonPersistence(), TestAddonCipher())
            val access = AddonManagementAccess { true }
            val importer = AddonBatchImport(client, store, access)
            val preview = importer.preview("source-probe", file.readText())
            check(importer.commit(preview).none { it.status == AddonImportStatus.FAILED })
            val installed = store.list("source-probe")
            val metadataProvider = installed.first()
            val browser = AddonBrowseRepository(client, store, MemoryResponseCache(), access)
            val sources = AddonSourceRepository(client, store, access)
            for (type in listOf("movie", "series")) {
                stage = "$type.catalog"
                val catalog = metadataProvider.manifest.catalogs.first { it.type == type && it.extras.none { it.required && it.name != "skip" } }
                val media = browser.catalog("source-probe", metadataProvider.installationId, type, catalog.id).value.items.first()
                stage = "$type.meta"
                val details = browser.metadata("source-probe", metadataProvider.installationId, media.key).value
                val videoId = if (type == "series") details.videos.first { it.season != 0 }.id else details.defaultVideoId ?: details.key.id
                val video = AddonMediaKey(details.key.type, videoId)
                stage = "$type.streams"
                val start = System.nanoTime()
                val streams = sources.streams("source-probe", video).toList().filter { it.status != AddonSourceStatus.LOADING }
                output += "$type.stream.elapsedMs=${(System.nanoTime() - start) / 1_000_000}"
                streams.forEach { result ->
                    output += "$type.stream.${result.position}.status=${result.status}"
                    output += "$type.stream.${result.position}.failure=${result.failure}"
                    output += "$type.stream.${result.position}.count=${result.items.size}"
                    output += "$type.stream.${result.position}.http=${result.items.count { it.kind == AddonStreamKind.HTTP }}"
                }
                stage = "$type.subtitles"
                val selected = streams.flatMap { it.items }.firstOrNull { it.kind == AddonStreamKind.HTTP }
                val subtitles = sources.subtitles("source-probe", video, selected?.subtitleExtras().orEmpty()).toList().filter { it.status != AddonSourceStatus.LOADING }
                subtitles.forEach { result ->
                    output += "$type.subtitles.${result.position}.status=${result.status}"
                    output += "$type.subtitles.${result.position}.failure=${result.failure}"
                    output += "$type.subtitles.${result.position}.count=${result.items.size}"
                    if (result.failure == AddonFailure.REDIRECT) {
                        val addon = installed.first { it.installationId == result.installationId }
                        val origin = addon.endpoint.resourceUrl("subtitles", video.type, video.id, selected?.subtitleExtras().orEmpty()).toHttpUrl()
                        val diagnostic = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                            .retryOnConnectionFailure(false).callTimeout(10, TimeUnit.SECONDS).build()
                        diagnostic.newCall(Request.Builder().url(origin).build()).execute().use { response ->
                            val target = response.header("Location")?.let { origin.resolve(it) }
                            output += "$type.subtitles.${result.position}.redirect.code=${response.code}"
                            output += "$type.subtitles.${result.position}.redirect.sameOrigin=${target != null && target.scheme == origin.scheme && target.host == origin.host && target.port == origin.port}"
                            output += "$type.subtitles.${result.position}.redirect.https=${target?.isHttps == true}"
                            output += "$type.subtitles.${result.position}.redirect.userinfo=${target != null && (target.username.isNotEmpty() || target.password.isNotEmpty())}"
                        }
                    }
                }
                // Empty results are valid provider responses, not parser failures.
                check(streams.isNotEmpty() && streams.any { it.status == AddonSourceStatus.READY })
                check(subtitles.isNotEmpty() && subtitles.any { it.status == AddonSourceStatus.READY })
            }
            output += "outcome=PASS"
        } catch (error: Throwable) {
            output += "outcome=FAIL"
            output += "stage=$stage"
            throw AssertionError("Private source probe failed at $stage (${(error as? AddonException)?.failure ?: "CHECK_FAILED"}); see redacted summary")
        } finally { File(file.parentFile, "live-sources-summary.txt").writeText(output.joinToString("\n")) }
    }
}
