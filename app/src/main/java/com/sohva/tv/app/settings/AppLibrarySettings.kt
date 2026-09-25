package com.sohva.tv.app.settings

import android.content.Intent
import androidx.core.net.toUri
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.metadata.MetadataConfig
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.vod.PreferredCopy
import com.sohva.tv.feature.settings.LibrarySettingsServices
import com.sohva.tv.feature.settings.MetadataSettingsView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Library from the app graph (spec 41 §4.1, §4.15; spec 40 VOD-FR-32). Settings are
 * read and written on the io dispatcher; resets and the standing-copy pass run on the bulk one.
 */
class AppLibrarySettings(private val graph: AppGraph) : LibrarySettingsServices {
    private val io get() = graph.dispatchers.io
    private val metadata get() = graph.metadata

    override fun metadata(): Flow<MetadataSettingsView> = flow {
        metadata.settings.current()
        emitAll(metadata.settings.config.filterNotNull().map { view(it) })
    }.flowOn(io)

    private suspend fun view(config: MetadataConfig) = MetadataSettingsView(
        tmdbSwitch = config.tmdbSwitch,
        credential = config.credential.orEmpty(),
        tvmaze = config.tvmazeEnabled,
        language = config.language,
        keyRefused = metadata.settings.keyRefused(),
    )

    override suspend fun setTmdb(on: Boolean, typed: String): Outcome<Unit> = withContext(io) {
        save(typed, on, metadata.settings.current().tvmazeEnabled)
    }

    override suspend fun setTvmaze(on: Boolean): Outcome<Unit> = metadata.settings.setTvmaze(on)

    override suspend fun saveKey(typed: String): Outcome<Unit> = withContext(io) {
        val config = metadata.settings.current()
        save(typed, config.tmdbSwitch, config.tvmazeEnabled)
    }

    /** A changed key forgets only the misses: a new key may find what the old one missed (META-FR-05). */
    private suspend fun save(typed: String, tmdbOn: Boolean, tvmazeOn: Boolean): Outcome<Unit> =
        when (val saved = metadata.settings.save(typed, tmdbOn, tvmazeOn)) {
            is Outcome.Failed -> saved
            is Outcome.Ok -> {
                if (saved.value) metadata.service.credentialChanged()
                Outcome.Ok(Unit)
            }
        }

    override suspend fun testTmdb(typed: String): Outcome<Unit> = metadata.service.testCredential(typed)

    override suspend fun setLanguage(tag: String) {
        if (!metadata.settings.setLanguage(tag)) return
        metadata.reset.languageChanged()
        if (graph.flags.metadataWorker) metadata.scheduler.restart()
    }

    override suspend fun clearMetadata() = metadata.reset.clearAll()

    override fun preferredCopy(): Flow<PreferredCopy> = flow { emitAll(graph.data.preferences.preferredCopyChanges) }.flowOn(io)

    /** The standing copies follow the new preference, source by source, in the background (VOD-FR-26, -32). */
    override suspend fun setPreferredCopy(copy: PreferredCopy) {
        withContext(io) { graph.data.preferences.setPreferredCopy(copy) }
        graph.appScope.launch(graph.dispatchers.bulk) {
            graph.data.sources.all().forEach { metadata.passes.refreshSource(it.id, copy) }
        }
    }

    override fun openWeb(url: String) {
        // A TV without a browser has nothing to open it with; Settings stays as it was.
        runCatching { graph.app.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
