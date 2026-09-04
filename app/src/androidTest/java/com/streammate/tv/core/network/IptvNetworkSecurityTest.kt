package com.streammate.tv.core.network

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IptvNetworkSecurityTest {

    @Test
    fun cleartextStaysAvailableForProviderSuppliedHosts() {
        val policy = NetworkSecurityPolicy.getInstance()

        // Provider playlists, EPG feeds and streams are whatever the user
        // types in, frequently plain HTTP and often a bare LAN address.
        assertTrue(policy.isCleartextTrafficPermitted("10.0.0.4"))
        assertTrue(policy.isCleartextTrafficPermitted("iptv.example.com"))
    }

    @Test
    fun firstPartyEndpointsCannotFallBackToCleartext() {
        val policy = NetworkSecurityPolicy.getInstance()

        // Everything StreamMate contacts on its own behalf is HTTPS-only, so
        // the cleartext allowance above cannot be used to downgrade a metadata
        // or sports lookup.
        listOf(
            "v3.football.api-sports.io",
            "v1.basketball.api-sports.io",
            "api.themoviedb.org",
            "image.tmdb.org",
            "api.tvmaze.com",
        ).forEach { host ->
            assertFalse(host, policy.isCleartextTrafficPermitted(host))
        }
    }
}
