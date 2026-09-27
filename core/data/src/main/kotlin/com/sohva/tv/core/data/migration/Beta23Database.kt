package com.sohva.tv.core.data.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sohva.tv.core.data.backup.BackupAlias
import com.sohva.tv.core.data.backup.BackupList
import com.sohva.tv.core.data.backup.BackupMember
import com.sohva.tv.core.data.backup.BackupRule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first

/** A beta 23 `channel_preferences` row (plan/04 §17). */
data class Beta23Channel(
    val channelId: String,
    val sourceId: String,
    val customName: String?,
    val customGroupTitle: String?,
    val hidden: Boolean,
    val sortOrder: Int?,
    val manualXmltvChannelId: String?,
    val updatedAt: Long,
    val customLogoUrl: String?,
    val channelNumber: Int?,
)

/** A beta 23 `playback_progress` row. */
data class Beta23Progress(
    val profileId: String,
    val contentKey: String,
    val sourceId: String,
    val contentType: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

data class Beta23Reminder(
    val id: String,
    val kind: String,
    val eventId: String?,
    val channelId: String?,
    val title: String,
    val subtitle: String?,
    val startAt: Long,
    val createdAt: Long,
)

data class Beta23Decision(val eventId: String, val channelId: String, val decision: String, val updatedAt: Long)

data class Beta23TeamAlias(val sport: String, val canonical: String, val alias: String)

/** A beta 23 metadata fix: `catalogue_metadata_overrides` with its genres. */
data class Beta23Override(
    val contentKey: String,
    val externalId: String?,
    val replacementTitle: String?,
    val replacementPoster: String?,
    val replaceProviderPoster: Boolean,
    val genres: List<String>,
    val genresVersion: Int,
    val updatedAt: Long,
)

/**
 * Beta 23's main database (`streammate.db`, schemas 23–29), read only and without Room (decision
 * A1 option B, plan/04 §17). Tables and columns are found by `PRAGMA table_info`, not by version:
 * a table or column an older schema lacks reads as empty or null. Nothing is written to it.
 */
class Beta23Database private constructor(private val db: SQLiteDatabase) : AutoCloseable {
    private val columns = HashMap<String, Set<String>>()

    private fun columns(table: String): Set<String> = columns.getOrPut(table) {
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) } }
    }

    private fun <T> rows(table: String, read: (Row) -> T?): List<T> {
        val have = columns(table)
        if (have.isEmpty()) return emptyList()
        return db.rawQuery("SELECT * FROM `$table`", null).use { c ->
            val row = Row(c, have)
            buildList { while (c.moveToNext()) read(row)?.let(::add) }
        }
    }

    /** One cursor row; a column the schema lacks reads as null. */
    class Row internal constructor(private val c: Cursor, private val have: Set<String>) {
        private fun at(name: String): Int? = if (name in have) c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) } else null

        fun text(name: String): String? = at(name)?.let(c::getString)

        fun long(name: String): Long? = at(name)?.let(c::getLong)

        fun int(name: String): Int? = at(name)?.let(c::getInt)

        fun bool(name: String): Boolean = (long(name) ?: 0L) != 0L
    }

    fun channels(): List<Beta23Channel> = rows("channel_preferences") { r ->
        Beta23Channel(
            channelId = r.text("channelId") ?: return@rows null, sourceId = r.text("sourceId") ?: return@rows null,
            customName = r.text("customName"), customGroupTitle = r.text("customGroupTitle"), hidden = r.bool("hidden"),
            sortOrder = r.int("sortOrder"), manualXmltvChannelId = r.text("manualXmltvChannelId"), updatedAt = r.long("updatedAtEpochMillis") ?: 0,
            customLogoUrl = r.text("customLogoUrl"), channelNumber = r.int("channelNumber"),
        )
    }

    fun lists(): List<BackupList> = rows("channel_lists") { r ->
        BackupList(r.text("listId") ?: return@rows null, r.text("name") ?: return@rows null, r.int("sortOrder") ?: 0, r.long("updatedAtEpochMillis") ?: 0)
    }

    fun members(): List<BackupMember> = rows("channel_list_members") { r ->
        BackupMember(r.text("listId") ?: return@rows null, r.text("channelId") ?: return@rows null, r.int("sortOrder") ?: 0)
    }

    fun rules(): List<BackupRule> = rows("organization_rules") { r ->
        BackupRule(
            room = r.text("room") ?: return@rows null, sourceId = r.text("sourceId") ?: return@rows null,
            groupKey = r.text("groupKey") ?: return@rows null, itemKey = r.text("itemKey") ?: return@rows null,
            enabled = r.long("enabled")?.let { it != 0L }, sortMode = r.text("sortMode"), position = r.long("position"),
        )
    }

    /**
     * The alias families of the films [identities] name, as beta 23's backup writes them (plan/04
     * §12.2: the whole table would be far larger than needed).
     */
    fun aliases(identities: Set<String>): List<BackupAlias> {
        if (identities.isEmpty()) return emptyList()
        return rows("organization_aliases") { r ->
            val identity = r.text("identity")?.takeIf { it in identities } ?: return@rows null
            BackupAlias(r.text("alias") ?: return@rows null, identity)
        }
    }

    fun progress(): List<Beta23Progress> = rows("playback_progress") { r ->
        Beta23Progress(
            profileId = r.text("profileId") ?: "default", contentKey = r.text("contentKey") ?: return@rows null,
            sourceId = r.text("sourceId") ?: return@rows null, contentType = r.text("contentType") ?: return@rows null,
            positionMs = r.long("positionMillis") ?: 0, durationMs = r.long("durationMillis") ?: 0, completed = r.bool("completed"),
            updatedAt = r.long("lastWatchedEpochMillis") ?: 0,
        )
    }

    fun reminders(): List<Beta23Reminder> = rows("reminders") { r ->
        Beta23Reminder(
            id = r.text("id") ?: return@rows null, kind = r.text("kind") ?: return@rows null, eventId = r.text("eventId"),
            channelId = r.text("channelId"), title = r.text("title") ?: "", subtitle = r.text("subtitle"),
            startAt = r.long("startEpochMillis") ?: return@rows null, createdAt = r.long("createdAtEpochMillis") ?: 0,
        )
    }

    fun decisions(): List<Beta23Decision> = rows("event_channel_decisions") { r ->
        Beta23Decision(r.text("eventId") ?: return@rows null, r.text("channelId") ?: return@rows null, r.text("decision") ?: return@rows null, r.long("updatedAtEpochMillis") ?: 0)
    }

    fun teamAliases(): List<Beta23TeamAlias> = rows("team_aliases") { r ->
        Beta23TeamAlias(r.text("sport") ?: return@rows null, r.text("normalizedCanonicalName") ?: return@rows null, r.text("normalizedAlias") ?: return@rows null)
    }

    fun overrides(): List<Beta23Override> {
        val genres = HashMap<String, MutableList<String>>()
        rows("catalogue_genres") { r -> r.text("contentKey")?.let { k -> r.text("genre")?.let { g -> genres.getOrPut(k) { ArrayList() } += g } } }
        return rows("catalogue_metadata_overrides") { r ->
            val key = r.text("contentKey") ?: return@rows null
            Beta23Override(
                contentKey = key, externalId = r.text("externalId"), replacementTitle = r.text("replacementTitle"),
                replacementPoster = r.text("replacementPosterUrl"), replaceProviderPoster = r.bool("replaceProviderPoster"),
                genres = genres[key].orEmpty(), genresVersion = r.int("genresVersion") ?: 0, updatedAt = r.long("updatedAtEpochMillis") ?: 0,
            )
        }
    }

    /** The providers beta 23's metadata cache saw an id under, by media type (a series' id may be TVmaze's). */
    fun providersOf(mediaType: String): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        rows("metadata_cache") { r ->
            if (r.text("mediaType") != mediaType || r.text("status") != "MATCHED" && r.text("status") != "matched") return@rows null
            r.text("externalId")?.let { id -> r.text("provider")?.let { p -> out.getOrPut(id) { HashSet() } += p.lowercase() } }
        }
        return out
    }

    override fun close() = db.close()

    companion object {
        const val FILE: String = "streammate.db"

        fun exists(context: Context): Boolean = context.getDatabasePath(FILE).exists()

        /** Opens the old database read only; null when there is none or it cannot be opened. */
        fun open(context: Context): Beta23Database? {
            val path = context.getDatabasePath(FILE).takeIf(File::exists) ?: return null
            return runCatching { Beta23Database(SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY)) }.getOrNull()
        }

        /** Beta 23's DataStore preferences by key name, read once and released; null when there is no file or it cannot be read. */
        suspend fun preferences(context: Context, io: kotlinx.coroutines.CoroutineDispatcher): Map<String, Any>? {
            val file = context.preferencesDataStoreFile(Beta23HiddenCategories.OLD_FILE)
            if (!file.exists()) return null
            val scope = CoroutineScope(io + SupervisorJob())
            return try {
                runCatching { PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first() }.getOrNull()
                    ?.asMap()?.mapKeys { it.key.name }
            } finally {
                // Released before returning, so a later DataStore on the same file is not refused.
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
    }
}
