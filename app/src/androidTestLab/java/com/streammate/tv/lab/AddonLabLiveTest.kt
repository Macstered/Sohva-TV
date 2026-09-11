package com.streammate.tv.lab

import com.streammate.tv.addons.*

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.streammate.tv.app.MainActivity
import com.streammate.tv.app.StreamMateApplication
import com.sohva.tv.addons.AddonEndpoint
import com.sohva.tv.addons.AddonImportStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Explicit local bridge only. Never accepts configured URLs as instrumentation arguments. */
class AddonLabLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun installAndBrowseConfiguredAddon(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val port = arguments.getString("addonBridgePort")?.toIntOrNull()
        val token = arguments.getString("addonBridgeToken")
        assumeTrue("Live emulator input is explicitly opt-in", port != null && token != null)
        var stage = "bridge"
        try {
            check(port!! in 1024..65535 && token!!.matches(Regex("[a-f0-9]{64}")))
            val app = compose.activity.application as StreamMateApplication
            check(app.packageName == "com.streammate.tv.lab")
            val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
            val configuredUrls = http.newCall(Request.Builder().url("http://127.0.0.1:$port/private-input")
                .header("Authorization", "Bearer $token").build()).execute().use { response ->
                check(response.code == 200 && response.body.contentLength() in 1..262_144)
                response.body.string().trim()
            }
            stage = "install"
            val host = AddonHost.get(app, app.container)
            val profileId = app.container.preferencesRepository.preferences.first().activeProfileId
            val preview = host.batchImport.preview(profileId, configuredUrls)
            check(preview.entries.none { it.status == AddonImportStatus.FAILED })
            if (arguments.getString("addonRequireExisting") == "true") {
                check(preview.entries.all { it.status == AddonImportStatus.ALREADY_INSTALLED || it.status == AddonImportStatus.DUPLICATE_INPUT })
            }
            check(host.batchImport.commit(preview).none { it.status == AddonImportStatus.FAILED })
            val expected = configuredUrls.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                .map { AddonEndpoint.parse(it, allowBaseUrl = true).fingerprint }.toList()
            val saved = host.manager.list(profileId)
            check(expected.all { fingerprint -> saved.any { it.endpoint.fingerprint == fingerprint } })
            val installation = saved.first { it.endpoint.fingerprint == expected.first() }
            val catalogs = installation.manifest.catalogs.filter { catalog ->
                catalog.extras.none { it.required && it.name != "skip" }
            }
            val catalog = catalogs.firstOrNull { it.type == "movie" } ?: catalogs.first()
            stage = "home-entry"
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("home-discover")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-discover").performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-manage")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("addon-manage").performClick()
            stage = "manager"
            compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("addon-manager-list")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("addon-manager-list").performScrollToNode(hasTestTag("addon-browse-${installation.installationId}"))
            compose.onNodeWithTag("addon-browse-${installation.installationId}").performScrollTo().performClick()
            stage = "catalog"
            compose.onNodeWithText("${catalog.name} · ${catalog.type}").performScrollTo().performClick()
            compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().isNotEmpty() }
            val count = compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().size
            check(count > 0)
            stage = "details"
            compose.onAllNodes(hasTestTag("addon-media-card"))[0].performClick()
            compose.waitUntil(25_000) { compose.onAllNodes(hasTestTag("addon-details-loaded")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(20_000) { compose.onAllNodes(hasText("Working…")).fetchSemanticsNodes().isEmpty() }
            check(compose.onAllNodes(hasTestTag("addon-details-loaded")).fetchSemanticsNodes().isNotEmpty())
            if (arguments.getString("addonCheckSources") == "true") {
                stage = "sources"
                compose.onNodeWithTag("addon-find-sources").performScrollTo().performClick()
                compose.waitUntil(45_000) { compose.onAllNodes(hasTestTag("addon-source-item")).fetchSemanticsNodes().isNotEmpty() }
            }
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            check(compose.onAllNodes(hasTestTag("addon-media-card")).fetchSemanticsNodes().isNotEmpty())
            println("Live Lab checks passed (${expected.distinct().size} configurations, existing-only=${arguments.getString("addonRequireExisting") == "true"}, sources=${arguments.getString("addonCheckSources") == "true"}); configured URLs and media values omitted.")
        } catch (error: Throwable) {
            throw AssertionError("Live Lab check failed at $stage (${error.javaClass.simpleName}); sensitive diagnostics omitted")
        }
    }
}
