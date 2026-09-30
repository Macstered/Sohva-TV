package com.sohva.tv.app

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sohva.tv.app.live.AppGuideEnvironment
import com.sohva.tv.core.data.database.LiveGroup
import com.sohva.tv.core.data.database.LiveSource
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.CustomListRef
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveRailRules
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.data.live.ShortcutRule
import com.sohva.tv.feature.live.GuideEnvironment
import com.sohva.tv.feature.live.GuideModel
import com.sohva.tv.feature.live.GuidePhase
import java.util.Locale
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cached source/rule flows may emit inline during construction on Main.immediate. */
@RunWith(AndroidJUnit4::class)
class GuideInitializationTest {
    @get:Rule
    val clear: ClearStateRule = ClearStateRule()

    private val graph get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SohvaApplication).graph
    private val source = LiveSource("cached", "Cached source", 0)

    private fun environment(sourceFlow: Flow<List<LiveSource>>, ruleFlow: Flow<LiveRailRules>): GuideEnvironment {
        val cachedList = runBlocking { graph.liveReads.open(ListSpec.Named(source.id, longArrayOf())) }
        val cached = object : LiveReads by graph.liveReads {
            override val sources: Flow<List<LiveSource>> = sourceFlow
            override fun railRuleChanges(): Flow<LiveRailRules> = ruleFlow
            override fun changes(): Flow<Unit> = emptyFlow()
            override fun guideChanges(): Flow<Unit> = emptyFlow()
            override fun favouriteKeys(): Flow<Set<String>> = flowOf(emptySet())
            override fun customLists(): Flow<List<CustomListRef>> = flowOf(emptyList())
            override suspend fun rail(sourceId: String): List<LiveGroup> = emptyList()
            override suspend fun open(spec: ListSpec): ChannelList = cachedList
        }
        return object : GuideEnvironment by AppGuideEnvironment(graph, Locale.ENGLISH) {
            override val reads: LiveReads = cached
            override val lastGuideSource: Flow<String?> = flowOf(null)
            override val lastChannel: Flow<String?> = flowOf(null)
            override suspend fun liveRestricted(): Boolean = false
            override suspend fun saveGuideSource(id: String) = Unit
        }
    }

    @Test
    fun cachedSourcesCanBuildTheRailDuringConstruction() {
        val env = environment(flowOf(listOf(source)), emptyFlow())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GuideModel(env, null)
            try {
                assertEquals(GuidePhase.READY, model.phase.value)
                assertEquals(listOf("favourites", "all", "recent"), model.rail.value.map { it.entry.id })
            } finally {
                model.viewModelScope.cancel()
            }
        }
    }

    @Test
    fun cachedRailSettingsStayAppliedWhenSourcesArriveLater() {
        val sources = MutableStateFlow(emptyList<LiveSource>())
        val rules = LiveRailRules(favourites = ShortcutRule(false), recent = ShortcutRule(false))
        val env = environment(sources, flowOf(rules))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = GuideModel(env, null)
            try {
                sources.value = listOf(source)
                assertEquals(listOf("all"), model.rail.value.map { it.entry.id })
            } finally {
                model.viewModelScope.cancel()
            }
        }
    }
}
