package com.sohva.tv.core.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The screen's side of the engine (spec 30 PLAY-FR-11): one [MediaController] for a player session,
 * reused across zaps and released when the viewer leaves the player. Main thread only.
 */
@OptIn(UnstableApi::class)
class PlaybackClient(context: Context) {
    private val app = context.applicationContext
    private var pending: ListenableFuture<MediaController>? = null

    /** Connects (or returns the connection in progress). Throws when the controller cannot be built. */
    suspend fun connect(listener: MediaController.Listener): MediaController {
        val future = pending ?: MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .setListener(listener)
            .buildAsync()
            .also { pending = it }
        return suspendCancellableCoroutine { continuation ->
            future.addListener({
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    pending = null
                    continuation.resumeWithException(e.cause ?: e)
                }
            }, ContextCompat.getMainExecutor(app))
        }
    }

    fun release() {
        pending?.let { MediaController.releaseFuture(it) }
        pending = null
    }
}
