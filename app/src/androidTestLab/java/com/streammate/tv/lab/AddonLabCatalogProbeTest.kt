package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.addons.*
import com.streammate.tv.app.StreamMateApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only diagnostic of the user's reported custom list. No imports or playback.
 * Output is limited to counts/categories; never emit titles, IDs, hosts or configured URLs.
 */
class AddonLabCatalogProbeTest {
    @Test fun inspectCustomMovieCatalog(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("addonCatalogProbe") == "true")
        val app = ApplicationProvider.getApplicationContext<StreamMateApplication>()
        check(app.packageName == "com.streammate.tv.lab")
        val emulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu")
        check(emulator || (android.os.Build.MODEL == "SHIELD Android TV" && arguments.getString("addonPhysicalDevice") == "true"))
        val host = AddonHost.get(app, app.container)
        val profile = app.container.preferencesRepository.preferences.first().activeProfileId
        val saved = host.manager.list(profile)
        println("Home routing: readyCatalogs=${saved.filter { it.enabled }.sumOf { it.manifest.catalogs.count { catalog -> catalog.belongsOnLanding() } }}, requiresChoice=${saved.filter { it.enabled }.sumOf { it.manifest.catalogs.count { catalog -> catalog.extras.any { extra -> extra.required && extra.name != "skip" } } }}")
        saved.forEachIndexed { index, owner -> println("Catalog inventory: index=$index, enabled=${owner.enabled}, catalogs=${owner.manifest.catalogs.size}, movie=${owner.manifest.catalogs.count { it.type == "movie" }}, series=${owner.manifest.catalogs.count { it.type == "series" }}, declaresMeta=${owner.manifest.resources.any { it.name == "meta" }}") }
        val matches = saved.filter { it.enabled }.flatMap { owner -> owner.manifest.catalogs.filter {
            (owner.manifest.name.contains("mdblist", true) || owner.manifest.id.contains("mdblist", true) ||
                it.name.contains("mdblist", true) || it.id.contains("mdblist", true) || owner.manifest.resources.none { resource -> resource.name == "meta" }) &&
                it.type == "movie" && it.extras.none { extra -> extra.required && extra.name != "skip" }
        }.map { owner to it } }.sortedBy { (owner, _) -> if (owner.manifest.resources.none { it.name == "meta" }) 0 else 1 }.take(1)
        println("Custom-list probe: installations=${saved.size}, matchingMovieCatalogs=${matches.size}")
        check(matches.isNotEmpty()) { "No matching movie catalog; no provider calls made." }
        for ((owner, catalog) in matches) {
            var stage = "catalog"
            try {
                val preview = host.browser.catalog(profile, owner.installationId, catalog.type, catalog.id).value.items.first()
                println("Catalog sample: movie=${preview.key.type == "movie"}, ownerSupportsMeta=${owner.manifest.supports("meta", preview.key.type, preview.key.id)}")
                stage = "metadata"
                val resolved = try { host.browser.details(profile, owner.installationId, preview.key).value }
                    catch (error: AddonException) { println("Metadata unavailable: ${error.failure.name}"); preview }
                val video = resolved.singleVideoKey()
                check(video != null && video.type == "movie")
                stage = "sources"
                val results = host.sources.streams(profile, video).toList().filter { it.status != AddonSourceStatus.LOADING }
                println("Movie routing: providers=${results.size}, ready=${results.count { it.status == AddonSourceStatus.READY }}, playable=${results.sumOf { result -> result.items.count { it.kind == AddonStreamKind.HTTP } }}")
                check(results.any { it.items.any { item -> item.kind == AddonStreamKind.HTTP } }) { "No playable sources for sampled movie." }
            } catch (error: Throwable) {
                throw AssertionError("Custom-list probe failed at $stage (${error.javaClass.simpleName}); sensitive details omitted")
            }
        }
    }
}
