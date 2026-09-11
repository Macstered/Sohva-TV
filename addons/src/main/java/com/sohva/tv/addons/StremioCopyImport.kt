package com.sohva.tv.addons

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Opaque pairing code; the displayed URL is constrained to Stremio by the client. */
class StremioImportLink(internal val code: String, val authorizationUrl: String) {
    init {
        val url = authorizationUrl.toHttpUrlOrNull() ?: fail(AddonFailure.INVALID_RESPONSE)
        if (code.length !in 4..128 || code.any { !it.isLetterOrDigit() || it.code >= 128 } ||
            url.scheme != "https" || url.host != "link.stremio.com" || url.port != 443 ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null ||
            url.encodedPath != "/$code") fail(AddonFailure.INVALID_RESPONSE)
    }
    override fun toString() = "StremioImportLink([redacted])"
}

interface StremioCopyApi {
    suspend fun createLink(): StremioImportLink
    /** Null means not authorized yet. No token is returned to UI or storage. */
    suspend fun readAddons(link: StremioImportLink, recheckAccess: suspend () -> Unit = {}): AddonCopyList?
}

sealed interface StremioCopyEvent {
    class Waiting(val link: StremioImportLink) : StremioCopyEvent
    class Ready(val addons: AddonCopyList) : StremioCopyEvent
}

/** Foreground caller owns cancellation. A fresh attempt always creates a new link. */
class StremioCopyImport(
    private val access: AddonManagementAccess,
    private val api: StremioCopyApi = StremioCopyClient(),
    private val lifetimeMillis: Long = 10 * 60_000,
    private val pollMillis: Long = 3_000,
) {
    init { require(lifetimeMillis in 1..600_000 && pollMillis in 1..30_000) }

    fun copy(profileId: String): Flow<StremioCopyEvent> = flow {
        suspend fun authorize() {
            validateProfile(profileId)
            if (!access.allowed(profileId)) fail(AddonFailure.ACCESS_DENIED)
        }
        val finished = withTimeoutOrNull(lifetimeMillis) {
            authorize()
            val link = api.createLink()
            authorize()
            emit(StremioCopyEvent.Waiting(link))
            while (true) {
                delay(pollMillis)
                authorize()
                val copied = api.readAddons(link, ::authorize)
                authorize()
                if (copied != null) {
                    emit(StremioCopyEvent.Ready(copied))
                    break
                }
            }
            true
        }
        if (finished == null) fail(AddonFailure.TIMEOUT)
    }
}
