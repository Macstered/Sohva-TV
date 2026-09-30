package com.sohva.tv.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.theme.SohvaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Data arriving after the viewer moved away cannot complete an old focus request. */
@RunWith(AndroidJUnit4::class)
class DelayedFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aLateAttachedTargetKeepsTheViewersNewerChoice() {
        var showOldTarget by mutableStateOf(false)
        var stillWanted = true
        val old = FocusRequester()
        val newer = FocusRequester()
        lateinit var scope: CoroutineScope
        lateinit var request: Job
        compose.mainClock.autoAdvance = false
        compose.setContent {
            scope = rememberCoroutineScope()
            SohvaTheme(ColorThemeId.ORIGINAL, reducedMotion = true) {
                Row {
                    TvSurface({}, Modifier.size(100.dp).focusRequester(newer).testTag("newer")) { }
                    if (showOldTarget) TvSurface({}, Modifier.size(100.dp).focusRequester(old).testTag("old")) { }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            newer.requestFocus()
            request = scope.launch {
                old.requestFocusWhenAttached(stillWanted = { stillWanted })
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertTrue("the focus request is waiting for its target", request.isActive)
            stillWanted = false
            showOldTarget = true
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("newer").assertIsFocused()
    }
}
