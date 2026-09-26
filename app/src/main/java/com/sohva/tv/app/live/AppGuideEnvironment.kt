package com.sohva.tv.app.live

import android.content.Intent
import androidx.core.net.toUri
import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.live.ChannelList
import com.sohva.tv.core.data.live.ListSpec
import com.sohva.tv.core.data.live.LiveReads
import com.sohva.tv.core.model.metadata.MediaType
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.reminder.Reminder
import com.sohva.tv.core.model.source.SourceHealth
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.core.model.time.TimeStyle
import com.sohva.tv.core.net.metadata.Artwork
import com.sohva.tv.core.net.metadata.MetadataRecord
import com.sohva.tv.core.sync.metadata.MetadataRequest
import com.sohva.tv.feature.live.GuideEnvironment
import com.sohva.tv.feature.live.HeroMetadata
import com.sohva.tv.feature.live.SavedSource
import com.sohva.tv.ui.design.text.TimeStyles
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The guide's view of the app (spec 20 §6): every store is touched off the main thread. */
class AppGuideEnvironment(private val graph: AppGraph, override val locale: Locale) : GuideEnvironment {
    private val io get() = graph.dispatchers.io
    private val prefs get() = graph.data.preferences

    override val reads: LiveReads = graph.liveReads
    override val clock: Clock get() = graph.clock
    override val timeStyle: TimeStyle = TimeStyles.of(graph.app, locale)
    override val format: CoroutineDispatcher get() = graph.dispatchers.ui

    override val lastGuideSource: Flow<String?> = flow { emitAll(prefs.lastGuideSource) }.flowOn(io)
    override suspend fun liveRestricted(): Boolean = graph.data.profiles.restriction(graph.data.profiles.activeId).restricts(OrgRoom.LIVE)

    override val lastChannel: Flow<String?> = flow { emitAll(prefs.lastChannel(graph.data.profiles.activeId)) }.flowOn(io)
    override val showChannelNumbers: Flow<Boolean> = flow { emitAll(prefs.showChannelNumbers) }.flowOn(io)
    override val timeZone: Flow<String?> = graph.appZone.flowOn(io)

    override val savedSources: Flow<List<SavedSource>> =
        flow { emitAll(graph.data.sources.observe()) }.map { list -> list.map { SavedSource(it.id, it.name, it.enabled) } }.flowOn(io)

    override val health: Flow<List<SourceHealth>> = flow { emitAll(graph.data.refreshStatus.observe()) }.flowOn(io)

    override suspend fun saveGuideSource(id: String) = withContext(io) { prefs.setLastGuideSource(id) }

    override fun syncAll() {
        graph.appScope.launch { graph.sync.scheduler.syncNow(null) }
    }

    override fun keptList(spec: ListSpec): ChannelList? = graph.keptRows.get(spec)

    override fun keepList(list: ChannelList) = graph.keptRows.keep(list)

    override val remindersOn: Boolean get() = graph.flags.reminders

    override val reminderIds: Flow<Set<String>> get() = graph.reminders.ids

    override suspend fun toggleReminder(reminder: Reminder): Boolean = graph.reminders.toggle(reminder)

    override fun cachedProgramme(programmeId: Long, title: String): HeroMetadata? =
        graph.metadata.cached(MetadataRequest(MediaType.PROGRAMME, title))?.let { hero(programmeId, it) }

    override suspend fun programmeMetadata(programmeId: Long, title: String): HeroMetadata? =
        graph.metadata.enrich(MetadataRequest(MediaType.PROGRAMME, title))?.let { hero(programmeId, it) }

    /** The backdrop, else the poster, at `w780` for the 16:9 still (spec 41 §9.5). */
    private fun hero(programmeId: Long, record: MetadataRecord) = HeroMetadata(
        programmeId = programmeId,
        year = record.year,
        rating = record.rating,
        overview = record.overview,
        stillUrl = Artwork.url(record.backdrop, Artwork.BACKDROP) ?: Artwork.url(record.poster, Artwork.BACKDROP),
        sourceName = record.provider.displayName,
        sourceUrl = record.attributionUrl ?: record.provider.home,
    )

    override fun openUrl(url: String) {
        runCatching { graph.app.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
