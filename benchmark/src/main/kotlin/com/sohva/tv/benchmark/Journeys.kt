package com.sohva.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The package under test: the release-like variants keep the release application id. */
const val TARGET_PACKAGE: String = "com.streammate.tv"

private const val WAIT_MS = 10_000L

/** Cold start to Home, found by its test tag (exposed as a resource id by the root). */
fun MacrobenchmarkScope.startToHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res("screen-home")), WAIT_MS)
    // Home places focus once its rows settle, after "fully drawn"; waiting for it keeps the
    // trace running until the frame that closes the start-up metric has ended.
    device.wait(Until.hasObject(By.focused(true)), WAIT_MS)
    device.waitForIdle()
}

/** The rail items top to bottom (spec 01 SHELL-FR-60), as the test tags name them. */
private val RAIL = listOf("home-live", "home-sportmate", "home-movies", "home-series", "home-search", "home-discover", "home-settings")

/**
 * Focus on rail item [tag] as the viewer reaches it: Home focuses its content (spec 02 §3.4), so
 * Left until the rail has focus, then Up or Down to [tag].
 */
fun MacrobenchmarkScope.focusRailItem(tag: String) {
    val rail = RAIL.filter { device.hasObject(By.res(it)) }
    val focusedIndex = { rail.indexOfFirst { device.hasObject(By.res(it).focused(true)) } }
    device.wait(Until.hasObject(By.focused(true)), WAIT_MS)
    // Each press waits for its effect: the rail expands as it takes focus, and a stale read of
    // the focused item would send one press too many.
    repeat(12) {
        if (focusedIndex() < 0) {
            device.pressDPadLeft()
            device.wait(Until.hasObject(By.res(Pattern.compile(rail.joinToString("|"))).focused(true)), 1_000)
        }
    }
    check(focusedIndex() >= 0) { "the rail did not take focus" }
    val target = rail.indexOf(tag)
    repeat(rail.size) {
        val at = focusedIndex()
        if (at == target || at < 0) return@repeat
        if (at < target) device.pressDPadDown() else device.pressDPadUp()
        val next = if (at < target) at + 1 else at - 1
        device.wait(Until.hasObject(By.res(rail[next]).focused(true)), 2_000)
    }
    check(device.wait(Until.hasObject(By.res(tag).focused(true)), WAIT_MS)) {
        "$tag not focused; focused: ${device.findObject(By.focused(true))?.resourceName}"
    }
}

/**
 * The M0 browse journey, D-pad only: down the rail, into each destination and back. Grows with
 * every milestone's screens (plan/05 §4.9 journey 2).
 */
fun MacrobenchmarkScope.browseRail() {
    val screens = listOf(
        "home-live" to "screen-guide", "home-sportmate" to "screen-today", "home-movies" to "screen-movies",
        "home-series" to "screen-series", "home-search" to "screen-search", "home-discover" to "screen-discover",
    )
    for ((item, screen) in screens) {
        // Discover is on the rail only where the build allows addons.
        if (!device.hasObject(By.res(item))) continue
        focusRailItem(item)
        device.pressDPadCenter()
        device.wait(Until.hasObject(By.res(screen)), WAIT_MS)
        device.pressBack()
        // Search's first Back may only close the keyboard (spec 03 §3).
        if (!device.wait(Until.hasObject(By.res("screen-home")), 2_000)) device.pressBack()
        device.wait(Until.hasObject(By.res("screen-home")), WAIT_MS)
    }
}
