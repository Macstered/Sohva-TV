package com.sohva.tv.ui.design

import androidx.compose.runtime.saveable.SaverScope
import com.sohva.tv.ui.design.navigation.RetainedBackStack
import com.sohva.tv.ui.design.navigation.RouteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** A recreated host and retained screen actions must continue to mutate the same live stack. */
class BackStackRestoreTest {
    private val codec = object : RouteCodec<String> {
        override fun encode(route: String): String = route
        override fun decode(value: String): String? = value.takeUnless { it == "removed" }
    }

    @Test
    fun recreationKeepsTheLiveStackEvenWhenTheSavedSnapshotIsOlder() {
        val retained = RetainedBackStack<String>()
        val stack = retained.start { listOf("home") }
        val saver = retained.saver(codec)
        val saved = with(saver) { SaverScope { true }.save(stack) }!!
        stack.push("guide")
        val restored = saver.restore(saved)!!
        assertSame(stack, restored)
        assertEquals(listOf("home", "guide"), restored.entries.map { it.route })
        // An action held by the old Home model still updates the newly visible host.
        stack.push("player")
        assertEquals("player", restored.top.route)
    }

    @Test
    fun aFreshProcessRestoresTheLatestRoutesAndEntryKeysAfterRepeatedRecreation() {
        val retained = RetainedBackStack<String>()
        val stack = retained.start { listOf("home") }
        val saver = retained.saver(codec)
        val first = with(saver) { SaverScope { true }.save(stack) }!!
        val afterRecreation = saver.restore(first)!!
        afterRecreation.push("film")
        afterRecreation.push("player")
        val latest = with(saver) { SaverScope { true }.save(afterRecreation) }!!
        val fresh = RetainedBackStack<String>()
        val restored = fresh.saver(codec).restore(latest)!!
        assertNotSame(stack, restored)
        assertEquals(listOf("home", "film", "player"), restored.entries.map { it.route })
        assertEquals(stack.entries.map { it.key }, restored.entries.map { it.key })
        assertSame(restored, fresh.start { error("must not restart at Home") })
        restored.push("guide")
        assertEquals(3, restored.top.key)
    }

    @Test
    fun aFreshProcessStillDropsRoutesRemovedByAnUpdate() {
        val old = RetainedBackStack<String>()
        val stack = old.start { listOf("home", "removed", "settings") }
        val saved = with(old.saver(codec)) { SaverScope { true }.save(stack) }!!
        val restored = RetainedBackStack<String>().saver(codec).restore(saved)!!
        assertEquals(listOf("home", "settings"), restored.entries.map { it.route })
        assertEquals(listOf(0, 2), restored.entries.map { it.key })
    }

    @Test
    fun recreationReappliesPlaybackAndManagementRestorationWithoutStaleActions() {
        val policy = object : RouteCodec<String> {
            override fun encode(route: String): String = route
            override fun decode(value: String): String? = when (value) {
                "player" -> null
                "settings" -> "pin-gate"
                else -> value
            }
        }
        val retained = RetainedBackStack<String>()
        val stack = retained.start { listOf("home", "settings", "player") }
        val saver = retained.saver(policy)
        val saved = with(saver) { SaverScope { true }.save(stack) }!!
        val restored = saver.restore(saved)!!
        assertSame(stack, restored)
        assertEquals(listOf("home", "pin-gate"), restored.entries.map { it.route })
        assertEquals(listOf(0, 1), restored.entries.map { it.key })
        stack.replaceTop("settings")
        assertEquals("settings", restored.top.route)
        assertEquals(3, restored.top.key)
    }
}
