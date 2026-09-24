package com.sohva.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline and startup profiles from D-pad journeys (plan/05 §4.9). Run on an
 * API 33+ rebuild emulator: `ANDROID_SERIAL=emulator-5572 ./gradlew :app:generateBaselineProfile`.
 * Review and commit the result; never hand-write it (the hand-written one missed the guide).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        startToHome()
        device.pressDPadDown()
    }

    @Test
    fun browse() = rule.collect(packageName = TARGET_PACKAGE) {
        startToHome()
        browseRail()
    }
}
