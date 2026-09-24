package com.sohva.tv.ui.design

import androidx.compose.runtime.saveable.SaverScope
import com.sohva.tv.ui.design.navigation.BackStack
import com.sohva.tv.ui.design.navigation.RouteCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackStackTest {
    private val codec = object : RouteCodec<String> {
        override fun encode(route: String) = route
        override fun decode(value: String) = value.takeUnless { it == "gone" }
    }

    private fun routes(stack: BackStack<String>) = stack.entries.map { it.route }

    @Test
    fun pushIgnoresTheRouteAlreadyOnTopSoADoubleOkOpensOnce() {
        val stack = BackStack.of(listOf("home"))
        assertTrue(stack.push("guide"))
        assertFalse(stack.push("guide"))
        assertEquals(listOf("home", "guide"), routes(stack))
    }

    @Test
    fun popStopsAtTheLastEntry() {
        val stack = BackStack.of(listOf("home", "guide"))
        assertTrue(stack.pop())
        assertFalse(stack.pop())
        assertEquals(listOf("home"), routes(stack))
    }

    @Test
    fun everyEntryGetsItsOwnKeyEvenForARepeatedRoute() {
        val stack = BackStack.of(listOf("home"))
        stack.push("guide")
        val first = stack.top.key
        stack.pop()
        stack.push("guide")
        assertNotEquals(first, stack.top.key)
    }

    @Test
    fun replaceTopAndResetNeverGrowPastWhatWasAsked() {
        val stack = BackStack.of(listOf("home", "guide", "player:1"))
        stack.replaceTop("player:2")
        assertEquals(listOf("home", "guide", "player:2"), routes(stack))
        stack.resetTo(listOf("home"))
        assertEquals(listOf("home"), routes(stack))
    }

    @Test
    fun savedStackRestoresRoutesAndKeysAndDropsUnknownRoutes() {
        val stack = BackStack.of(listOf("home", "gone", "settings"))
        val saver = BackStack.saver(codec)
        val saved = with(saver) { SaverScope { true }.save(stack) }!!
        val restored = saver.restore(saved)!!
        assertEquals(listOf("home", "settings"), routes(restored))
        assertEquals(listOf(0, 2), restored.entries.map { it.key })
        restored.push("guide")
        assertEquals(3, restored.top.key)
    }
}
