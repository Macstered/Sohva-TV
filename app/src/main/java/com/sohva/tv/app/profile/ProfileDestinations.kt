package com.sohva.tv.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.feature.profiles.PickerTile
import com.sohva.tv.feature.profiles.PinGateKind
import com.sohva.tv.feature.profiles.PinModel
import com.sohva.tv.feature.profiles.PinScreen
import com.sohva.tv.feature.profiles.ProfilePickerScreen
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.launch

/** The PIN in front of a locked channel (spec 04 §4.6); Back pops without playing (spec 01 §3.3). */
@Composable
fun PinGateDestination(route: AppRoute.PinGate, stack: BackStack<AppRoute>, graph: AppGraph) {
    val generic = stringResource(R.string.generic_channel)
    val name by produceState(generic, route.channelKey) { value = graph.data.live.channel(route.channelKey)?.name ?: generic }
    val profiles = graph.data.profiles
    val model = viewModel { PinModel(profiles::verifyPin, profiles.household.value.pinConfigured) { graph.unlockChannel(route, stack) } }
    PinScreen(model, PinGateKind.LOCKED_CHANNEL, name) { stack.pop() }
}

/**
 * The PIN in front of entering a profile, or of Settings and the managers for a restricted
 * profile (spec 01 SHELL-FR-32, -33). A management gate the active profile does not need (a
 * restored Settings of an unrestricted profile) steps aside at once.
 */
@Composable
fun ProfileGateDestination(route: AppRoute.ProfileGate, stack: BackStack<AppRoute>, graph: AppGraph) {
    val profiles = graph.data.profiles
    var needed by remember { mutableStateOf(route.targetId != null) }
    LaunchedEffect(route) {
        if (route.targetId == null) {
            if (profiles.managementNeedsPin()) needed = true else if (stack.top.route == route) stack.replaceTop(route.then ?: AppRoute.Settings)
        }
    }
    if (!needed) return
    val household by profiles.household.collectAsState()
    val defaultName = stringResource(R.string.profile_default_name)
    val subject = (route.targetId?.let { id -> household.shown.firstOrNull { it.id == id } } ?: household.active).displayName(defaultName)
    val model = viewModel {
        PinModel(profiles::verifyPin, profiles.household.value.pinConfigured) {
            graph.appScope.launch(graph.dispatchers.main) {
                if (stack.top.route != route) return@launch
                val target = route.targetId
                if (target != null) {
                    graph.enterProfile(target)
                    stack.pop()
                } else {
                    stack.replaceTop(route.then ?: AppRoute.Settings)
                }
            }
        }
    }
    val kind = if (route.targetId != null) PinGateKind.SWITCH_PROFILE else PinGateKind.SETTINGS
    PinScreen(model, kind, subject) { stack.pop() }
}

/** Who is watching from the rail (spec 04 PROF-FR-14): the picker pops first, then the switch. */
@Composable
fun ProfilePickerDestination(stack: BackStack<AppRoute>, graph: AppGraph) {
    val household by graph.data.profiles.household.collectAsState()
    val tiles = pickerTiles(household, stringResource(R.string.profile_default_name))
    ProfilePickerScreen(tiles, household.active.id, onChoose = { id ->
        stack.pop()
        graph.switchProfile(id, stack, fromSettings = false)
    })
}

fun pickerTiles(household: Household, defaultName: String): List<PickerTile> =
    household.shown.map { PickerTile(it.id, it.displayName(defaultName), it.colorIndex) }
