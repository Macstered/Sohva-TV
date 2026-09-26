package com.sohva.tv.app.backup

import android.os.SystemClock
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.channels.ChannelLogoStore
import com.sohva.tv.core.data.backup.BackupPayload
import com.sohva.tv.core.data.backup.BackupReader
import com.sohva.tv.core.data.backup.BackupWriter
import com.sohva.tv.core.data.backup.ProfileKept
import com.sohva.tv.core.model.backup.BackupCipher
import com.sohva.tv.core.model.backup.BackupException
import com.sohva.tv.core.model.backup.BackupProblem
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.profile.Profiles
import com.sohva.tv.core.model.source.SourceConfig
import com.sohva.tv.core.model.source.SourceRules
import com.sohva.tv.core.model.vod.PreferredCopy
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.sink

/**
 * Saving and restoring the encrypted backup (spec 71). Everything runs off the main thread; a
 * restore runs to its end in the app's scope once it has started writing (§9 NFR-04), under a
 * marker that tells the next start when it did not finish (§8).
 */
class BackupService(private val graph: AppGraph) {
    private val data get() = graph.data
    private val logos by lazy { ChannelLogoStore(graph.app) }
    private val rows get() = graph.data.backup

    /** Collects, writes and encrypts the backup into [out] (BACKUP-FR-07); the plaintext is wiped after. */
    suspend fun save(out: OutputStream, password: CharArray) = withContext(graph.dispatchers.io) {
        val started = SystemClock.elapsedRealtime()
        val payload = collect()
        val collected = SystemClock.elapsedRealtime()
        val buffer = BackupCipher.WipingBuffer()
        try {
            val sink = buffer.sink().buffer()
            BackupWriter.write(payload, ZoneId.systemDefault().id, graph.clock.wallMillis(), sink)
            sink.flush()
            val written = SystemClock.elapsedRealtime()
            BackupCipher.write(buffer.bytes, buffer.length, password, out)
            // Spec 71 §11: the phases on the low-end box; encryption is mostly the key derivation.
            graph.diagnostics.info(
                "backup",
                "saved ${buffer.length} bytes: read ${collected - started} ms, json ${written - collected} ms, encrypt ${SystemClock.elapsedRealtime() - written} ms",
            )
        } finally {
            buffer.wipe()
        }
    }

    private suspend fun collect(): BackupPayload {
        val sources = data.sources.all().mapNotNull { (data.sources.load(it.id) as? Outcome.Ok)?.value }
        val ids = sources.mapTo(HashSet()) { it.source.id }
        val prefs = data.preferences.backupPreferences()
        val household = data.profiles.household.value
        val profileData = LinkedHashMap<String, ProfileKept>()
        for (profile in household.shown) {
            profileData[profile.id] = rows.profileRows(profile.id).copy(
                favouriteEventIds = data.preferences.favouriteEvents(profile.id),
                lastChannelId = data.preferences.lastChannelOf(profile.id),
            )
        }
        val (rules, aliases) = rows.organisation()
        return BackupPayload(
            formatVersion = BackupWriter.FORMAT_VERSION,
            sources = sources,
            parentalPin = data.profiles.pinForBackup().takeIf { household.pinConfigured },
            preferences = prefs,
            profileData = profileData,
            channelPreferences = rows.channels(ids, logos::read),
            channelLists = rows.lists(),
            channelListMembers = rows.members(),
            rules = rules,
            aliases = aliases,
        )
    }

    /**
     * Reads, decrypts and checks the whole file before anything changes (BACKUP-FR-18 steps 1–3):
     * refused past 10 MiB; the sources must pass the rebuild's own rules too, so the restore
     * cannot stop half-way on one of them.
     */
    suspend fun open(input: InputStream, password: CharArray): BackupPayload = withContext(graph.dispatchers.io) {
        val started = SystemClock.elapsedRealtime()
        val plaintext = BackupCipher.read(Bounded(input, BackupCipher.MAX_FILE.toLong() + BackupCipher.HEADER), password)
        graph.diagnostics.info("backup", "opened ${plaintext.size} bytes: decrypt ${SystemClock.elapsedRealtime() - started} ms")
        val payload = try {
            BackupReader.read(Buffer().write(plaintext))
        } finally {
            plaintext.fill(0)
        }
        for (config in payload.sources) {
            if (SourceRules.validate(config) is SourceRules.Result.Invalid) throw BackupException(BackupProblem.STRUCTURE, "sources")
        }
        payload
    }

    /** The sources this TV has that the backup does not (or has as another type): a restore removes them (§8). */
    suspend fun removedBy(payload: BackupPayload): List<String> {
        val kept = payload.sources.associateBy { it.source.id }
        return data.sources.all().filter { kept[it.id]?.source?.type != it.type }.map { it.name }
    }

    /**
     * Applies a checked payload (BACKUP-FR-18 steps 4–11, BACKUP-FR-21): sources, then the
     * database rows in one transaction, logos, the PIN, the preferences; then what the rows mean is
     * applied to the surviving sources' channels and titles. Nothing syncs by itself (BACKUP-FR-19).
     */
    suspend fun restore(payload: BackupPayload) = withContext(graph.dispatchers.io) {
        val started = SystemClock.elapsedRealtime()
        rows.markRestoring(true)
        val before = data.profiles.household.value.shown.map { it.id }
        val kept = payload.sources.associateBy { it.source.id }
        for (source in data.sources.all()) {
            if (kept[source.id]?.source?.type != source.type) graph.sync.runner.remove(source.id)
        }
        for (config in payload.sources) saveSource(config)
        val restored = rows.replace(payload, graph.clock.wallMillis())
        for ((key, bytes) in restored.logos) rows.setLogo(key, logos.save(key, bytes))
        for ((key, url) in restored.logoAddresses) rows.setLogo(key, logos.kept(url))
        data.profiles.restorePin(payload.parentalPin)
        data.preferences.restoreBackup(payload.preferences, payload.profileData, pinConfigured = payload.parentalPin != null)
        // A profile the backup does not have leaves with its positions (spec 71 BACKUP-FR-21 rebuild rule).
        val now = payload.preferences.profiles.map { it.id }.toSet() + Profiles.DEFAULT_ID
        for (id in before.filter { it !in now }) rows.deleteProfile(id)
        graph.guideFocusChannel = null
        graph.keptRows.clear()
        graph.browseSessions.values.forEach { it.clear() }
        rows.markRestoring(false)
        // What the restored edits and rules mean for the channels and titles already here.
        data.channelEdits.reapply(restored.channelKeys)
        rows.applyRules(restored.ruleKeys, PreferredCopy.entries.firstOrNull { it.name == payload.preferences.preferredCopy } ?: PreferredCopy.NONE)
        graph.diagnostics.info("backup", "restored: ${SystemClock.elapsedRealtime() - started} ms")
    }

    private suspend fun saveSource(config: SourceConfig) {
        when (val saved = data.sources.save(config)) {
            is Outcome.Ok -> Unit
            is Outcome.Failed -> throw BackupException(BackupProblem.STRUCTURE, "sources")
        }
    }

    /** Whether the last restore stopped half-way (spec 71 §8). */
    suspend fun unfinished(): Boolean = withContext(graph.dispatchers.io) { rows.unfinished() }

    /** Refuses a file longer than [limit] bytes as soon as it passes it (BACKUP-FR-18 step 1). */
    private class Bounded(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var read = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }

        private fun count(n: Long) {
            read += n
            if (read > limit) throw BackupException(BackupProblem.TOO_LARGE)
        }
    }
}
