package com.sohva.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The package under test: the release-like variants keep the release application id. */
const val TARGET_PACKAGE: String = "com.streammate.tv"

private const val WAIT_MS = 10_000L

/** Cold start to Home's rail, found by its test tag (exposed as a resource id by the root). */
fun MacrobenchmarkScope.startToHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res("home-live")), WAIT_MS)
}

/**
 * The M0 browse journey, D-pad only: down the rail, into each destination and back. Grows with
 * every milestone's screens (plan/05 §4.9 journey 2).
 */
fun MacrobenchmarkScope.browseRail() {
    repeat(6) { step ->
        repeat(step) { device.pressDPadDown() }
        device.pressDPadCenter()
        // Live TV is a real screen from M2; the others are placeholders until their milestones.
        device.wait(Until.hasObject(By.res(if (step == 0) "screen-guide" else "placeholder-back")), WAIT_MS)
        device.pressBack()
        device.wait(Until.hasObject(By.res("home-live")), WAIT_MS)
    }
}
