package com.sohva.tv.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.trakt.Beta23TraktImport
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.feature.trakt.protocol.ScrobbleAction
import com.sohva.tv.feature.trakt.shelf.TraktShelfKind
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decision A1 for Trakt: beta 23's `trakt_accounts`, written here as beta 23 wrote it (its main
 * envelope, its JSON), comes across once: the account and tokens, the scrobble queue with its
 * actions renamed, the activity stamp and the Watch next list with its ids regrouped. A lost old
 * key imports nothing. Fictional account and tokens only.
 */
@RunWith(AndroidJUnit4::class)
class TraktBeta23ImportTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val graph get() = (context.applicationContext as SohvaApplication).graph
    private val spec = EnvelopeSpec.BETA23_MAIN
    private val profile = "default"

    @get:Rule
    val clear = ClearStateRule()

    @Before
    fun seed() {
        forget()
        val cipher = EnvelopeCipher(spec, AndroidKeystoreKeyProvider(spec.keystoreAlias), PrefsWrappedKeyStore(context, spec))
        fun enc(json: Any) = cipher.encrypt(json.toString())
        val account = JSONObject().put("access", "not-a-real-token").put("refresh", "not-a-real-refresh").put("expires", 4_000_000_000_000L)
            .put("username", "fictional-viewer").put("reauthorize", false)
        val pending = JSONArray().put(JSONObject().put("kind", "movie").put("tmdb", 603).put("action", "STOP").put("progress", 100.0))
        val nextUp = JSONObject().put("at", 5L).put(
            "items",
            JSONArray().put(
                JSONObject().put("kind", "show").put("title", "A fictional show").put("trakt", 10).put("tmdb", 1399)
                    .put("season", 1).put("number", 2).put("episodeTitle", "Second").put("updatedAt", 7L),
            ),
        )
        context.getSharedPreferences(Beta23TraktImport.OLD_FILE, Context.MODE_PRIVATE).edit()
            .putString("account:$profile", enc(account))
            .putString("pending:$profile", enc(pending))
            .putLong("activity:$profile", 1_234L)
            .putString("nextup:$profile", enc(nextUp))
            .commit()
    }

    @After
    fun forget() {
        runBlocking { graph.data.appMeta.delete(Beta23TraktImport.MARKER) }
        listOf(Beta23TraktImport.OLD_FILE, spec.prefsFile).forEach { context.deleteSharedPreferences(it) }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        runBlocking { graph.trakt?.resetForTests() }
    }

    @Test
    fun beta23sAccountComesAcrossOnce() = runBlocking {
        Beta23TraktImport(graph).run()
        val host = graph.trakt!!
        val account = host.account(profile)!!
        assertEquals("fictional-viewer", account.username)
        assertEquals("not-a-real-token", account.tokens?.access)
        assertEquals(4_000_000_000_000L, account.tokens?.expiresAt)
        assertEquals(listOf(ScrobbleAction.STOP), host.scrobbles.load(profile).map { it.action })
        assertEquals(1_234L, host.store.long("activity:$profile"))
        val next = host.shelves.read(profile, TraktShelfKind.WATCH_NEXT)!!.cards.single()
        assertEquals(1399L, next.ids.tmdb)
        assertEquals(2, next.number)
        // Once: a second run changes nothing.
        host.disconnect(profile)
        Beta23TraktImport(graph).run()
        assertNull(host.account(profile))
    }

    @Test
    fun aLostOldKeyImportsNothing() = runBlocking {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        Beta23TraktImport(graph).run()
        assertNull(graph.trakt!!.account(profile))
        assertEquals("failed", graph.data.appMeta.value(Beta23TraktImport.MARKER))
    }
}
