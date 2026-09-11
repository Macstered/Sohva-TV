package com.sohva.tv.addons

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in only. One unshared link, three reads, no account or provider requests. */
class StremioLiveLinkTest {
    @Test fun freshUnsharedLinkRemainsPendingAcrossPolls(): Unit = runBlocking {
        assumeTrue("Live link probe is explicitly opt-in", System.getProperty("sohva.stremio.linkProbe") == "true")
        try {
            withTimeout(45_000) {
                val client = StremioCopyClient()
                // Do not print, save, scan or share this code. Never probe a guessed code.
                val link = client.createLink()
                repeat(3) {
                    delay(3_000)
                    var checks = 0
                    val result = client.readAddons(link) {
                        // A second access check precedes the token exchange. Stop there
                        // if unexpectedly authorized; this probe must not access accounts.
                        if (++checks != 1) fail(AddonFailure.ACCESS_DENIED)
                    }
                    assertTrue("Fresh unshared link should remain pending", result == null)
                    assertTrue("No account access should be attempted", checks == 1)
                }
            }
        } catch (error: Exception) {
            val category = (error as? AddonException)?.failure?.name ?: "LOCAL_OR_TIMEOUT"
            throw AssertionError("Unauthenticated link probe failed ($category); response and pairing material omitted")
        }
    }
}
