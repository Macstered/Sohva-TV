package com.sohva.tv.app.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohva.tv.app.AppGraph
import com.sohva.tv.feature.profiles.PinGateKind
import com.sohva.tv.feature.profiles.PinModel
import com.sohva.tv.feature.profiles.PinScreen
import com.sohva.tv.feature.profiles.ProfilePickerScreen
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who is watching at start (spec 04 PROF-FR-10, -13), before the start stack exists: the chosen
 * profile's last channel opens the stack (decision "Start-time picker"). A choice that needs the
 * PIN shows the PIN screen; its Back returns to the picker. Back on the picker leaves the app.
 */
@Composable
fun StartQuestion(graph: AppGraph, onAnswered: (lastChannel: String?) -> Unit, onLeave: () -> Unit) {
    val profiles = graph.data.profiles
    val household by profiles.household.collectAsState()
    val scope = rememberCoroutineScope()
    var pinFor by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultName = stringResource(R.string.profile_default_name)
    val answer: suspend (String) -> Unit = { id ->
        graph.enterProfile(id)
        val last = withContext(graph.dispatchers.io) { graph.data.preferences.lastChannel(id).first() }
        graph.startAnswered = true
        onAnswered(last)
    }
    val target = pinFor
    if (target == null) {
        BackHandler(onBack = onLeave)
        ProfilePickerScreen(pickerTiles(household, defaultName), household.active.id, onChoose = { id ->
            scope.launch { if (profiles.entryNeedsPin(id)) pinFor = id else answer(id) }
        })
    } else {
        BackHandler { pinFor = null }
        val model = viewModel(key = "start-pin:$target") {
            PinModel(profiles::verifyPin, profiles.household.value.pinConfigured) { scope.launch { answer(target) } }
        }
        val name = household.shown.firstOrNull { it.id == target }?.displayName(defaultName) ?: target
        PinScreen(model, PinGateKind.SWITCH_PROFILE, name) { pinFor = null }
    }
}
