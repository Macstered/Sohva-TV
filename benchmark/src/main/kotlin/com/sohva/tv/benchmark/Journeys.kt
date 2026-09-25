package com.sohva.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The package under test: the release-like variants keep the release application id. */
const val TARGET_PACKAGE: String = "com.streammate.tv"

private const val WAIT_MS = 10_000L

/** Cold start to Home, found by its test tag (exposed as a resource id by the root). */
fun MacrobenchmarkScope.startToHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res("screen-home")), WAIT_MS)
}

/** The rail items top to bottom (spec 01 SHELL-FR-60), as the test tags name them. */
private val RAIL = listOf("home-live", "home-sportmate", "home-movies", "home-series", "home-search", "home-discover", "home-settings")

/**
 * Focus on rail item [tag] as the viewer reaches it: Home focuses its content (spec 02 §3.4), so
 * Left until the rail has focus, Up to its first item, then Down to [tag].
 */
fun MacrobenchmarkScope.focusRailItem(tag: String) {
    val onRail = { RAIL.any { device.hasObject(By.res(it).focused(true)) } }
    device.wait(Until.hasObject(By.focused(true)), WAIT_MS)
    repeat(12) { if (!onRail()) device.pressDPadLeft() }
    check(onRail()) { "the rail did not take focus" }
    repeat(RAIL.size) { if (!device.hasObject(By.res(RAIL.first()).focused(true))) device.pressDPadUp() }
    repeat(RAIL.size) { if (!device.hasObject(By.res(tag).focused(true))) device.pressDPadDown() }
    check(device.wait(Until.hasObject(By.res(tag).focused(true)), WAIT_MS)) { "$tag not focused" }
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
        device.wait(Until.hasObject(By.res("screen-home")), WAIT_MS)
    }
}
