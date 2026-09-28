package com.sohva.tv.feature.trakt

import com.sohva.tv.feature.trakt.protocol.TraktListRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 02 HOME-FR-99: what the viewer types or pastes to add a Trakt list. */
class TraktListRefTest {
    @Test
    fun numbersAndAddressesAreRecognised() {
        assertEquals(TraktListRef.ById(26421), TraktListRef.parse(" 26421 "))
        assertEquals(TraktListRef.ById(26421), TraktListRef.parse("https://trakt.tv/lists/26421"))
        assertEquals(TraktListRef.ByUser("viewer-one", "best-films"), TraktListRef.parse("https://trakt.tv/users/viewer-one/lists/best-films?sort=rank,asc"))
        assertEquals(TraktListRef.ByUser("viewer_one", "best-films"), TraktListRef.parse("app.trakt.tv/users/viewer_one/lists/best-films/"))
        assertEquals(TraktListRef.ByUser("a.b", "123"), TraktListRef.parse("http://www.trakt.tv/users/a.b/lists/123#top"))
    }

    /** HOME-FR-101: the Trakt app's smart list addresses, as the owner pasted one on 28 September 2026. */
    @Test
    fun smartListAddressesAreRecognised() {
        val smart = TraktListRef.Smart("lasten-sarjat-2eadc6438118f342")
        assertEquals(smart, TraktListRef.parse("https://app.trakt.tv/lists/smart/view/lasten-sarjat-2eadc6438118f342"))
        assertEquals(smart, TraktListRef.parseAddress("app.trakt.tv/lists/smart/view/lasten-sarjat-2eadc6438118f342/"))
        assertEquals(smart, TraktListRef.parse("https://trakt.tv/lists/smart/lasten-sarjat-2eadc6438118f342"))
        assertEquals(TraktListRef.Smart("48183"), TraktListRef.parse("https://app.trakt.tv/smart-lists/48183"))
        assertNull(TraktListRef.parse("https://app.trakt.tv/lists/smart/view/../x"))
    }

    @Test
    fun anythingElseIsANameUnlessItLooksLikeAnAddress() {
        assertEquals(TraktListRef.Search("Nordic noir"), TraktListRef.parse("Nordic noir"))
        assertNull("not a list page", TraktListRef.parse("https://trakt.tv/movies/some-film"))
        assertNull("another site", TraktListRef.parse("https://provider.example/users/a/lists/b"))
        assertNull("a path that could leave the list", TraktListRef.parse("https://trakt.tv/users/../lists/x"))
        assertNull(TraktListRef.parse("   "))
        assertNull(TraktListRef.parse("0"))
        assertNull(TraktListRef.parse("x".repeat(400)))
        // The phone page takes addresses and numbers only.
        assertNull(TraktListRef.parseAddress("Nordic noir"))
        assertEquals(TraktListRef.ById(7), TraktListRef.parseAddress("7"))
    }
}
