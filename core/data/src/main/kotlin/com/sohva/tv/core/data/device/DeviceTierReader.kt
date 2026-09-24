package com.sohva.tv.core.data.device

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import com.sohva.tv.core.model.device.DeviceTierInputs

/** Reads the tier inputs of plan/07 §2.2. Binder calls: run once, off the main thread. */
class DeviceTierReader(private val context: Context) {
    fun read(): DeviceTierInputs {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val animatorScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return DeviceTierInputs(
            isLowRamDevice = activityManager.isLowRamDevice,
            memoryClassMb = activityManager.memoryClass,
            totalMemBytes = memory.totalMem,
            animatorDurationScale = animatorScale,
        )
    }
}
