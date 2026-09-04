package com.streammate.tv.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import com.streammate.tv.R
import com.streammate.tv.iptv.playback.PlaybackRepository
import kotlinx.coroutines.CancellationException

class ExternalPlayerLauncher(
    context: Context,
    private val playbackRepository: PlaybackRepository,
) {
    private val applicationContext = context.applicationContext

    suspend fun launch(channelId: String): Result<Unit> = try {
        val source = requireNotNull(playbackRepository.sourceFor(channelId)) {
            applicationContext.getString(R.string.external_channel_unavailable)
        }
        source.use {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(source.streamUrl), "video/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (source.headers.isNotEmpty()) {
                val headerBundle = Bundle().apply {
                    source.headers.forEach { (name, value) -> putString(name, value) }
                }
                intent.putExtra(Browser.EXTRA_HEADERS, headerBundle)
                intent.putExtra(
                    "headers",
                    source.headers.flatMap { (name, value) -> listOf(name, value) }.toTypedArray(),
                )
            }
            applicationContext.startActivity(intent)
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
