package com.streammate.tv.matching

import com.streammate.tv.core.model.TodayEvent
import java.io.*
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Derived data only. A corrupt/old file is a cache miss, never a lost user decision. */
class EventChannelMatchCache(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private var loaded = false
    private var generation = ""
    private val entries = linkedMapOf<String, Entry>()
    private data class Entry(val key: String, val saved: Long, val matches: List<EventChannelMatch>)

    suspend fun read(events: List<TodayEvent>, inputGeneration: String): Map<String, List<EventChannelMatch>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            if (generation != inputGeneration) return@withLock emptyMap()
            events.mapNotNull { event ->
                entries[event.id]?.takeIf { it.key == eventKey(event) && clock() - it.saved in 0..MAX_AGE }
                    ?.let { event.id to it.matches }
            }.toMap()
        }
    }

    suspend fun write(events: List<TodayEvent>, inputGeneration: String, matches: Map<String, List<EventChannelMatch>>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            if (generation != inputGeneration) entries.clear()
            generation = inputGeneration
            entries.entries.removeAll { clock() - it.value.saved !in 0..MAX_AGE }
            events.forEach { event -> matches[event.id]?.let { entries[event.id] = Entry(eventKey(event), clock(), it) } }
            while (entries.size > 512 || entries.values.sumOf { it.matches.size } > 40_000) entries.remove(entries.keys.first())
            val target = file ?: return@withLock
            val pending = File(target.parentFile, "${target.name}.tmp")
            try {
                target.parentFile?.mkdirs()
                DataOutputStream(BufferedOutputStream(FileOutputStream(pending))).use { out ->
                    out.writeInt(VERSION); out.text(generation); out.writeInt(entries.size)
                    entries.forEach { (id, entry) ->
                        out.text(id); out.text(entry.key); out.writeLong(entry.saved); out.writeInt(entry.matches.size)
                        entry.matches.forEach { m ->
                            out.text(m.eventId); out.text(m.channelId); out.text(m.channelName); out.text(m.programmeId); out.text(m.programmeTitle)
                            out.writeLong(m.programmeStartEpochMillis); out.writeLong(m.startOffsetMinutes)
                            out.text(m.automaticConfidence.name); out.writeInt(m.score); out.text(m.source.name); out.writeBoolean(m.hasExplicitStartTime)
                        }
                    }
                }
                if (pending.length() > MAX_BYTES) { pending.delete(); return@withLock }
                if (!pending.renameTo(target)) { target.delete(); pending.renameTo(target) }
            } catch (_: IOException) { pending.delete() }
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val target = file?.takeIf { it.isFile && it.length() <= MAX_BYTES } ?: return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(target))).use { input ->
                require(input.readInt() == VERSION)
                generation = input.text()
                val count = input.readInt().also { require(it in 0..512) }
                var matchCount = 0
                repeat(count) {
                    val id = input.text(); val key = input.text(); val saved = input.readLong()
                    val size = input.readInt().also { require(it in 0..40_000); matchCount += it; require(matchCount <= 40_000) }
                    val matches = List(size) {
                        val eventId = input.text(); val channelId = input.text(); val channelName = input.text()
                        val programmeId = input.text(); val programmeTitle = input.text()
                        val start = input.readLong(); val offset = input.readLong()
                        val confidence = ChannelMatchConfidence.valueOf(input.text()); val score = input.readInt()
                        val source = MatchCandidateSource.valueOf(input.text()); val explicit = input.readBoolean()
                        EventChannelMatch(eventId, channelId, channelName, programmeId, programmeTitle, start, offset, confidence, score, null, source, explicit)
                    }
                    entries[id] = Entry(key, saved, matches)
                }
                require(input.read() == -1)
            }
        } catch (_: Exception) { entries.clear(); generation = "" }
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > 64_000) throw IOException("Match cache field too long")
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.text(): String {
        val size = readInt().also { require(it in 0..64_000) }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }
    private fun eventKey(event: TodayEvent) = fingerprint(listOf(event.id, event.sport.name, event.home, event.away, event.startEpochMillis.toString(), event.startMinuteOfDay.toString()))
    companion object {
        // Recompute results after numeric date support and removal of unzoned clock comparisons.
        private const val VERSION = 3
        private const val MAX_AGE = 24 * 60 * 60_000L
        private const val MAX_BYTES = 16 * 1024 * 1024L
        fun fingerprint(values: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.forEach { value -> digest.update("${value.length}:$value".toByteArray(Charsets.UTF_8)) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
