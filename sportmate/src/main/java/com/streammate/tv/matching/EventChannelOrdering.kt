package com.streammate.tv.matching

import com.streammate.tv.core.model.ChannelStreamTags
import com.streammate.tv.core.model.StreamTagKind
import kotlin.math.abs

object EventChannelOrdering {
    fun sort(matches: List<EventChannelMatch>, priority: List<String>): List<EventChannelMatch> {
        val ranks = if (priority.isEmpty()) emptyMap() else matches.associate { match ->
                val code = ChannelStreamTags.read(match.channelName).firstOrNull { it.kind == StreamTagKind.LANGUAGE }?.label
                match.channelId to (priority.indexOf(code).takeIf { it >= 0 } ?: Int.MAX_VALUE)
        }
        return matches.sortedWith(compareBy<EventChannelMatch> { it.confidence.ordinal }
            .thenBy { ranks[it.channelId] ?: Int.MAX_VALUE }
            .thenByDescending { it.score }
            .thenBy { abs(it.startOffsetMinutes) }
            .thenBy { it.channelName },
        )
    }
}
