package com.sohva.tv.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tracing.trace
import com.sohva.tv.core.model.guide.GuideProgramme
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/**
 * The live TV guide (spec 20, guide.md §0): hero, then the rail (when open) beside the grid and its
 * key hints; the dial read-out and the options sheet above. Back peels the sheet, then leaves
 * (spec 20 §3.2; the rail has no Back of its own, as in beta 23).
 */
@Composable
fun GuideScreen(model: GuideModel, navigation: GuideNavigation) = trace("Guide:Screen") {
    val phase by model.phase.collectAsStateWithLifecycle()
    ScreenBackground(Modifier.fillMaxSize().testTag("screen-guide")) {
        Box(Modifier.fillMaxSize().padding(horizontal = Sohva.spacing.safeHorizontal, vertical = Sohva.spacing.safeVertical)) {
            when (phase) {
                GuidePhase.LOADING -> Text(
                    stringResource(R.string.guide_loading),
                    Modifier.padding(28.dp).testTag("guide-loading"),
                    style = Sohva.typography.body,
                    color = Sohva.palette.textMuted,
                )
                GuidePhase.EMPTY_LIBRARY -> EmptyLibrary(model, navigation)
                GuidePhase.READY -> GuideContent(model, navigation)
            }
        }
    }
}

@Composable
private fun GuideContent(model: GuideModel, navigation: GuideNavigation) {
    val view by model.list.collectAsStateWithLifecycle()
    val railOpen by model.overlays.railOpen.collectAsStateWithLifecycle()
    val optionsOpen by model.overlays.optionsOpen.collectAsStateWithLifecycle()
    val actionsTarget by model.overlays.actions.collectAsStateWithLifecycle()
    val dial by model.overlays.dial.collectAsStateWithLifecycle()
    val reading by model.readingNotice.collectAsStateWithLifecycle()
    // Kept while the rail is closed, so reopening shows the same viewport (GUIDE-FR-25).
    val railState = rememberLazyListState()
    val railOptions = remember { FocusRequester() }
    val heroWatch = remember { FocusRequester() }
    val actions = remember(model, navigation) {
        object : RowActions {
            override fun play(row: GuideRowData) = navigation.play(row.key)

            override fun playArchive(row: GuideRowData, programme: GuideProgramme) =
                navigation.playArchive(row.key, programme.start, programme.stop)

            override fun openActions(row: GuideRowData, column: Int) {
                val programme = model.programmes.schedule(row.channel.epgId)?.programmes?.getOrNull(column) ?: return
                model.overlays.openActions(ActionsTarget(row, programme, column))
            }

            override fun openRail() = model.overlays.openRail()

            override fun toHero() {
                runCatching { heroWatch.requestFocus() }
            }
        }
    }
    BackHandler(enabled = optionsOpen) { placeAfterSheet(model, model.overlays.closeOptions(), railOptions) }
    Column(Modifier.fillMaxSize()) {
        GuideHero(model, actions, heroWatch)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (railOpen) GuideRail(model, railState, railOptions)
            val current = view
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (reading) {
                    Text(
                        stringResource(R.string.guide_loading),
                        Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        style = Sohva.typography.label,
                        color = Sohva.palette.textMuted,
                    )
                }
                if (current == null || current.size == 0) {
                    val missing by model.groupsMissing.collectAsStateWithLifecycle()
                    Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (missing && current != null) stringResource(R.string.profile_groups_missing) else emptyText(current),
                            Modifier.testTag("guide-list-empty"),
                            style = Sohva.typography.body,
                            color = Sohva.palette.textMuted,
                        )
                    }
                } else {
                    GuideGrid(model, current, actions, Modifier.fillMaxWidth().weight(1f))
                }
                GuideKeyHints()
            }
        }
    }
    DialOverlay(dial, Modifier.zIndex(1f).padding(top = 12.dp, end = 12.dp))
    if (optionsOpen) {
        Box(Modifier.fillMaxSize().zIndex(2f)) {
            GuideOptions(model, navigation) { placeAfterSheet(model, it, railOptions) }
        }
    }
    actionsTarget?.let { target ->
        GuideActionsDialog(model, target, actions) {
            // Back to the block the dialog came from (guide.md §12 item 6).
            model.overlays.closeActions()?.let { model.focusRow(it.row.index, it.column) }
        }
    }
}

/** Focus placed before the sheet hides (GUIDE-FR-93). */
private fun placeAfterSheet(model: GuideModel, target: GuideOverlays.SheetReturn, railOptions: FocusRequester) {
    when (target) {
        GuideOverlays.SheetReturn.FirstRow -> model.focusRow(0, GuideModel.CHANNEL)
        GuideOverlays.SheetReturn.RailOptions -> runCatching { railOptions.requestFocus() }
        GuideOverlays.SheetReturn.SelectedRow -> model.selection.value?.row?.index?.let { model.focusRow(it, GuideModel.KEEP) }
    }
}

@Composable
private fun emptyText(view: ListView?): String = when (view?.entry) {
    RailEntry.Favourites -> stringResource(R.string.guide_empty_favourites)
    RailEntry.Recent -> stringResource(R.string.guide_empty_recent)
    null -> stringResource(R.string.guide_loading)
    else -> stringResource(R.string.guide_empty_filtered)
}
