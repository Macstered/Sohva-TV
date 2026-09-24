package com.sohva.tv.spike.guidegrid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.theme.Sohva

enum class GridVariant { CANVAS, CELLS }

/**
 * The guide's frame at 960 × 540 dp without its hero (the hero is the one allowed recomposition
 * per press, measured separately in M2): 148 dp where the hero sits, then the grid. Focus starts
 * on the first row's channel cell.
 */
@Composable
fun GuideSpikeScreen(variant: GridVariant) {
    val first = remember { FocusRequester() }
    ScreenBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = Sohva.spacing.safeHorizontal, vertical = Sohva.spacing.safeVertical)) {
            Spacer(Modifier.height(148.dp + 24.dp))
            val grid = Modifier.fillMaxWidth().weight(1f).testTag("spike-grid")
            when (variant) {
                GridVariant.CANVAS -> CanvasGrid(first, grid)
                GridVariant.CELLS -> CellGrid(first, grid)
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocusWhenAttached() }
}
