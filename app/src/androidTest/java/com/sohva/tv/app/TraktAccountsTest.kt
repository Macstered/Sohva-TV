package com.sohva.tv.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Profile
import com.sohva.tv.feature.home.RailItem
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Spec 51 §11 "Instrumented" for Settings › Accounts with a fake Trakt: not configured without
 * credentials; a sign-in shows the code, Back cancels it first; approving connects the account;
 * Disconnect forgets it; going to the background stops a sign-in; a restricted profile has no
 * Accounts section. Fictional data only.
 */
@RunWith(AndroidJUnit4::class)
class TraktAccountsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val graph get() = (instrumentation.targetContext.applicationContext as SohvaApplication).graph
    private val host get() = graph.trakt!!
    private val server = MockWebServer()

    @Volatile private var approved = false

    private val fake = object : ExternalResource() {
        override fun before() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.url.encodedPath) {
                        "/oauth/device/code" -> """{"device_code":"dev","user_code":"FICT1234","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":1}"""
                        "/oauth/device/token" -> if (approved) {
                            """{"access_token":"not-a-real-token","refresh_token":"not-a-real-refresh","token_type":"bearer","created_at":${System.currentTimeMillis() / 1000},"expires_in":7776000}"""
                        } else {
                            return MockResponse.Builder().code(400).body("""{"error":"authorization_pending"}""").build()
                        }
                        "/users/settings" -> """{"user":{"username":"fictional-viewer","ids":{"uuid":"uuid-1"}}}"""
                        else -> return MockResponse.Builder().code(404).build()
                    }
                    return MockResponse.Builder().body(body).build()
                }
            }
            server.start()
        }

        override fun after() = server.close()
    }
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(fake).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTagExists(tag)

    private fun click(tag: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { exists(tag) }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    private fun string(id: Int, vararg args: Any): String = AppLocales.texts(graph.app).getString(id, *args)

    private fun openAccounts() {
        compose.waitUntil(15_000) { exists(RailItem.SETTINGS.tag) }
        click(RailItem.SETTINGS.tag)
        click("settings-section-accounts")
        compose.waitUntil(5_000) { exists("trakt-primary") }
    }

    @Test
    fun withoutCredentialsThePanelSaysNotConfigured() {
        // A build made on a machine with the ignored credentials file has them; this test takes them away.
        host.useTestServer(server.url("/"), TraktCredentials.NONE)
        openAccounts()
        assertTrue(exists("trakt-unconfigured"))
        assertTrue(exists("trakt-help"))
    }

    @Test
    fun signInShowsTheCodeBackCancelsItAndApprovalConnects() {
        host.useTestServer(server.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
        openAccounts()
        click("trakt-primary")
        compose.waitUntil(5_000) { exists("trakt-code") }
        assertTrue(text("trakt-code"), text("trakt-code") == "FICT1234")
        // The first Back cancels the sign-in and stays on the panel.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("trakt-pairing") }
        assertTrue(exists("trakt-primary"))
        approved = true
        click("trakt-primary")
        compose.waitUntil(10_000) { exists("trakt-disconnect") }
        assertTrue(compose.onAllNodesWithTextExists(string(R.string.trakt_connected, "fictional-viewer")))
        click("trakt-disconnect")
        compose.waitUntil(5_000) { !exists("trakt-disconnect") }
        assertTrue(runBlocking { host.store.account(graph.data.profiles.activeId) } == null)
    }

    @Test
    fun goingToTheBackgroundStopsASignIn() {
        host.useTestServer(server.url("/"), TraktCredentials("fictional-client-id", "not-a-real-secret"))
        openAccounts()
        click("trakt-primary")
        compose.waitUntil(5_000) { exists("trakt-code") }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { exists("trakt-message") }
        assertTrue(text("trakt-message") == string(R.string.trakt_cancelled_background))
        assertTrue(!exists("trakt-pairing"))
    }

    @Test
    fun aRestrictedProfileHasNoAccountsSection() {
        runBlocking {
            graph.data.preferences.editHousehold { it.copy(stored = listOf(Profile("kids", "Kids", 1)), activeId = "kids", askAtStart = false) }
            graph.data.profiles.setAllowed("kids", OrgRoom.LIVE, "g0", true)
        }
        compose.waitUntil(15_000) { exists(RailItem.SETTINGS.tag) }
        click(RailItem.SETTINGS.tag)
        // Settings is behind the PIN for a restricted profile; without a PIN set it opens directly.
        compose.waitUntil(10_000) { exists("settings-section-sources") }
        Thread.sleep(500)
        assertTrue(!exists("settings-section-accounts"))
        assertTrue(runBlocking { !host.access.allowed("kids") })
    }
}
