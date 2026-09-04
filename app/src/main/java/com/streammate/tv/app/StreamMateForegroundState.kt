package com.streammate.tv.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tells deferrable background maintenance whether a viewer is using the app.
 * Activity starts and stops are used instead of a Compose route so every
 * screen, including playback, receives the same protection.
 */
object StreamMateForegroundState : Application.ActivityLifecycleCallbacks {
    private val startedActivities = AtomicInteger(0)

    val isForeground: Boolean
        get() = startedActivities.get() > 0

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities.incrementAndGet() == 1) {
            CatalogueMetadataScheduler.pause(activity.applicationContext)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val remaining = startedActivities.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
        if (remaining == 0 && !activity.isChangingConfigurations) {
            CatalogueMetadataScheduler.start(activity.applicationContext)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

fun Application.installCatalogueMetadataLifecycle(context: Context = this) {
    CatalogueMetadataScheduler.initialize(context)
    registerActivityLifecycleCallbacks(StreamMateForegroundState)
}
