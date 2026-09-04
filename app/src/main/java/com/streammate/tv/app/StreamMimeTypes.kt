package com.streammate.tv.app

import androidx.media3.common.MimeTypes

/**
 * Infers the container type of a provider stream from its URL.
 *
 * Playback rewrites every [androidx.media3.common.MediaItem] URI to an
 * extensionless `streammate://channel/<id>` placeholder so that credentials
 * never reach the media session. That leaves `DefaultMediaSourceFactory` with
 * nothing to infer a container from, so without an explicit MIME type it always
 * builds a progressive source — and HLS or DASH channels can never play even
 * though the extractors are on the classpath.
 *
 * Passing the real container type through `MediaItem.Builder.setMimeType`
 * restores correct source selection while keeping the credentialed URL out of
 * the session. Returns `null` when the container cannot be determined, which
 * preserves the previous progressive behaviour for raw MPEG-TS endpoints.
 */
object StreamMimeTypes {

    fun fromStreamUrl(streamUrl: String?): String? {
        val path = streamUrl
            ?.substringBefore('#')
            ?.substringBefore('?')
            ?.trimEnd('/')
            ?.lowercase()
            ?: return null
        return when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".ism") || path.endsWith(".isml") -> MimeTypes.APPLICATION_SS
            path.endsWith("/manifest") -> MimeTypes.APPLICATION_SS
            else -> null
        }
    }
}
