package com.sohva.tv.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as SohvaApplication).graph
        val metrics = resources.displayMetrics
        val screen = IntSize(metrics.widthPixels, metrics.heightPixels)
        OpenRequest.of(intent)?.let { graph.openRequest.value = it }
        setContent { SohvaRoot(graph, this, screen) }
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

    override fun onAppDrawn() {
        // A flat colour costs a tile-based GPU nothing; the launch picture would be repainted
        // under every later frame (design/01 §2, lessons of beta 23).
        window.setBackgroundDrawable(WINDOW_COLOR.toDrawable())
        reportFullyDrawn()
        (application as SohvaApplication).graph.afterFirstFrame()
    }

    private companion object {
        const val WINDOW_COLOR: Int = 0xFF05070D.toInt()
    }
}
