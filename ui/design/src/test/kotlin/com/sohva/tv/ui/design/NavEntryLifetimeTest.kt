package com.sohva.tv.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sohva.tv.ui.design.navigation.EntryStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Restored routes can drop playback while no host is composed; its model must still be cleared. */
class NavEntryLifetimeTest {
    private class ScreenModel : ViewModel() {
        var cleared: Boolean = false
        override fun onCleared() { cleared = true }
    }

    private fun model(stores: EntryStores, key: Int): ScreenModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ScreenModel() as T
        }
        return ViewModelProvider(stores.owner(key), factory)[ScreenModel::class.java]
    }

    @Test
    fun aRestoredHostClearsDroppedModelsAndKeepsTheLiveHomeModel() {
        val stores = EntryStores()
        val home = model(stores, 0)
        val player = model(stores, 1)
        assertEquals(listOf(1), stores.clearMissing(setOf(0)))
        assertTrue(player.cleared)
        assertFalse(home.cleared)
        assertEquals(home, model(stores, 0))
        assertEquals(emptyList<Int>(), stores.clearMissing(setOf(0)))
        assertNotSame(player, model(stores, 1))
    }
}
