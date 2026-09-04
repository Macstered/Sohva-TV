package com.streammate.tv.iptv.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogueRepositoryTitleTest {
    @Test
    fun infersBracketedOrParenthesizedYearFromProviderTitle() {
        assertEquals(2025, vodYearFromTitle("The Shadow's Edge [Multi-Sub] [2025]"))
        assertEquals(1999, vodYearFromTitle("The Matrix (1999)"))
        assertNull(vodYearFromTitle("Movie without a release year"))
    }
}
