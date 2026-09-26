package com.sohva.tv.feature.player

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.player.SubtitleFormat

/**
 * One addon subtitle to choose (spec 50 ADDON-FR-97): [key] is a session-only hash (never the
 * subtitle's id or URL); [provider] null for the stream's own inline subtitles; [number] is "Option N".
 */
@Immutable
data class SubtitleCandidate(val key: String, val provider: String?, val language: String?, val number: Int)

/**
 * Subtitle results so far (FR-100): candidates in provider priority order, how many providers are
 * still answering, and each failed provider's line ("<provider>: <message>").
 */
@Immutable
data class SubtitleResults(val candidates: List<SubtitleCandidate> = emptyList(), val pending: Int = 0, val errors: List<String> = emptyList()) {
    val done: Boolean get() = pending == 0
}

/** A downloaded subtitle, decoded and recognised (FR-101), or the sentence that says why not. */
sealed interface SubtitleDownload {
    data class Ready(val text: String, val format: SubtitleFormat) : SubtitleDownload

    data class Failed(val message: String) : SubtitleDownload
}

/** What the viewer (or the automatic choice) picked. */
sealed interface SubtitlePick {
    data object Off : SubtitlePick

    /** An embedded text track by its language (Media3 picks the track), or by position when chosen by hand. */
    data class Embedded(val language: String?, val group: Int?, val index: Int?) : SubtitlePick

    /** A downloaded subtitle, side-loaded. */
    data class Addon(val candidate: SubtitleCandidate, val language: String?) : SubtitlePick
}
