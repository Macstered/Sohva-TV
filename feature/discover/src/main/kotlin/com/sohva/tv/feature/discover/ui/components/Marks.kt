package com.sohva.tv.feature.discover.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.sohva.tv.core.model.vod.TitleMark
import com.sohva.tv.core.model.vod.TitleMarks

/** Trakt's marks for the cards on screen (ADDON-FR-71); none without Trakt. */
val LocalTitleMarks = staticCompositionLocalOf { TitleMarks.NONE }

/** A movie poster's key (FR-71); series posters show nothing (Trakt marks episodes). */
fun movieMarkKey(type: String, id: String): String? = if (type == "movie") "movie:$id" else null

fun episodeMarkKey(seriesId: String, season: Int?, episode: Int?): String? =
    if (season != null && episode != null) "series:$seriesId:$season:$episode" else null

/**
 * The marks of one row, grid or episode list: one lookup for all of [keys], again when the
 * keys or Trakt's history change. Data arriving never moves focus; cards only redraw.
 */
@Composable
fun rememberMarks(keys: List<String>): Map<String, TitleMark> {
    val marks = LocalTitleMarks.current
    if (marks === TitleMarks.NONE) return emptyMap()
    val revision by marks.revision.collectAsState()
    var found by remember { mutableStateOf(emptyMap<String, TitleMark>()) }
    LaunchedEffect(keys, revision) {
        found = try {
            marks.marks(keys)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            found
        }
    }
    return found
}

/** A poster's content with its Trakt bar or tick. */
fun PosterContent.marked(mark: TitleMark?): PosterContent =
    if (mark == null) this else copy(progress = mark.fraction ?: progress, watched = mark.watched && mark.fraction == null)
