package com.streammate.tv.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    // Below API 33 nothing else applies the chosen interface language, and it
    // has to be in place before any resource is resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StreamMateApplication).container
        setContent {
            var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(LAUNCH_SPLASH_DURATION_MILLIS)
                showLaunchSplash = false
            }
            if (showLaunchSplash) {
                StreamMateTheme { StreamMateLaunchScreen() }
            } else {
                StreamMateApp(container)
            }
        }
    }
}

private const val LAUNCH_SPLASH_DURATION_MILLIS = 2_000L
