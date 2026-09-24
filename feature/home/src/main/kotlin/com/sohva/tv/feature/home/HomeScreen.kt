package com.sohva.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohva.tv.ui.design.components.SohvaTvBrand
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground

/**
 * Home at M0: the ground, the brand where the header starts (96 dp: the collapsed rail plus 16)
 * and the rail. The hero and the rows arrive in M5 (spec 02).
 *
 * Focus: on entry the first rail item; on return from a destination the rail item that opened
 * it, remembered in this entry's saved state (roadmap M0 exit criterion).
 */
@Composable
fun HomeScreen(items: List<RailItem>, onOpen: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    var lastOpened by rememberSaveable { mutableStateOf<RailItem?>(null) }
    val requesters = remember(items) { items.associateWith { FocusRequester() } }
    ScreenBackground(modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 96.dp, end = 24.dp, top = 16.dp)) {
            SohvaTvBrand(fontSize = 22.sp)
        }
        HomeRail(
            items = items,
            requesters = requesters,
            onOpen = { item ->
                lastOpened = item
                onOpen(item)
            },
        )
    }
    LaunchedEffect(requesters) {
        val target = lastOpened?.takeIf { it in requesters } ?: items.first()
        requesters.getValue(target).requestFocusWhenAttached()
    }
}
