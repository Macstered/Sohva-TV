package com.sohva.tv.feature.sport.pairing

import com.sohva.tv.core.model.sport.SportEvent
import com.sohva.tv.core.model.sport.pairing.Confidence
import com.sohva.tv.core.model.sport.pairing.MatchSource
import com.sohva.tv.core.model.sport.pairing.StreamMatch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/**
 * The pairing result cache (spec 60 SPORT-FR-119): derived data in the cache directory, so a warm
 * start costs one small file read and no channel scan. Only automatic results are stored; the
 * viewer's decisions live in the database and are applied on read, so a lost or corrupt file is a
 * cache miss, never a lost decision. Bounded: 512 games, 40,000 results, 16 MiB, 24 hours; the
 * oldest-inserted games go first. Not thread-safe: the pairing controller calls it from one place.
 */
class PairingCache(private val file: File) {
    private class Entry(val eventId: String, val fingerprint: String, val savedAt: Long, val results: List<StreamMatch>)

    /** Results of [events] saved for [generation] with a matching game fingerprint, 0–24 h old. */
    fun read(generation: String, events: List<SportEvent>, now: Long): Map<String, List<StreamMatch>> {
        val wanted = events.associateBy({ it.id }, ::fingerprint)
        return load(generation).filter { e -> wanted[e.eventId] == e.fingerprint && now - e.savedAt in 0..MAX_AGE_MS }
            .associate { it.eventId to it.results }
    }

    /** After a completed scan: other generations and expired games dropped, [results] added, then bounded. */
    fun write(generation: String, events: List<SportEvent>, results: Map<String, List<StreamMatch>>, now: Long) {
        val byId = events.associateBy { it.id }
        val fresh = results.mapNotNull { (id, list) -> byId[id]?.let { Entry(id, fingerprint(it), now, list.map { m -> m.withDecision(null) }) } }
        val renewed = fresh.map { it.eventId }.toSet()
        val kept = load(generation).filter { now - it.savedAt in 0..MAX_AGE_MS && it.eventId !in renewed }
        val all = ArrayDeque(kept + fresh)
        while (all.size > MAX_EVENTS || all.sumOf { it.results.size } > MAX_RESULTS) all.removeFirst()
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(Capped(temp.outputStream()))).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(generation)
                out.writeInt(all.size)
                all.forEach { writeEntry(out, it) }
            }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: IOException) {
            // Too big or the disk refused it: the next start scans again.
            temp.delete()
        }
    }

    fun clear() {
        file.delete()
    }

    private fun load(generation: String): List<Entry> {
        if (!file.isFile || file.length() > MAX_BYTES) return emptyList()
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readUTF() != generation) return emptyList()
                val count = input.readInt()
                if (count !in 0..MAX_EVENTS) return emptyList()
                List(count) { readEntry(input) }
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        } catch (e: IndexOutOfBoundsException) {
            emptyList()
        }
    }

    private fun writeEntry(out: DataOutputStream, e: Entry) {
        out.writeUTF(e.eventId)
        out.writeUTF(e.fingerprint)
        out.writeLong(e.savedAt)
        out.writeInt(e.results.size)
        for (m in e.results) {
            out.writeUTF(m.channelKey)
            out.writeUTF(m.channelName.take(MAX_TEXT))
            out.writeUTF(m.programmeId)
            out.writeUTF(m.programmeTitle.take(MAX_TEXT))
            out.writeByte(m.source.ordinal)
            out.writeLong(m.programmeStartMillis)
            out.writeLong(m.offsetMinutes)
            out.writeBoolean(m.explicitStart)
            out.writeInt(m.score)
            out.writeByte(m.automatic.ordinal)
        }
    }

    private fun readEntry(input: DataInputStream): Entry {
        val eventId = input.readUTF()
        val fingerprint = input.readUTF()
        val savedAt = input.readLong()
        val n = input.readInt()
        if (n !in 0..MAX_RESULTS) throw IOException("bad count")
        val results = List(n) {
            StreamMatch(
                eventId = eventId, channelKey = input.readUTF(), channelName = input.readUTF(), programmeId = input.readUTF(),
                programmeTitle = input.readUTF(), source = MatchSource.entries[input.readByte().toInt()],
                programmeStartMillis = input.readLong(), offsetMinutes = input.readLong(), explicitStart = input.readBoolean(),
                score = input.readInt(), automatic = Confidence.entries[input.readByte().toInt()],
            )
        }
        return Entry(eventId, fingerprint, savedAt, results)
    }

    /** Refuses to write past [MAX_BYTES], so an oversized file is never left behind. */
    private class Capped(out: OutputStream) : FilterOutputStream(out) {
        private var written = 0L

        override fun write(b: Int) {
            if (++written > MAX_BYTES) throw IOException("pairing cache over its limit")
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            written += len
            if (written > MAX_BYTES) throw IOException("pairing cache over its limit")
            out.write(b, off, len)
        }
    }

    companion object {
        const val FILE_NAME: String = "sports-channel-matches.bin"
        private const val MAGIC = 0x53535043 // "SSPC"
        private const val VERSION = 1
        private const val MAX_EVENTS = 512
        private const val MAX_RESULTS = 40_000
        private const val MAX_BYTES = 16L * 1024 * 1024
        private const val MAX_AGE_MS = 24 * 60 * 60_000L
        private const val MAX_TEXT = 300

        /** A game's identity for the cache: a new kick-off, teams or zone (the minute of day) makes it a miss. */
        fun fingerprint(e: SportEvent): String {
            val digest = MessageDigest.getInstance("SHA-256")
            listOf(e.id, e.sport.name, e.home.name, e.away.name, e.startMillis.toString(), e.startMinuteOfDay.toString()).forEach {
                digest.update(it.toByteArray())
                digest.update(0)
            }
            val hex = "0123456789abcdef"
            return buildString { digest.digest().forEach { b -> append(hex[(b.toInt() shr 4) and 15]).append(hex[b.toInt() and 15]) } }
        }
    }
}
