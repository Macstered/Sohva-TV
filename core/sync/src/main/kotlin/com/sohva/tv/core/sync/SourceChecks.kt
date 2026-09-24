package com.sohva.tv.core.sync

import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.AppException
import com.sohva.tv.core.model.source.XtreamAccount
import com.sohva.tv.core.net.http.ProviderHttp
import com.sohva.tv.core.net.http.ProviderRequest
import com.sohva.tv.core.net.m3u.M3uReader
import com.sohva.tv.core.net.xtream.XtreamClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The source page's Test address and Test connection (spec 10 §4.5). Neither saves anything
 * (SRC-FR-26 rebuild rule); both run on the io dispatcher and report what the viewer reads.
 */
class SourceChecks(private val http: ProviderHttp, private val io: CoroutineDispatcher) {
    sealed interface Result {
        /** Entries read from the start of a playlist; [more] when the probe stopped at [PROBE_LIMIT]. */
        data class Playlist(val entries: Int, val more: Boolean) : Result

        /** The Xtream account signed in; the server's connection limit when it reports one. */
        data class Account(val serverLimit: Int?) : Result

        data class Failed(val error: AppError) : Result
    }

    /** Reads at most [PROBE_LIMIT] entries, then closes the connection (SRC-FR-24, SRC-L-07). */
    suspend fun testPlaylist(address: String): Result = checked {
        http.get(address, ProviderRequest.SOURCE) { body ->
            val reader = M3uReader(body)
            var count = 0
            while (count < PROBE_LIMIT && reader.next() != null) count++
            Result.Playlist(count, more = count == PROBE_LIMIT)
        }
    }

    /** The account call alone (SRC-FR-25). */
    suspend fun testXtream(account: XtreamAccount): Result = checked {
        Result.Account(XtreamClient(http, account).accountInfo().maxConnections)
    }

    private suspend fun checked(block: suspend () -> Result): Result = withContext(io) {
        try {
            block()
        } catch (e: AppException) {
            Result.Failed(e.error)
        }
    }

    companion object {
        const val PROBE_LIMIT: Int = 500
    }
}
