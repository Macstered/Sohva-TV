package com.sohva.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvActionButton
import com.sohva.tv.ui.design.components.TvIcons
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.requestFocusWhenAttached
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva
import java.util.Locale

/**
 * Settings (spec 70 §4.1, settings.md §1–2): a header, the section rail and one pane. Only the
 * selected section is composed (SET-FR-16). Back closes the source page first, then leaves
 * (SET-FR-03); dialogs close themselves before either.
 */
@Composable
fun SettingsScreen(model: SettingsModel, onBack: () -> Unit) {
    val state by model.ui.collectAsState()
    val paneStart = remember { FocusRequester() }
    val railSelected = remember { FocusRequester() }
    BackHandler(enabled = state.page != null) { model.closePage() }
    ScreenBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = Sohva.spacing.safeHorizontal, vertical = Sohva.spacing.safeVertical).testTag("screen-settings"),
        ) {
            SettingsHeader(state.section, onBack)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                SettingsRail(state, model::select, railSelected, Modifier.width(214.dp).fillMaxHeight())
                SettingsPane(state, model, paneStart, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    // The phone page never outlives the screen or the app's visibility (spec 11 PHONE-FR-03, PHONE-FR-15).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.closePhoneSetup() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            model.closePhoneSetup()
        }
    }
    // Focus goes where the model says, once per command (SET-FR-02, SET-FR-12).
    LaunchedEffect(state.focus.serial) {
        if (state.focus.target == FocusTarget.SectionStart && !paneStart.requestFocusWhenAttached()) railSelected.requestFocusWhenAttached()
    }
}

@Composable
private fun SettingsHeader(section: SettingsSection, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.settings_title),
            style = Sohva.typography.display.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            color = Sohva.palette.textPrimary,
            maxLines = 1,
        )
        Text(
            // Upper-cased in the interface locale, read observably (SET-FR-13).
            "›  " + stringResource(section.label).uppercase(ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT),
            Modifier.weight(1f).padding(start = 14.dp, bottom = 6.dp),
            style = Sohva.typography.label.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
            color = Sohva.palette.textDim,
            maxLines = 1,
        )
        TvActionButton(stringResource(R.string.action_back), onBack, Modifier.testTag("settings-back"), icon = TvIcons.Back, compact = true)
    }
}

/** Rows of 41 dp; OK selects, focus alone does not (SET-FR-12). Entering it lands on the selected row (SET-FR-15). */
@Composable
private fun SettingsRail(state: SettingsState, onSelect: (SettingsSection) -> Unit, selected: FocusRequester, modifier: Modifier) {
    val sections = remember(state.accounts) { SettingsSection.visible(state.accounts) }
    LazyColumn(
        modifier.focusProperties { onEnter = { selected.requestFocus() } },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(sections, key = { it.name }) { section ->
            val isSelected = section == state.section
            TvListRow(
                label = stringResource(section.label),
                onClick = { onSelect(section) },
                modifier = Modifier.height(41.dp).then(if (isSelected) Modifier.focusRequester(selected) else Modifier)
                    .testTag("settings-section-${section.tag}"),
                icon = section.icon,
                state = SurfaceState(selected = isSelected),
            )
        }
    }
}

/** The selected section; scrolls so the focused control stays in view (SET-FR-16). */
@Composable
private fun SettingsPane(
    state: SettingsState,
    model: SettingsModel,
    start: FocusRequester,
    modifier: Modifier,
) {
    Column(
        modifier
            // Right from the rail comes back to the control focused last (SET-FR-15); Left lands on
            // the selected rail row through the rail's own enter rule.
            .focusRestorer(start)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.section) {
            SettingsSection.SOURCES -> if (state.page == null) PlaylistsList(state, model, start) else SourcePage(state, model, start)
            SettingsSection.GENERAL -> GeneralPane(state, model, start)
            else -> PendingSection(state.section)
        }
    }
}

/** A section whose milestone has not come yet: its name and beta 23's "Coming in a later phase". */
@Composable
private fun PendingSection(section: SettingsSection) {
    SettingsOverline(stringResource(section.label))
    Text(
        stringResource(R.string.home_coming_soon),
        Modifier.padding(horizontal = 14.dp).testTag("settings-pending-${section.tag}"),
        style = Sohva.typography.body,
        color = Sohva.palette.textMuted,
    )
    Spacer(Modifier.height(8.dp))
}
