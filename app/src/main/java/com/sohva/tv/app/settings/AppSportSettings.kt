package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.sport.Competition
import com.sohva.tv.core.model.sport.SportFeedStatus
import com.sohva.tv.core.model.sport.SportFollows
import com.sohva.tv.core.model.sport.SportType
import com.sohva.tv.feature.settings.SportSettingsServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Settings › Sohva Sport from the app graph (spec 60 §4.1); every store is touched off the main thread. */
class AppSportSettings(private val graph: AppGraph) : SportSettingsServices {
    private val io get() = graph.dispatchers.io
    private val prefs get() = graph.data.preferences

    override fun hasKey(): Flow<Boolean> = flow { emitAll(graph.data.serviceKeys.apiSportsSaved()) }.flowOn(io)

    override suspend fun saveKey(key: String): Outcome<Unit> = withContext(io) {
        if (key.isEmpty()) graph.data.serviceKeys.removeApiSports() else graph.data.serviceKeys.saveApiSports(key)
    }

    override fun follows(): Flow<SportFollows> = flow { emitAll(prefs.sportFollows) }.flowOn(io)

    override suspend fun setFollows(follows: SportFollows) = withContext(io) { prefs.setSportFollows(follows) }

    override suspend fun competitions(sport: SportType): List<Competition> = graph.sport.repository.competitions(sport)

    override fun priority(): Flow<List<String>> = flow { emitAll(prefs.sportsPriority) }.flowOn(io)

    override suspend fun setPriority(codes: List<String>) = withContext(io) { prefs.setSportsPriority(codes) }

    override fun status(): Flow<SportFeedStatus> = flow { emitAll(graph.sport.feed.state.map { it.status }) }.flowOn(io)
}
