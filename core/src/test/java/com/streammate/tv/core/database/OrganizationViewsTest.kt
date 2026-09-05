package com.streammate.tv.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

class OrganizationViewsTest {
    @Test
    fun `the inlined live predicate is the view's predicate with the timeline's aliases`() {
        val viewPredicate = ORGANIZATION_VISIBLE_LIVE_SQL.substringAfter("WHERE ").trim()
        val expected = viewPredicate
            .replace("m.", "c.")
            .replace("p.hidden", "preference.hidden")
            .replace("p.customOrganizationGroupKey", "preference.customOrganizationGroupKey")
        assertEquals(expected, ORGANIZATION_VISIBLE_LIVE_PREDICATE.trim())
    }
}
