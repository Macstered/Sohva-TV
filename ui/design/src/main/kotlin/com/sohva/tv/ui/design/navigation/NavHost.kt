package com.sohva.tv.ui.design.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Shows only the top destination (spec 01 SHELL-FR-14), instantly, with no transition (FR-15).
 * Each entry keeps its saveable state and its own screen models while it is in the stack; both
 * are dropped when the entry is popped. Back goes through the activity's back dispatcher, so a
 * remote's Back and `sendKeyDownUpSync(KEYCODE_BACK)` behave alike (FR-90); screen handlers
 * registered inside [content] take precedence.
 */
@Composable
fun <R : Any> NavHost(stack: BackStack<R>, content: @Composable (R) -> Unit) {
    val holder = rememberSaveableStateHolder()
    // Initializer form: no reflective ViewModel factory (plan/03 §4.4).
    val stores = viewModel { EntryStores() }
    val top = stack.top
    val owner = remember(top.key) { stores.owner(top.key) }

    BackHandler(enabled = stack.size > 1) { stack.pop() }

    holder.SaveableStateProvider(top.key) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content(top.route) }
    }

    // Drop the state of popped entries; entries still below the top keep theirs.
    LaunchedEffect(stack) {
        var previous = stack.entries.map { it.key }.toSet()
        snapshotFlow { stack.entries.map { it.key }.toSet() }.collect { live ->
            (previous - live).forEach { key ->
                holder.removeState(key)
                stores.clear(key)
            }
            previous = live
        }
    }
}

/** Screen models per stack entry; survives configuration changes with the activity. */
internal class EntryStores : ViewModel() {
    private val stores = HashMap<Int, ViewModelStore>()

    fun owner(key: Int): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun clear(key: Int) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}
