package com.streammate.tv.trakt

import android.content.Context
import com.sohva.tv.trakt.*
import com.streammate.tv.core.security.SecretCipher
import kotlinx.serialization.json.*

/** What a card shows for a title from Trakt: a partial position, a watched mark, or neither. */
class TraktTitleState(val fraction: Float?, val watched: Boolean, val updatedAtMillis: Long)

/**
 * A title Home shows from Trakt: a recommendation, or the next episode of a
 * show in progress. Addressed by id; the text and pictures are Trakt's own.
 */
class TraktHomeTitle(
    val kind: String, val ids: TraktIds, val title: String, val year: Int?, val overview: String?,
    val poster: String?, val fanart: String?,
    val season: Int? = null, val number: Int? = null, val episodeTitle: String? = null,
    val updatedAtMillis: Long = 0L,
) {
    val key: String get() = "$kind:${ids.trakt ?: ids.tmdb ?: ids.imdb}:${season ?: ""}:${number ?: ""}"
    override fun toString() = "TraktHomeTitle($kind $ids)"
}

/** One connected Trakt account for a Sohva profile. */
class TraktAccount(val username: String, val needsReauthorization: Boolean)

internal class TraktPendingScrobble(val item: TraktItem, val action: TraktScrobbleAction, val progress: Double)

/**
 * Encrypted per-profile storage: tokens, the account name, and scrobbles that
 * could not be delivered yet. Nothing here is a watch history of its own.
 */
internal class TraktAccountStore(context: Context, private val cipher: SecretCipher) {
    private val preferences = context.getSharedPreferences("trakt_accounts", Context.MODE_PRIVATE)

    private class Record(val tokens: TraktTokens?, val username: String, val needsReauthorization: Boolean)

    @Synchronized fun profiles(): Set<String> = preferences.all.keys
        .filter { it.startsWith(ACCOUNT_PREFIX) }.map { it.removePrefix(ACCOUNT_PREFIX) }.toSet()

    @Synchronized fun account(profile: String): TraktAccount? = read(profile)?.let { TraktAccount(it.username, it.needsReauthorization) }

    @Synchronized fun tokens(profile: String): TraktTokens? = read(profile)?.tokens

    @Synchronized fun save(profile: String, tokens: TraktTokens, username: String) =
        write(profile, Record(tokens, username, needsReauthorization = false))

    /** Keeps the account visible so the user sees why nothing syncs and can sign in again. */
    @Synchronized fun markReauthorization(profile: String) {
        val current = read(profile) ?: return
        write(profile, Record(null, current.username, needsReauthorization = true))
    }

    @Synchronized fun remove(profile: String) {
        check(preferences.edit().remove(ACCOUNT_PREFIX + profile).remove(PENDING_PREFIX + profile).remove(ACTIVITY_PREFIX + profile)
            .remove(ACTIVE_PREFIX + profile).remove("$RECOMMENDATIONS:$profile").remove("$NEXT_UP:$profile").commit())
    }

    /** A cached list of Home titles and when it was fetched. */
    class Titles(val fetchedAtMillis: Long, val items: List<TraktHomeTitle>)

    @Synchronized fun titles(profile: String, name: String): Titles? = preferences.getString("$name:$profile", null)?.let { encrypted ->
        runCatching {
            val json = Json.parseToJsonElement(cipher.decrypt(encrypted)).jsonObject
            Titles(json.getValue("at").jsonPrimitive.long, json.getValue("items").jsonArray.mapNotNull(::decodeTitle))
        }.getOrNull()
    }

    @Synchronized fun saveTitles(profile: String, name: String, titles: Titles) {
        val json = buildJsonObject { put("at", titles.fetchedAtMillis); put("items", buildJsonArray { titles.items.forEach { add(encodeTitle(it)) } }) }
        check(preferences.edit().putString("$name:$profile", cipher.encrypt(json.toString())).commit())
    }

    private fun encodeTitle(t: TraktHomeTitle) = buildJsonObject {
        put("kind", t.kind); put("title", t.title); t.year?.let { put("year", it) }; t.overview?.let { put("overview", it) }
        t.poster?.let { put("poster", it) }; t.fanart?.let { put("fanart", it) }
        t.ids.trakt?.let { put("trakt", it) }; t.ids.tmdb?.let { put("tmdb", it) }; t.ids.imdb?.let { put("imdb", it) }; t.ids.tvdb?.let { put("tvdb", it) }
        t.season?.let { put("season", it) }; t.number?.let { put("number", it) }; t.episodeTitle?.let { put("episodeTitle", it) }
        put("updatedAt", t.updatedAtMillis)
    }

