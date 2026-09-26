package com.sohva.tv.core.model.sport.pairing

import com.sohva.tv.core.model.text.StreamTags
import kotlin.math.abs

/**
 * How streams are presented (spec 60 SPORT-FR-117): confidence first, then the rank of the channel
 * name's language tag in the viewer's priority codes (untagged or unlisted last), then score,
 * closeness and name. Priority only reorders within a confidence; it cannot know the commentary.
 */
object StreamOrder {
    fun present(matches: List<StreamMatch>, priority: List<String>): List<StreamMatch> {
        if (matches.size < 2) return matches
        val rank = priority.withIndex().associate { it.value to it.index }
        val languageRank = matches.associateWith { m -> StreamTags.language(m.channelName)?.let(rank::get) ?: Int.MAX_VALUE }
        return matches.sortedWith(
            compareBy<StreamMatch> { it.confidence.ordinal }.thenBy { languageRank.getValue(it) }
                .thenByDescending { it.score }.thenBy { abs(it.offsetMinutes) }.thenBy { it.channelName },
        )
    }

    /** Available (confirmed included) and Possible counts, the card's call to action (SPORT-FR-51). */
    fun counts(matches: List<StreamMatch>): Pair<Int, Int> =
        matches.count { it.confidence == Confidence.AVAILABLE } to matches.count { it.confidence == Confidence.POSSIBLE }
}
