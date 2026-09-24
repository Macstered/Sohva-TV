package com.sohva.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun releaseHasEverythingButDemoContent() {
        assertEquals(
            FeatureFlags(sport = true, discover = true, trakt = true, reminders = true, publicUpdates = true, demoContent = false),
            FeatureFlags.resolve(BuildKind.RELEASE, traktConfigured = true),
        )
    }

    @Test
    fun labHasNoRemindersAndNoPublicUpdates() {
        val lab = FeatureFlags.resolve(BuildKind.LAB, traktConfigured = false)
        assertEquals(false, lab.reminders)
        assertEquals(false, lab.publicUpdates)
        assertEquals(true, lab.discover)
        assertEquals(false, lab.trakt)
    }

    @Test
    fun demoHasNoDiscoverAndCarriesDemoContent() {
        val demo = FeatureFlags.resolve(BuildKind.DEMO, traktConfigured = false)
        assertEquals(false, demo.discover)
        assertEquals(true, demo.demoContent)
        assertEquals(false, demo.publicUpdates)
    }
}
