package com.streammate.tv.app

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streammate.tv.R

/** The broadcast the close button on the corner sends. */
const val ACTION_PICTURE_IN_PICTURE_CLOSE = "com.streammate.tv.action.PICTURE_IN_PICTURE_CLOSE"

/**
 * Home while watching: the picture shrinks to a corner over the launcher
 * rather than stopping. The app says when a stream is on screen, the
 * activity acts on the leave hint, and both read the result here.
 *
 * The corner carries its own close button, because a TV launcher need not
 * offer one and the viewer would otherwise have no way back out. Closing
 * stops the activity and with it the stream, so a provider's connection is
 * not held by a window nobody is watching.
 */
class PictureInPictureState {
    /** A stream is on screen and the viewer allows the corner; set by the app. */
    var allowed by mutableStateOf(false)
    /** The activity is in the corner right now; set by the activity. */
    var active by mutableStateOf(false)

    /** True when the activity should enter the corner on a leave hint. */
    fun shouldEnter(): Boolean = allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enter(activity: Activity): Boolean {
        if (!shouldEnter()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching { activity.enterPictureInPictureMode(pictureInPictureParams(activity)) }
            .getOrDefault(false)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun pictureInPictureParams(context: Context): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(listOf(closeAction(context)))
        .build()

@RequiresApi(Build.VERSION_CODES.O)
private fun closeAction(context: Context): RemoteAction {
    val label = context.getString(R.string.picture_in_picture_close)
    val intent = Intent(ACTION_PICTURE_IN_PICTURE_CLOSE).setPackage(context.packageName)
    val pending = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return RemoteAction(
        Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
        label,
        label,
        pending,
    )
}