    private fun decodeTitle(element: JsonElement): TraktHomeTitle? = runCatching {
        val j = element.jsonObject
        fun text(name: String) = (j[name] as? JsonPrimitive)?.contentOrNull
        TraktHomeTitle(
            kind = j.getValue("kind").jsonPrimitive.content,
            ids = TraktIds(j["trakt"]?.jsonPrimitive?.longOrNull, j["tmdb"]?.jsonPrimitive?.longOrNull, text("imdb"), j["tvdb"]?.jsonPrimitive?.longOrNull),
            title = j.getValue("title").jsonPrimitive.content, year = j["year"]?.jsonPrimitive?.intOrNull, overview = text("overview"),
            poster = text("poster"), fanart = text("fanart"),
            season = j["season"]?.jsonPrimitive?.intOrNull, number = j["number"]?.jsonPrimitive?.intOrNull, episodeTitle = text("episodeTitle"),
            updatedAtMillis = j["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }.getOrNull()

    /** Trakt's newest activity time at the last full sync; the cheap "anything new?" check. */
    @Synchronized fun activityStamp(profile: String): Long = preferences.getLong(ACTIVITY_PREFIX + profile, 0L)
    @Synchronized fun saveActivityStamp(profile: String, value: Long) { check(preferences.edit().putLong(ACTIVITY_PREFIX + profile, value).commit()) }

    /** The start we last sent and have not yet closed, so a killed app can still send its pause. */
    @Synchronized fun active(profile: String): TraktPendingScrobble? = preferences.getString(ACTIVE_PREFIX + profile, null)
        ?.let { encrypted -> runCatching { decodePending(Json.parseToJsonElement(cipher.decrypt(encrypted))) }.getOrNull() }

    @Synchronized fun saveActive(profile: String, entry: TraktPendingScrobble?) {
        val editor = preferences.edit()
        if (entry == null) editor.remove(ACTIVE_PREFIX + profile) else editor.putString(ACTIVE_PREFIX + profile, cipher.encrypt(encodePending(entry).toString()))
        check(editor.commit())
    }

    @Synchronized fun pending(profile: String): List<TraktPendingScrobble> = preferences.getString(PENDING_PREFIX + profile, null)
        ?.let { encrypted -> runCatching { Json.parseToJsonElement(cipher.decrypt(encrypted)).jsonArray.mapNotNull(::decodePending) }.getOrNull() }
        .orEmpty()

    @Synchronized fun savePending(profile: String, entries: List<TraktPendingScrobble>) {
        val editor = preferences.edit()
        if (entries.isEmpty()) editor.remove(PENDING_PREFIX + profile)
        else editor.putString(PENDING_PREFIX + profile, cipher.encrypt(buildJsonArray { entries.takeLast(MAX_PENDING).forEach { add(encodePending(it)) } }.toString()))
        check(editor.commit())
    }

    private fun read(profile: String): Record? = preferences.getString(ACCOUNT_PREFIX + profile, null)?.let { encrypted ->
        runCatching {
            val json = Json.parseToJsonElement(cipher.decrypt(encrypted)).jsonObject
            val tokens = json["access"]?.jsonPrimitive?.contentOrNull?.let { access ->
                TraktTokens(access, json.getValue("refresh").jsonPrimitive.content, json.getValue("expires").jsonPrimitive.long)
            }
            Record(tokens, json.getValue("username").jsonPrimitive.content, json["reauthorize"]?.jsonPrimitive?.booleanOrNull == true)
        }.getOrNull()
    }

    private fun write(profile: String, record: Record) {
        val json = buildJsonObject {
            record.tokens?.let { put("access", it.accessToken); put("refresh", it.refreshToken); put("expires", it.expiresAtMillis) }
            put("username", record.username)
            put("reauthorize", record.needsReauthorization)
        }
        check(preferences.edit().putString(ACCOUNT_PREFIX + profile, cipher.encrypt(json.toString())).commit())
    }

    private fun encodePending(entry: TraktPendingScrobble) = buildJsonObject {
        val item = entry.item
        put("kind", if (item is TraktItem.Movie) "movie" else "episode")
        item.ids.tmdb?.let { put("tmdb", it) }; item.ids.imdb?.let { put("imdb", it) }
        item.ids.trakt?.let { put("trakt", it) }; item.ids.tvdb?.let { put("tvdb", it) }
        if (item is TraktItem.Episode) { put("season", item.season); put("number", item.number) }
        put("action", entry.action.name); put("progress", entry.progress)
    }

    private fun decodePending(element: JsonElement): TraktPendingScrobble? = runCatching {
        val json = element.jsonObject
        val ids = TraktIds(json["trakt"]?.jsonPrimitive?.longOrNull, json["tmdb"]?.jsonPrimitive?.longOrNull,
            json["imdb"]?.jsonPrimitive?.contentOrNull, json["tvdb"]?.jsonPrimitive?.longOrNull)
        val item = if (json.getValue("kind").jsonPrimitive.content == "movie") TraktItem.Movie(ids)
            else TraktItem.Episode(ids, json.getValue("season").jsonPrimitive.int, json.getValue("number").jsonPrimitive.int)
        TraktPendingScrobble(item, TraktScrobbleAction.valueOf(json.getValue("action").jsonPrimitive.content), json.getValue("progress").jsonPrimitive.double)
    }.getOrNull()

    companion object {
        private const val ACCOUNT_PREFIX = "account:"
        private const val PENDING_PREFIX = "pending:"
        private const val ACTIVITY_PREFIX = "activity:"
        private const val ACTIVE_PREFIX = "active:"
        const val RECOMMENDATIONS = "recommendations"
        const val NEXT_UP = "nextup"
        private const val MAX_PENDING = 200
    }
}
