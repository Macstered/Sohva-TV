package com.sohva.tv.app.migration

import android.content.Context
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.backup.BackupService
import com.sohva.tv.core.data.backup.BackupChannel
import com.sohva.tv.core.data.backup.BackupPayload
import com.sohva.tv.core.data.backup.BackupPreferences
import com.sohva.tv.core.data.backup.BackupProfile
import com.sohva.tv.core.data.backup.ProfileKept
import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.migration.Beta23Database
import com.sohva.tv.core.data.migration.Beta23Rows
import com.sohva.tv.core.data.migration.Beta23SourceImport
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.profile.Profiles
import java.io.File
import java.net.URI
import kotlinx.coroutines.withContext

/**
 * The rest of beta 23's data on an upgraded TV (decision A1 option B, plan/04 §17), once: its
 * settings, profiles and each profile's favourites, recents, last channel, locks and allowed
 * groups, channel edits with phone logos, channel lists and organisation rules go through the
 * backup restore (the same mapping a beta 23 `.smbak` gets, sources excepted: M1's importer brings
 * those); positions, reminders, sport decisions, team aliases and metadata fixes, which backups
 * never held, are copied row by row. Old stores are read only; a part that cannot be read is
 * reported and skipped, and nothing is written over it.
 */
class Beta23Upgrade(private val graph: AppGraph) {
    private val app: Context get() = graph.app

    sealed interface Result {
        data object Nothing : Result

        data class Done(val summary: String) : Result

        data object Failed : Result
    }

    /** Whether beta 23's viewer data may still need importing: file checks only, no database (plan/07 start-up rule). */
    fun pending(): Boolean = !doneFile(app).exists() && (Beta23Database.exists(app) || oldPreferences(app).exists())

    suspend fun run(): Result = withContext(graph.dispatchers.io) {
        val meta = graph.data.appMeta
        if (meta.value(MARKER) != null) return@withContext Result.Nothing.also { markDone() }
        if (!pending()) {
            meta.put(AppMetaEntity(MARKER, "nothing"))
            markDone()
            return@withContext Result.Nothing
        }
        val result = try {
            val prefs = Beta23Database.preferences(app, graph.dispatchers.io)
            val old = Beta23Database.open(app)
            try {
                val pin = pin()
                val payload = payload(prefs, old, pin)
                BackupService(graph).restore(payload, withSources = false)
                val counts = old?.let { Beta23Rows.copy(it, graph.data.database) }
                val summary = "profiles ${payload.preferences.profiles.size + 1}, channels ${payload.channelPreferences.size}, " +
                    "lists ${payload.channelLists.size}, rules ${payload.rules.size}, positions ${counts?.progress ?: 0}, " +
                    "reminders ${counts?.reminders ?: 0}, decisions ${counts?.decisions ?: 0}, matches ${counts?.matches ?: 0}"
                graph.diagnostics.info("upgrade", "beta 23 import: $summary")
                Result.Done(summary)
            } finally {
                old?.close()
            }
        } catch (e: Exception) {
            // Counts and kinds only: nothing a viewer entered reaches the log.
            graph.diagnostics.info("upgrade", "beta 23 import failed: ${e.javaClass.simpleName}")
            Result.Failed
        }
        meta.put(AppMetaEntity(MARKER, if (result is Result.Done) "done" else "failed"))
        markDone()
        result
    }

    private fun markDone() {
        runCatching { doneFile(app).apply { parentFile?.mkdirs() }.createNewFile() }
    }

    /** The PIN from beta 23's secure store, read with its own key opened read only; null when absent or unreadable. */
    private fun pin(): String? = runCatching {
        val file = File(app.applicationInfo.dataDir, "shared_prefs/${Beta23SourceImport.FILE}.xml")
        if (!file.exists()) return null
        val stored = app.getSharedPreferences(Beta23SourceImport.FILE, Context.MODE_PRIVATE).getString(PIN_KEY, null) ?: return null
        Beta23SourceImport.readOnlyCipher(app).decrypt(stored).takeIf { PIN.matches(it) }
    }.getOrNull()

