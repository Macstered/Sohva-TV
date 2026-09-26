package com.sohva.tv.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sohva.tv.core.model.player.BufferProfile

/** Builds the one ExoPlayer (spec 30 PLAY-FR-19) for a buffer profile. */
@OptIn(UnstableApi::class)
internal object PlayerFactory {
    fun create(context: Context, env: PlayerEnvironment, registry: StreamRegistry, profile: BufferProfile): ExoPlayer {
        // Every request of the stream, manifest and segments, goes through the resolver (L-23).
        // Addon streams go through their own transport (spec 50 ADDON-FR-95), marked by the registry.
        val addon = (env.callFactory as? okhttp3.OkHttpClient)?.let(AddonTransport::client) ?: env.callFactory
        val http = OkHttpDataSource.Factory(AddonTransport.route(env.callFactory, addon))
        val sources = DefaultMediaSourceFactory(ResolvingDataSource.Factory(http, registry.resolver))
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        return ExoPlayer.Builder(context, renderers, sources)
            .setLoadControl(loadControl(profile, env.lowMemory))
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            // Audio focus and "becoming noisy" on both players (PLAY-FR-135, Q-05 recommended).
            .setAudioAttributes(audio, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * The profile's durations with an explicit byte cap (spec 30 §9): Media3 holds the buffer on the
     * Java heap, and its default video target alone is about 125 MiB — over a 1–2 GB box's budget.
     */
    fun loadControl(profile: BufferProfile, lowMemory: Boolean): LoadControl {
        val builder = DefaultLoadControl.Builder()
            .setTargetBufferBytes(profile.targetBytes(lowMemory))
            .setPrioritizeTimeOverSizeThresholds(false)
        if (profile.minBufferMs != null && profile.maxBufferMs != null && profile.startMs != null && profile.rebufferMs != null) {
            builder.setBufferDurationsMs(profile.minBufferMs!!, profile.maxBufferMs!!, profile.startMs!!, profile.rebufferMs!!)
        }
        return builder.build()
    }
}
