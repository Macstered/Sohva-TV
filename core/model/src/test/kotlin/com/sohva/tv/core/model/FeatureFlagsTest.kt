package com.sohva.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureFlagsTest {
    /** Decision "Play build": everything the release has, but no in-app updater. */
    @Test
    fun thePlayBuildIsTheReleaseWithoutTheUpdater() {
        assertEquals(FeatureFlags.resolve(BuildKind.RELEASE).copy(publicUpdates = false), FeatureFlags.resolve(BuildKind.PLAY))
    }

    @Test
    fun releaseHasEverythingButDemoContent() {
        assertEquals(
            FeatureFlags(sport = true, discover = true, trakt = true, reminders = true, publicUpdates = true, demoContent = false),
            FeatureFlags.resolve(BuildKind.RELEASE),
        )
    }

    @Test
    fun labHasNoRemindersAndNoPublicUpdates() {
        val lab = FeatureFlags.resolve(BuildKind.LAB)
        assertEquals(false, lab.reminders)
        assertEquals(false, lab.publicUpdates)
        assertEquals(true, lab.discover)
        // Trakt is in every build; credentials decide whether it can connect (spec 51 TRAKT-08).
        assertEquals(true, lab.trakt)
    }

    @Test
    fun demoHasNoDiscoverAndCarriesDemoContent() {
        val demo = FeatureFlags.resolve(BuildKind.DEMO)
        assertEquals(false, demo.discover)
        assertEquals(true, demo.demoContent)
        assertEquals(false, demo.publicUpdates)
    }
}
