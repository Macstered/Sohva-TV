package com.sohva.tv.feature.trakt.store

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.protocol.TraktJson
import com.sohva.tv.feature.trakt.protocol.fields
import okio.Buffer

/** Encrypts and decrypts stored values with the app's main envelope cipher (spec 51 §6). */
interface TraktCipher {
    fun encrypt(plaintext: String): String

    fun decrypt(value: String): String
}

/** Where the store keeps its strings; SharedPreferences on a device, a map in JVM tests. */
interface TraktPrefs {
    fun get(key: String): String?

    /** Written synchronously (§6: `commit()`); callers are off the main thread. */
    fun put(key: String, value: String?)

    fun keys(): Set<String>
}

@SuppressLint("ApplySharedPref")
class AndroidTraktPrefs(context: Context) : TraktPrefs {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    // Synchronous as spec 51 §6 asks; the store is used on the io dispatcher only.
    override fun put(key: String, value: String?) = prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }

    override fun keys(): Set<String> = prefs.all.keys

    companion object {
        /** Not beta 23's `trakt_accounts`: that file stays for the importer, readable only with the old key (decision A1). */
        const val FILE: String = "sohva_trakt_accounts"
    }
}

/**
 * One profile's Trakt account (§6): the name shown, the account's stable id (rebuild rule: kept
 * so a different account after "Sign in again" starts clean), and tokens, which are absent once
 * Trakt no longer accepts the sign-in.
 */
class TraktAccount(val username: String, val uuid: String, val tokens: TraktTokens?, val reauthorize: Boolean) {
    override fun toString(): String = "TraktAccount(reauthorize=$reauthorize)"
}

/**
 * Per-profile Trakt records (§6) in `sohva_trakt_accounts`, values encrypted: the account, the pending
 * scrobbles, the in-flight START, the activity stamp and format version, and Home's two lists.
 */
class TraktAccountStore(private val prefs: TraktPrefs, private val cipher: TraktCipher) {
    fun account(profile: String): TraktAccount? = secret("account:$profile")?.let { runCatching { decode(it) }.getOrNull() }

    fun saveAccount(profile: String, account: TraktAccount) {
        val json = TraktJson.write {
            beginObject().name("username").value(account.username).name("uuid").value(account.uuid).name("reauthorize").value(account.reauthorize)
            account.tokens?.let { name("access").value(it.access).name("refresh").value(it.refresh).name("expires").value(it.expiresAt).name("lifetime").value(it.lifetimeMs) }
            endObject()
        }
        putSecret("account:$profile", json)
    }

    private fun decode(text: String): TraktAccount {
        var username = ""
        var uuid = ""
        var reauthorize = false
        var access: String? = null
        var refresh: String? = null
        var expires = 0L
        var lifetime = DEFAULT_LIFETIME
        TraktJson.parse(Buffer().writeUtf8(text)) { j ->
            j.fields { f ->
                when (f) {
                    "username" -> username = j.nextString()
                    "uuid" -> uuid = j.nextString()
                    "reauthorize" -> reauthorize = j.nextBoolean()
                    "access" -> access = j.nextString()
                    "refresh" -> refresh = j.nextString()
                    "expires" -> expires = j.nextLong()
                    "lifetime" -> lifetime = j.nextLong()
                    else -> j.skipValue()
                }
            }
        }
        val a = access
        val r = refresh
        return TraktAccount(username, uuid, if (a != null && r != null) TraktTokens(a, r, expires, lifetime) else null, reauthorize)
    }

    fun secret(key: String): String? = prefs.get(key)?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    fun putSecret(key: String, plaintext: String?) = prefs.put(key, plaintext?.let(cipher::encrypt))

    fun long(key: String): Long = prefs.get(key)?.toLongOrNull() ?: 0L

    fun putLong(key: String, value: Long?) = prefs.put(key, value?.toString())

    /** Profiles with an account, for the demo seed and the importer. */
    fun profiles(): List<String> = prefs.keys().filter { it.startsWith("account:") }.map { it.removePrefix("account:") }

    /** Device tests only: every record of every profile. */
    fun clearAll() = prefs.keys().forEach { prefs.put(it, null) }

    /** Disconnect (FR-10): every record of the profile. */
    fun forget(profile: String) {
        for (prefix in PROFILE_KEYS) prefs.put("$prefix:$profile", null)
    }

    /** A different account signed in (§6 rebuild rule): its predecessor's cache, stamp and queue go. */
    fun forgetState(profile: String) {
        for (prefix in STATE_KEYS) prefs.put("$prefix:$profile", null)
    }

    companion object {
        val STATE_KEYS: List<String> = listOf(
            "pending", "active", "activity", "activity-mw", "activity-mp", "activity-ew", "activity-ep", "format", "recommendations", "nextup",
        )
        val PROFILE_KEYS: List<String> = STATE_KEYS + "account"

        /** Trakt's access tokens last three months; used when a stored record has no lifetime. */
        const val DEFAULT_LIFETIME: Long = 90L * 24 * 60 * 60 * 1000
    }
}
