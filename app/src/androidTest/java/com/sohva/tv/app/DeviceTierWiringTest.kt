package com.sohva.tv.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.core.data.device.DeviceTierReader
import com.sohva.tv.core.model.device.DeviceTier
import com.sohva.tv.core.model.device.MemoryTier
import com.sohva.tv.feature.home.RailItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A 1–2 GB TV with a generous heap still needs small artwork and the low-memory player cap. */
@RunWith(AndroidJUnit4::class)
class DeviceTierWiringTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ClearStateRule()).around(compose)

    @Test
    fun homeAndPlayerUseTheStartupMemoryTier() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = (context.applicationContext as SohvaApplication).graph
        val expected = DeviceTier.decide(DeviceTierReader(context).read()).memory == MemoryTier.LOW
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(20_000) { compose.onAllNodesWithTagExists(RailItem.LIVE_TV.tag) }
            assertEquals("Home artwork and playback must include physical RAM in their tier", expected, graph.player.lowMemory)
        }
    }
}
