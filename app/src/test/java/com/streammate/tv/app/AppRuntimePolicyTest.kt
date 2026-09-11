package com.streammate.tv.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppRuntimePolicyTest {
    @Test fun `lab never runs public updates or automatic provider work`() {
        val policy = AppRuntimePolicy.forPackage("com.streammate.tv.lab")
        assertTrue(policy.isLab)
        assertTrue(policy.addonsAllowed)
        assertFalse(policy.publicUpdatesAllowed)
        assertFalse(policy.automaticMaintenanceAllowed)
        assertFalse(policy.automaticSportsRefreshAllowed)
        assertFalse(policy.remindersAllowed)
    }

    @Test fun `production behavior remains enabled`() {
        val policy = AppRuntimePolicy.forPackage("com.streammate.tv")
        assertFalse(policy.isLab)
        assertTrue(policy.publicUpdatesAllowed)
        assertTrue(policy.automaticMaintenanceAllowed)
        assertTrue(policy.automaticSportsRefreshAllowed)
        assertTrue(policy.remindersAllowed)
        assertTrue(policy.addonsAllowed)
        assertNotNull(AddonFeature.load(policy))
    }

    @Test fun `other packages cannot offer the production updater`() {
        listOf("com.streammate.tv.debug", "com.streammate.tv.demo", "com.streammate.tv.other").forEach {
            val policy = AppRuntimePolicy.forPackage(it)
            assertFalse(policy.publicUpdatesAllowed)
            assertFalse(policy.isLab)
            assertTrue(policy.automaticMaintenanceAllowed)
        }
    }

    @Test fun `debug can test addons without changing ordinary background policy`() {
        val policy = AppRuntimePolicy.forPackage("com.streammate.tv.debug")
        assertTrue(policy.addonsAllowed)
        assertTrue(policy.automaticSportsRefreshAllowed)
        assertTrue(policy.remindersAllowed)
        assertNotNull(AddonFeature.load(policy))
    }

    @Test fun `demo and unknown packages cannot activate addons`() {
        listOf("com.streammate.tv.demo", "com.streammate.tv.other", "com.streammate.tv.lab.other", "").forEach {
            val policy = AppRuntimePolicy.forPackage(it)
            assertFalse(policy.addonsAllowed)
            assertNull(AddonFeature.load(policy))
        }
    }
}
