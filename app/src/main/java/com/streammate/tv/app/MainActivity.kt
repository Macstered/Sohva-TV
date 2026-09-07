package com.streammate.tv.app

import android.content.Intent
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {

    // Below API 33 nothing else applies the chosen interface language, and it
    // has to be in place before any resource is resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptOpenRequest(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StreamMateApplication).container
        container.openRequests.offer(OpenRequest.fromIntent(intent))
        setContent {
            var showLaunchSplash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(LAUNCH_SPLASH_DURATION_MILLIS)
                showLaunchSplash = false
            }
            // The whole app is laid out at the chosen interface size: one
            // density for every screen, so layouts and text shrink together.
            val interfaceScale by container.preferencesRepository.preferences
                .map { it.interfaceScale }
                .distinctUntilChanged()
                .collectAsStateWithLifecycle(initialValue = InterfaceScale.DEFAULT)
            InterfaceScaled(interfaceScale) {
                if (showLaunchSplash) {
                    StreamMateTheme { StreamMateLaunchScreen() }
                } else {
                    StreamMateApp(container)
                }
            }
        }
    }
}

private const val LAUNCH_SPLASH_DURATION_MILLIS = 2_000L

/** A reminder tapped while the app is already up arrives here, not in onCreate. */
private fun MainActivity.acceptOpenRequest(intent: Intent?) {
    (application as StreamMateApplication).container.openRequests.offer(OpenRequest.fromIntent(intent))
}

/** [content] laid out at [scale]: the device density times its factor, with the font scale as it is. */
@Composable
internal fun InterfaceScaled(scale: InterfaceScale, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = remember(base, scale) { Density(base.density * scale.factor, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
