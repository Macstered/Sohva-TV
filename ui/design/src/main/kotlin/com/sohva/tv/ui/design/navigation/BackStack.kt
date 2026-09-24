package com.sohva.tv.ui.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** Turns a route into a short string and back; routes carry ids only, never records. */
interface RouteCodec<R : Any> {
    fun encode(route: R): String

    /** Null for a route this build no longer knows; the stack drops it. */
    fun decode(value: String): R?
}

/** One place in the stack. [key] identifies its saved state and screen models. */
@Immutable
data class NavEntry<R : Any>(val key: Int, val route: R)

/**
 * The destination stack of spec 01 §4.3: starts from a start route, pushes unless the route is
 * already on top (a double OK opens once), pops while more than one entry remains. Saved across
 * process death and a language change through [RouteCodec] (beta 23 restarted at the start route).
 */
@Stable
class BackStack<R : Any> internal constructor(entries: List<NavEntry<R>>, private var nextKey: Int) {
    private val stack = mutableStateListOf<NavEntry<R>>().apply { addAll(entries) }

    val entries: List<NavEntry<R>> get() = stack
    val top: NavEntry<R> get() = stack.last()
    val size: Int get() = stack.size

    /** Opens [route]; ignored when it is already on top. Returns whether the stack changed. */
    fun push(route: R): Boolean {
        if (stack.lastOrNull()?.route == route) return false
        stack.add(NavEntry(nextKey++, route))
        return true
    }

    /** Back: drops the top entry while more than one remains. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the top entry (a channel change inside the player never grows the stack). */
    fun replaceTop(route: R) {
        stack[stack.lastIndex] = NavEntry(nextKey++, route)
    }

    /** Resets to [routes], for example a player shortcut to Home. */
    fun resetTo(routes: List<R>) {
        require(routes.isNotEmpty()) { "a stack is never empty" }
        stack.clear()
        routes.forEach { stack.add(NavEntry(nextKey++, it)) }
    }

    companion object {
        fun <R : Any> of(routes: List<R>): BackStack<R> {
            require(routes.isNotEmpty()) { "a stack is never empty" }
            return BackStack(routes.mapIndexed { i, r -> NavEntry(i, r) }, routes.size)
        }

        fun <R : Any> saver(codec: RouteCodec<R>): Saver<BackStack<R>, ArrayList<String>> = Saver(
            save = { stack ->
                ArrayList<String>().apply {
                    add(stack.nextKey.toString())
                    stack.entries.forEach { add("${it.key}|${codec.encode(it.route)}") }
                }
            },
            restore = { saved ->
                val entries = saved.drop(1).mapNotNull { line ->
                    val key = line.substringBefore('|').toIntOrNull() ?: return@mapNotNull null
                    codec.decode(line.substringAfter('|'))?.let { NavEntry(key, it) }
                }
                if (entries.isEmpty()) null else BackStack(entries, saved.first().toInt())
            },
        )
    }
}

/** The stack, saved and restored with the activity; [start] is used only the first time. */
@Composable
fun <R : Any> rememberBackStack(codec: RouteCodec<R>, start: () -> List<R>): BackStack<R> =
    rememberSaveable(saver = BackStack.saver(codec)) { BackStack.of(start()) }
