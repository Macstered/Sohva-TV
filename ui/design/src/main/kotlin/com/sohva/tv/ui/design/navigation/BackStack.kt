package com.sohva.tv.ui.design.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

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

    /**
     * Resets to [routes], for example a player shortcut to Home. The bottom entry stays itself when
     * its route is the same, so Home keeps its state and screen model and comes back to the card
     * last used (spec 02 HOME-FR-97); everything above starts afresh. [fresh] replaces every entry
     * (after a backup restore).
     */
    fun resetTo(routes: List<R>, fresh: Boolean = false) {
        require(routes.isNotEmpty()) { "a stack is never empty" }
        val keep = !fresh && stack.first().route == routes.first()
        while (stack.size > (if (keep) 1 else 0)) stack.removeAt(stack.lastIndex)
        routes.drop(if (keep) 1 else 0).forEach { stack.add(NavEntry(nextKey++, it)) }
    }

    /** Apply the codec's playback/PIN restoration policy without replacing the live stack. */
    internal fun restoreRoutes(codec: RouteCodec<R>): Boolean {
        val restored = stack.mapNotNull { entry ->
            codec.decode(codec.encode(entry.route))?.let { NavEntry(entry.key, it) }
        }
        if (restored.isEmpty()) return false
        if (restored != stack) {
            stack.clear()
            stack.addAll(restored)
        }
        return true
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

/**
 * The activity's single navigation stack. Keep its identity with the screen models across
 * recreation; otherwise their saved actions mutate an invisible old stack (HOME-27).
 * Save route ids and entry keys for process death; [start] is used only for a fresh stack.
 */
@Composable
fun <R : Any> rememberBackStack(codec: RouteCodec<R>, start: () -> List<R>): BackStack<R> {
    val retained = viewModel { RetainedBackStack<R>() }
    val saver = remember(retained, codec) { retained.saver(codec) }
    return rememberSaveable(saver = saver) { retained.start(start) }
}

/** One stack per activity owner, released with it; this holds only routes/keys, never content. */
internal class RetainedBackStack<R : Any> : ViewModel() {
    private var stack: BackStack<R>? = null

    fun start(routes: () -> List<R>): BackStack<R> = stack ?: BackStack.of(routes()).also { stack = it }

    fun saver(codec: RouteCodec<R>): Saver<BackStack<R>, ArrayList<String>> {
        val persisted = BackStack.saver(codec)
        return Saver(
            save = { value -> with(persisted) { save(value) } },
            // Keep captured actions live, but still drop playback/reapply management gates.
            restore = { saved ->
                val live = stack
                if (live != null && live.restoreRoutes(codec)) live
                else persisted.restore(saved).also { stack = it }
            },
        )
    }

    override fun onCleared() {
        stack = null
    }
}
