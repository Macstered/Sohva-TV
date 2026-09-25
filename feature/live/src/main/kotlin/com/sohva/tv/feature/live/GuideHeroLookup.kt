package com.sohva.tv.feature.live

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What metadata adds to the hero (spec 20 GUIDE-FR-62…65), for the programme [programmeId]. */
@Immutable
data class HeroMetadata(
    val programmeId: Long,
    val year: Int?,
    /** "8.2": shown as "TMDB 8.2" (`guide_rating`). */
    val rating: String?,
    val overview: String?,
    /** Backdrop, else poster, sized for the 16:9 still. */
    val stillUrl: String?,
    val sourceName: String,
    val sourceUrl: String,
)

/**
 * The hero's programme lookup (GUIDE-FR-64): cleared at once when the selected programme changes,
 * answered from the memory cache at once, else looked up 350 ms after the selection rests; any
 * further change cancels the wait and the request.
 */
internal class GuideHeroLookup(
    private val env: GuideEnvironment,
    scope: CoroutineScope,
    selection: StateFlow<GuideSelection?>,
) {
    private val _metadata = MutableStateFlow<HeroMetadata?>(null)
    val metadata: StateFlow<HeroMetadata?> = _metadata.asStateFlow()

    init {
        scope.launch {
            selection.map { it?.programme }.distinctUntilChanged { a, b -> a?.id == b?.id }.collectLatest { programme ->
                _metadata.value = null
                if (programme == null || programme.title.isBlank()) return@collectLatest
                env.cachedProgramme(programme.id, programme.title)?.let {
                    _metadata.value = it
                    return@collectLatest
                }
                delay(REST_MS)
                _metadata.value = env.programmeMetadata(programme.id, programme.title)
            }
        }
    }

    private companion object {
        const val REST_MS = 350L
    }
}
