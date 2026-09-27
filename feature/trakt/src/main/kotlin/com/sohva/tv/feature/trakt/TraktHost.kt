package com.sohva.tv.feature.trakt

import com.sohva.tv.core.model.diagnostics.DiagnosticsLog
import com.sohva.tv.core.model.time.Clock
import com.sohva.tv.feature.trakt.account.SignInResult
import com.sohva.tv.feature.trakt.account.TraktSignIn
import com.sohva.tv.feature.trakt.protocol.RefreshResult
import com.sohva.tv.feature.trakt.protocol.TraktApiClient
import com.sohva.tv.feature.trakt.protocol.TraktAuthClient
import com.sohva.tv.feature.trakt.protocol.TraktCredentials
import com.sohva.tv.feature.trakt.protocol.TraktException
import com.sohva.tv.feature.trakt.protocol.TraktFailure
import com.sohva.tv.feature.trakt.protocol.TraktGate
import com.sohva.tv.feature.trakt.protocol.TraktIdentityClient
import com.sohva.tv.feature.trakt.protocol.TraktTokens
import com.sohva.tv.feature.trakt.scrobble.TraktScrobbler
import com.sohva.tv.feature.trakt.scrobble.TraktScrobbles
import com.sohva.tv.feature.trakt.shelf.TraktShelves
import com.sohva.tv.feature.trakt.store.TraktAccount
import com.sohva.tv.feature.trakt.store.TraktAccountStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** Whether a profile may use Trakt (FR-36): unrestricted profiles only. */
fun interface TraktAccess {
    suspend fun allowed(profile: String): Boolean
}

class TraktDispatchers(val io: CoroutineDispatcher, val parse: CoroutineDispatcher)

/**
 * Trakt for the app (spec 51): the build's credentials, the three clients, the encrypted
 * per-profile records, the accounts as a flow, token refresh one at a time, sign-in, disconnect
 * and profile removal. Built on first use; nothing here touches the network until asked.
 * [offline] is the demo build (FR-37): no call ever leaves the device.
 */
