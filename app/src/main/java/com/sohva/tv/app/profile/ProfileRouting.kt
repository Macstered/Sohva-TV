package com.sohva.tv.app.profile

import android.widget.Toast
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.navigation.AppRoute
import com.sohva.tv.core.model.profile.ChannelAdmission
import com.sohva.tv.feature.player.ArchiveWindow
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.navigation.BackStack
import kotlinx.coroutines.launch

/** The profile's group check first, then the lock (spec 01 SHELL-FR-20, spec 04 PROF-FR-43). */
suspend fun AppGraph.admit(channelKey: String): ChannelAdmission = when {
    !data.live.allowed(channelKey) -> ChannelAdmission.REFUSED
    data.profiles.isLocked(channelKey) -> ChannelAdmission.LOCKED
    else -> ChannelAdmission.PLAY
}

/** The refusal toast (spec 01 SHELL-FR-34). */
fun AppGraph.refuseChannel() {
    Toast.makeText(app, app.getString(R.string.profile_content_blocked), Toast.LENGTH_SHORT).show()
}

/**
 * Every live start outside the player goes through here (spec 01 SHELL-FR-20, -21): guide focus
 * when [forGuide], then the group check (toast), then the lock (PIN gate), then the player.
 */
class ChannelStarter(private val graph: AppGraph, private val stack: BackStack<AppRoute>) {
    fun play(channelKey: String, forGuide: Boolean, archive: ArchiveWindow? = null) {
        if (forGuide) graph.guideFocusChannel = channelKey
        graph.appScope.launch(graph.dispatchers.main) {
            when (graph.admit(channelKey)) {
                ChannelAdmission.REFUSED -> graph.refuseChannel()
                ChannelAdmission.LOCKED ->
                    stack.push(AppRoute.PinGate(channelKey, archive, replacePlayer = false, rememberForGuide = forGuide, recordWatched = forGuide))
                ChannelAdmission.PLAY -> stack.push(player(channelKey, forGuide, archive, forGuide))
            }
        }
    }

    companion object {
        /** Catch-up always pops on Back (spec 01 SHELL-FR-25); a reminder's playback is not a recent channel (CHAN-FR-61). */
        fun player(channelKey: String, forGuide: Boolean, archive: ArchiveWindow?, record: Boolean): AppRoute.Player =
            AppRoute.Player(channelKey, returnToGuide = forGuide && archive == null, archive = archive, recordWatched = record, admitted = true)
    }
}

/** Unlocking a channel's gate (spec 01 SHELL-FR-23): the player replaces the gate, and with [AppRoute.PinGate.replacePlayer] the player under it. */
fun AppGraph.unlockChannel(gate: AppRoute.PinGate, stack: BackStack<AppRoute>) {
    if (stack.top.route != gate) return
    if (gate.rememberForGuide) guideFocusChannel = gate.channelKey
    val player = ChannelStarter.player(gate.channelKey, gate.rememberForGuide, gate.archive, gate.recordWatched)
    if (gate.replacePlayer && stack.size > 1 && stack.entries[stack.size - 2].route is AppRoute.Player) stack.pop()
    stack.replaceTop(player)
}

/**
 * Opens Settings, channel management or the library manager (spec 01 SHELL-FR-32, spec 04
 * PROF-FR-35): behind the PIN when the active profile is restricted and a PIN exists.
 */
fun AppGraph.openManaged(route: AppRoute, stack: BackStack<AppRoute>) {
    appScope.launch(dispatchers.main) {
        stack.push(if (data.profiles.managementNeedsPin()) AppRoute.ProfileGate(null, route) else route)
    }
}

/**
 * Switches to [targetId] (spec 01 SHELL-FR-33, spec 04 §4.2): through the PIN when entering it
 * needs one, else at once. Switching into a restricted profile from Settings returns to Home
 * (decision "Spec 04 quirks fixed").
 */
fun AppGraph.switchProfile(targetId: String, stack: BackStack<AppRoute>, fromSettings: Boolean) {
    appScope.launch(dispatchers.main) {
        if (targetId == data.profiles.activeId) return@launch
        if (data.profiles.entryNeedsPin(targetId)) {
            stack.push(AppRoute.ProfileGate(targetId, null))
            return@launch
        }
        enterProfile(targetId)
        if (fromSettings && targetId in data.profiles.restrictedIds()) stack.resetTo(listOf(AppRoute.Home))
    }
}

/**
 * Makes [profileId] active and forgets what the process kept for the previous one: the guide's
 * focus and kept rows, the walls' browse sessions (spec 04 §9: every per-profile flow at once).
 * Home and Continue watching re-read on the switch themselves.
 */
suspend fun AppGraph.enterProfile(profileId: String) {
    if (profileId != data.profiles.activeId) switchedAt.set(android.os.SystemClock.elapsedRealtime())
    data.profiles.switchTo(profileId)
    guideFocusChannel = null
    keptRows.clear()
    browseSessions.values.forEach { it.clear() }
}
