package com.sohva.tv.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toDrawable
import com.sohva.tv.app.shell.RootHost
import com.sohva.tv.app.reminder.OpenRequest
import com.sohva.tv.app.shell.SohvaRoot

/**
 * The one activity. Its window shows the launch picture until the app has drawn; the content is
 * set at once (spec 01 SHELL-FR-03). No key callback is overridden: Back goes through the back
 * dispatcher (SHELL-FR-90).
 */
class MainActivity : ComponentActivity(), RootHost {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.attach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as SohvaApplication).graph
        graph.markLaunch()
        val metrics = resources.displayMetrics
        val screen = IntSize(metrics.widthPixels, metrics.heightPixels)
        OpenRequest.of(intent)?.let { graph.openRequest.value = it }
        // A corner closed with the activity never reported leaving it (PLAY-FR-111).
        graph.inPictureInPicture.value = false
        setContent { SohvaRoot(graph, this, screen) }
        // The corner's Close (spec 30 PLAY-FR-110): only this app can send it.
        androidx.core.content.ContextCompat.registerReceiver(
            this, closeCorner, android.content.IntentFilter(ACTION_CLOSE_CORNER), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The TV's own zone changed (spec 74 L10N-FR-33): times on screen follow at once.
        androidx.core.content.ContextCompat.registerReceiver(
            this, zoneChanged, android.content.IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private val zoneChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            (application as SohvaApplication).graph.refreshDeviceZone()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(zoneChanged)
        unregisterReceiver(closeCorner)
        super.onDestroy()
    }

    /** singleTask: a notification tap or a bring-forward reaches the running activity here (spec 22 §7). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        OpenRequest.of(intent)?.let { (application as SohvaApplication).graph.openRequest.value = it }
    }

    override fun onStart() {
        super.onStart()
        val graph = (application as SohvaApplication).graph
        graph.inForeground.value = true
        // A zone changed while the app was away arrives with no broadcast to this activity.
        graph.refreshDeviceZone()
        // The enrichment gives way while the viewer is here (spec 41 META-FR-62).
        if (graph.flags.metadataWorker) graph.metadata.scheduler.onReturn()
    }

    override fun onStop() {
        val graph = (application as SohvaApplication).graph
        graph.inForeground.value = false
        if (graph.flags.metadataWorker && !isChangingConfigurations) graph.metadata.scheduler.onLeave()
        super.onStop()
    }

    override fun leave() = finish()

    private val closeCorner = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    /**
     * The corner's parameters (spec 30 PLAY-FR-110): 16:9, one Close action, the whole window as
     * the picture's source; [autoEnter] from Android 12, where Home enters the corner by itself.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun cornerParams(autoEnter: Boolean): android.app.PictureInPictureParams {
        val close = android.app.PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_CLOSE_CORNER).setPackage(packageName), android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val label = getString(com.sohva.tv.ui.design.R.string.picture_in_picture_close)
        val action = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), label, label, close,
        )
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(listOf(action))
            .setSourceRectHint(android.graphics.Rect(0, 0, window.decorView.width, window.decorView.height))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    /**
     * From the first frame on, Android 12+ keeps auto-enter in step with "a player is on top and
     * Keep watching in a corner is on"; below 12 the leave hint enters the corner.
     */
    private fun followCorner() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val graph = (application as SohvaApplication).graph
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(graph.playerOnTop, graph.pictureInPictureOn) { top, on -> top && on }
                .distinctUntilChanged()
                .collect { allowed -> runCatching { setPictureInPictureParams(cornerParams(allowed)) } }
        }
    }

    /**
     * Home while a player is on top and the setting is on; also from Android 12 when the system did
     * not enter the corner by itself (TV launchers need not honour auto-enter). A TV that refuses is
     * ignored.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val graph = (application as SohvaApplication).graph
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) return
        if (!graph.playerOnTop.value || !graph.pictureInPictureOn.value) return
        runCatching { enterPictureInPictureMode(cornerParams(autoEnter = false)) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        (application as SohvaApplication).graph.inPictureInPicture.value = isInPictureInPictureMode
    }

    override fun onAppDrawn() {
        // A flat colour costs a tile-based GPU nothing; the launch picture would be repainted
        // under every later frame (design/01 §2, lessons of beta 23).
        window.setBackgroundDrawable(WINDOW_COLOR.toDrawable())
        reportFullyDrawn()
        (application as SohvaApplication).graph.afterFirstFrame()
        followCorner()
    }

    companion object {
        private const val WINDOW_COLOR: Int = 0xFF05070D.toInt()

        /** The corner's Close (PLAY-FR-110), package-scoped. */
        const val ACTION_CLOSE_CORNER: String = "com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE"
    }
}
