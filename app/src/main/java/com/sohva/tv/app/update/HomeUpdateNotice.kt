package com.sohva.tv.app.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.reminder.PromptStep
import com.sohva.tv.ui.design.theme.Sohva
import com.sohva.tv.ui.design.theme.UpdateNoticeDimensions

/** The release app's Home only; optional services are never opened by the launch frame. */
@Composable
fun HomeUpdateNotice(graph: AppGraph, onHome: Boolean, importNotice: Boolean) {
    if (!graph.flags.publicUpdates || !onHome) return
    var drawn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        drawn = true
    }
    if (!drawn) return
    val foreground by graph.inForeground.collectAsStateWithLifecycle()
    val playing by graph.playbackActive.collectAsStateWithLifecycle()
    val corner by graph.inPictureInPicture.collectAsStateWithLifecycle()
    val reminder by graph.reminders.current.collectAsStateWithLifecycle()
    val prompt by graph.reminders.prompt.collectAsStateWithLifecycle()
    val eligible = foreground && !playing && !corner && !importNotice && reminder == null && prompt == PromptStep.NONE
    Box(Modifier.fillMaxSize()) {
        UpdateNoticeLayer(
            graph.updateNotices, eligible,
            Modifier.align(Alignment.TopEnd).padding(top = UpdateNoticeDimensions.top, end = Sohva.spacing.xl),
        )
    }
}
