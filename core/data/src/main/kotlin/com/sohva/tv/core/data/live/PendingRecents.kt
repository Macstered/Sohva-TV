package com.sohva.tv.core.data.live

/**
 * Channels watched whose "recent" row is not written yet. The write needs the database's one
 * write connection and waits behind a running import, sometimes for minutes; Home's row reads
 * these first, so the channel just watched leads it at once (HOME-FR-34).
 *
 * Bounded: at most [LIMIT] entries, the oldest dropped; an entry goes when its write has finished.
 */
class PendingRecents {
    private class Entry(val profile: String, val key: String, val at: Long)

    private val waiting = ArrayDeque<Entry>()

    @Synchronized
    fun add(profile: String, key: String, at: Long) {
        waiting.removeAll { it.profile == profile && it.key == key }
        waiting.addLast(Entry(profile, key, at))
        while (waiting.size > LIMIT) waiting.removeFirst()
    }

    /** The write of ([profile], [key]) made at [at] is done; a newer watch of the same channel stays. */
    @Synchronized
    fun written(profile: String, key: String, at: Long) {
        waiting.removeAll { it.profile == profile && it.key == key && it.at == at }
    }

    /** [profile]'s waiting channel keys, newest first. */
    @Synchronized
    fun keys(profile: String): List<String> = waiting.filter { it.profile == profile }.sortedByDescending { it.at }.map { it.key }

    private companion object {
        /** As many as the recents list keeps (spec 30 PLAY-FR-57). */
        const val LIMIT = 20
    }
}
