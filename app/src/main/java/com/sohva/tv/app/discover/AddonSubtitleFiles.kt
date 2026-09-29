package com.sohva.tv.app.discover

import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.AppLocales
import com.sohva.tv.core.model.player.SubtitleText
import com.sohva.tv.feature.discover.DiscoverHost
import com.sohva.tv.feature.discover.net.SubtitleDownloader
import com.sohva.tv.feature.discover.protocol.AddonException
import com.sohva.tv.feature.discover.ui.failureRes
import com.sohva.tv.feature.player.SubtitleDownload
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.withContext

/**
 * A subtitle file from an addon (spec 50 ADDON-FR-101), for an addon playback and for a library
 * title (spec 30 PLAY-FR-141) alike: fetched through Discover's subtitle file client, its provider
 * checked again once it has come, then decoded and recognised off the main thread.
 */
internal object AddonSubtitleFiles {
    suspend fun download(graph: AppGraph, host: DiscoverHost, profile: String, url: String, provider: String?): SubtitleDownload {
        val texts = AppLocales.texts(graph.app)
        val failed = SubtitleDownload.Failed(texts.getString(R.string.addon_error_operation))
        val address = SubtitleDownloader.parse(url) ?: return failed
        return try {
            val bytes = host.subtitleFiles.get(address)
            // FR-101: the provider may have gone while the file came.
            if (provider != null) {
                val still = withContext(graph.dispatchers.io) { host.installations.find(profile, provider) }
                if (still == null || !still.enabled || !host.access.allowed(profile)) return failed
            }
            val decoded = withContext(graph.dispatchers.ui) {
                SubtitleText.decode(bytes)?.let { text -> SubtitleText.detect(text)?.let { SubtitleDownload.Ready(text, it) } }
            }
            decoded ?: SubtitleDownload.Failed(texts.getString(R.string.addon_error_manifest))
        } catch (e: AddonException) {
            SubtitleDownload.Failed(texts.getString(failureRes(e.failure)))
        }
    }
}