    private fun payload(prefs: Map<String, Any>?, old: Beta23Database?, pin: String?): BackupPayload {
        val p = prefs.orEmpty()
        fun text(key: String) = p[key] as? String
        fun flag(key: String) = p[key] as? Boolean
        fun set(key: String): List<String> = (p[key] as? Set<*>)?.filterIsInstance<String>().orEmpty()
        val profiles = Profiles.decode(text("profiles")).map { BackupProfile(it.id, it.name.orEmpty(), it.colorIndex) }
        val preferences = BackupPreferences(
            timeZoneId = text("time_zone"), profiles = profiles, activeProfileId = text("active_profile_id") ?: Profiles.DEFAULT_ID,
            askProfileAtStart = flag("ask_profile_at_start") ?: true, lastGuideSourceId = text("last_guide_source_id"),
            startupScreen = text("startup_screen") ?: "HOME", remoteChannelKeyMode = text("remote_channel_key_mode") ?: "DPAD_AND_CHANNEL_KEYS",
            remoteMappings = (p["remote_mappings"] as? Set<*>)?.filterIsInstance<String>(), metadataLanguage = text("metadata_language"),
            interfaceScale = text("interface_scale"), colorTheme = text("color_theme"),
            followedSports = (p["followed_sports"] as? Set<*>)?.filterIsInstance<String>(),
            followedCompetitionKeys = (p["followed_competitions"] as? Set<*>)?.filterIsInstance<String>(),
            sportsChannelPriority = text("sports_channel_priority")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
            refreshInterval = text("playlist_epg_refresh_interval"), bufferProfile = text("playback_buffer_profile"),
            seekStep = text("playback_seek_step"), subtitleSize = text("subtitle_text_size"), subtitleColor = text("subtitle_text_color"),
            subtitleBackground = text("subtitle_background"), reconnectPolicy = text("playback_reconnect_policy"),
            autoPlayNext = flag("auto_play_next_episode") ?: true, pictureInPicture = flag("picture_in_picture") ?: false,
            // Backups leave it out (plan/04 §12.2); an upgrade keeps it.
            autoFrameRate = flag("auto_frame_rate"), editorsShowHidden = flag("editors_show_hidden") ?: true,
            showChannelNumbers = flag("show_channel_numbers") ?: true, preferredCopy = text("preferred_catalogue_copy"),
            hiddenLive = set("hidden_live_categories"), hiddenMovies = set("hidden_movie_categories"), hiddenSeries = set("hidden_series_categories"),
            audioPrimary = text("preferred_audio_language"), audioSecondary = text("secondary_audio_language"),
            subtitlesPrimary = text("preferred_subtitle_language"), subtitlesSecondary = text("secondary_subtitle_language"),
            customGroupsJson = text("custom_catalogue_groups"),
        )
        val ids = listOf(Profiles.DEFAULT_ID) + profiles.map { it.id }
        val profileData = ids.associateWith { id ->
            fun key(base: String) = Profiles.key(base, id)
            ProfileKept(
                favouriteEventIds = set(key("favourite_event_ids")),
                favouriteChannelIds = set(key("favourite_channel_ids")).sorted(),
                recentChannelIds = text(key("recent_channel_ids"))?.split('\u001F')?.filter { it.isNotBlank() }.orEmpty(),
                lastChannelId = text(key("last_channel_id")),
                lockedChannelIds = set(key("locked_channel_ids")).sorted(),
                allowedLive = set(key("allowed_groups_live")),
                allowedMovies = set(key("allowed_groups_movies")),
                allowedSeries = set(key("allowed_groups_series")),
            )
        }
        val rules = old?.rules().orEmpty()
        val films = rules.filter { it.room == OrgRoom.MOVIES.wire && it.itemKey.isNotEmpty() && !it.itemKey.startsWith("@") }.mapTo(HashSet()) { it.itemKey }
        val channels = old?.channels().orEmpty().map { c ->
            BackupChannel(
                c.channelId, c.sourceId, c.customName, c.customGroupTitle, c.hidden, c.sortOrder, c.manualXmltvChannelId, c.updatedAt,
                c.customLogoUrl.takeUnless { logoFile(it) != null }, c.channelNumber, logoFile(c.customLogoUrl)?.let { runCatching { it.readBytes() }.getOrNull() },
            )
        }
        return BackupPayload(
            formatVersion = 2, sources = emptyList(), parentalPin = pin, preferences = preferences, profileData = profileData,
            channelPreferences = channels, channelLists = old?.lists().orEmpty(), channelListMembers = old?.members().orEmpty(),
            rules = rules, aliases = old?.aliases(films).orEmpty(),
        )
    }

    /** A phone-sent logo: a `file:` address inside beta 23's own `channel-logos` folder, and nothing else. */
    private fun logoFile(address: String?): File? {
        if (address == null || !address.startsWith("file:")) return null
        val dir = File(app.filesDir, LOGO_DIR).canonicalFile
        val file = runCatching { File(URI(address)).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.parentFile == dir && it.isFile && it.length() <= MAX_LOGO_BYTES }
    }

    companion object {
        const val MARKER: String = "import.beta23.viewer"
        private const val PIN_KEY = "parental_pin_v1"
        private val PIN = Regex("\\d{4,8}")
        private const val LOGO_DIR = "channel-logos"
        private const val MAX_LOGO_BYTES = 2L * 1024 * 1024

        /** Written once the import has run (done or failed), so later starts need no database read. */
        fun doneFile(context: Context): File = File(context.noBackupFilesDir, "beta23-import.done")

        fun oldPreferences(context: Context): File = File(context.filesDir, "datastore/${com.sohva.tv.core.data.migration.Beta23HiddenCategories.OLD_FILE}.preferences_pb")
    }
}