class TraktHost(
    credentials: TraktCredentials,
    private val http: () -> OkHttpClient,
    val store: TraktAccountStore,
    val clock: Clock,
    val dispatchers: TraktDispatchers,
    val access: TraktAccess,
    val log: DiagnosticsLog,
    val appScope: CoroutineScope,
    val offline: Boolean = false,
    /** Monotonic milliseconds for sign-in polling (FR-02). */
    private val monotonic: () -> Long,
    /** Removes the profile's `trakt_state` rows (FR-10). */
    private val forgetCache: suspend (String) -> Unit,
    /** Trakt's two origins; a test points them at its own server. */
    authOrigin: HttpUrl = TraktAuthClient.ORIGIN,
    apiOrigin: HttpUrl = TraktApiClient.ORIGIN,
) {
    @Volatile
    var credentials: TraktCredentials = credentials
        private set

    @Volatile private var origins: Pair<HttpUrl, HttpUrl> = authOrigin to apiOrigin
    private val original = credentials to (authOrigin to apiOrigin)

    private class Clients(val auth: TraktAuthClient, val identity: TraktIdentityClient, val api: TraktApiClient)

    @Volatile private var clients: Clients? = null

    /** One door for every Trakt request of the app: waits after a 429, spaced writes, counts (decision "Trakt request pacing"). */
    @Volatile
    var gate: TraktGate = TraktGate()
        private set

    private fun clients(): Clients = clients ?: synchronized(this) {
        clients ?: Clients(
            TraktAuthClient(http(), credentials, origins.first, gate),
            TraktIdentityClient(http(), credentials, origins.second, gate),
            TraktApiClient(http(), credentials, dispatchers.parse, origins.second, gate = gate),
        ).also { clients = it }
    }

    val configured: Boolean get() = credentials.configured && !offline

    val auth: TraktAuthClient get() = clients().auth
    val identity: TraktIdentityClient get() = clients().identity
    val api: TraktApiClient get() = clients().api

    /** Device tests: every Trakt record gone and the build's own credentials and origins back. */
    suspend fun resetForTests() {
        withContext(dispatchers.io) { store.clearAll() }
        shelves.clear()
        gate = TraktGate()
        credentials = original.first
        origins = original.second
        clients = null
        _accounts.value = emptyMap()
    }

    /** Device tests: a fake Trakt at [url] with test credentials; cached accounts are read again. */
    fun useTestServer(url: HttpUrl, testCredentials: TraktCredentials) {
        credentials = testCredentials
        gate = TraktGate()
        origins = url to url
        clients = null
        _accounts.value = emptyMap()
    }

    private val _accounts = MutableStateFlow<Map<String, TraktAccount?>>(emptyMap())

    /** Accounts read so far, by profile; a missing key has not been read yet, null means none. */
    val accounts: StateFlow<Map<String, TraktAccount?>> = _accounts.asStateFlow()

    private val refreshLock = Mutex()
    private val _syncRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Profiles that want a sync now: a new sign-in (FR-21). */
    val syncRequests: SharedFlow<String> = _syncRequests.asSharedFlow()

    /** Home's stored Watch next and Recommended lists (FR-25, -28). */
    val shelves: TraktShelves by lazy { TraktShelves(this) }

    /** The persisted scrobble queue (FR-19, -20). */
    val scrobbles: TraktScrobbles by lazy { TraktScrobbles(this) }

    /**
     * A scrobbler for one title on a player (FR-15), running its pause settle on [main]; its item
     * is given later with [TraktScrobbler.begin] once resolved.
     */
    fun scrobbler(profile: String, main: CoroutineScope): TraktScrobbler =
        TraktScrobbler(profile, { p, item, action, pct -> scrobbles.submit(p, item, action, pct) }, { p, pct -> scrobbles.active(p, pct) }, main)

    /** Whether [profile] should scrobble at all: a configured build, access, and an account (even one waiting for a new sign-in). */
    suspend fun scrobbles(profile: String): Boolean = configured && access.allowed(profile) && account(profile) != null

    suspend fun account(profile: String): TraktAccount? {
        _accounts.value[profile]?.let { return it }
        if (profile in _accounts.value) return null
        val read = withContext(dispatchers.io) { store.account(profile) }
        _accounts.update { it + (profile to read) }
        return read
    }

    private fun publish(profile: String, account: TraktAccount?) = _accounts.update { it + (profile to account) }

    /** A sign-in for [profile] (FR-02), checking its access before every step. */
    fun signIn(profile: String): TraktSignIn = TraktSignIn(auth, identity, monotonic) { access.allowed(profile) }

    /** Accounts written by the beta 23 importer: read again, and a sync asked for each (decision A1). */
    fun accountsImported(profiles: Collection<String>) {
        _accounts.update { it - profiles.toSet() }
        profiles.forEach { _syncRequests.tryEmit(it) }
    }

    /** FR-02 step 7 and §6: a different account than before starts with an empty cache, stamp and queue. */
    suspend fun saveSignIn(profile: String, result: SignInResult.Success) = withContext(dispatchers.io) {
        val before = store.account(profile)
        if (before != null && before.uuid != result.identity.uuid) {
            store.forgetState(profile)
            forgetCache(profile)
            shelves.forget(profile)
        }
        val account = TraktAccount(result.identity.username, result.identity.uuid, result.tokens, reauthorize = false)
        store.saveAccount(profile, account)
        publish(profile, account)
        log.info("trakt", "signed in")
        _syncRequests.tryEmit(profile)
    }

    /**
     * Current tokens for [profile] (FR-08, -09), refreshed first when they expire within
     * min(24 h, half their lifetime); one refresh at a time. Null when there is no usable sign-in.
     */
    suspend fun tokens(profile: String): TraktTokens? {
        if (!configured || !access.allowed(profile)) return null
        val first = account(profile)?.tokens ?: return null
        if (!needsRefresh(first)) return first
        return refreshLock.withLock {
            val current = withContext(dispatchers.io) { store.account(profile) } ?: return@withLock null
            val tokens = current.tokens ?: return@withLock null
            if (!needsRefresh(tokens)) return@withLock tokens
            when (val r = auth.refresh(tokens.refresh)) {
                is RefreshResult.Refreshed -> {
                    val next = TraktAccount(current.username, current.uuid, r.tokens, reauthorize = false)
                    withContext(dispatchers.io) { store.saveAccount(profile, next) }
                    publish(profile, next)
                    r.tokens
                }
                RefreshResult.Reauthorize -> {
                    markReauthorize(profile)
                    null
                }
            }
        }
    }

    /** The access token is refused (401 or `invalid_grant`): tokens removed, the account kept with the flag. */
    suspend fun markReauthorize(profile: String) = withContext(dispatchers.io) {
        val current = store.account(profile) ?: return@withContext
        val flagged = TraktAccount(current.username, current.uuid, null, reauthorize = true)
        store.saveAccount(profile, flagged)
        publish(profile, flagged)
        log.info("trakt", "sign-in no longer accepted")
    }

    /** FR-08, -09: refresh within min(24 h, half the lifetime) of expiry, so a 24 h token is not refreshed on every call. */
    private fun needsRefresh(tokens: TraktTokens): Boolean = tokens.expiresAt - clock.wallMillis() <= minOf(DAY_MS, tokens.lifetimeMs / 2)

    /** FR-10, -11: this TV forgets the account and every cached record; nothing is sent to Trakt. */
    suspend fun disconnect(profile: String) = withContext(dispatchers.io) {
        store.forget(profile)
        forgetCache(profile)
        shelves.forget(profile)
        publish(profile, null)
        log.info("trakt", "disconnected")
    }

    /** Calls an API operation with current tokens; a 401 flags re-authorisation (§4.12). */
    suspend fun <T> withTokens(profile: String, block: suspend (TraktTokens) -> T): T {
        val tokens = tokens(profile) ?: throw TraktException(TraktFailure.REAUTHORIZE)
        return try {
            block(tokens)
        } catch (e: TraktException) {
            if (e.failure == TraktFailure.REAUTHORIZE) markReauthorize(profile)
            throw e
        }
    }

    companion object {
        const val DAY_MS: Long = 24L * 60 * 60 * 1000
    }
}
