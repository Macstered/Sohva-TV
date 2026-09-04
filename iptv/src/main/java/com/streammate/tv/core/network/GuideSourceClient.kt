package com.streammate.tv.core.network

import com.streammate.tv.core.error.ResourceArgument
import com.streammate.tv.core.error.LocalizedException
import com.streammate.tv.core.R as CoreR
import androidx.annotation.StringRes
import com.streammate.tv.core.security.SecretRedactor
import com.streammate.tv.iptv.xmltv.CompressionAwareInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface GuideSource {
    suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T
}

class GuideSourceClient(
    private val client: OkHttpClient,
) : GuideSource {
    override suspend fun <T> withSource(url: String, block: suspend (InputStream) -> T): T =
        withContext(Dispatchers.IO) {
            val normalizedUrl = validateSourceUrl(url)
            val request = Request.Builder().url(normalizedUrl).get().build()
            val response = try {
                client.newCall(request).execute()
            } catch (error: IOException) {
                throw transportFailure(error)
            }
            try {
                if (!response.isSuccessful) {
                    throw GuideSourceException(CoreR.string.error_source_http, listOf(response.code))
                }
                CompressionAwareInputStream.wrap(response.body.byteStream()).use { stream -> block(stream) }
            } finally {
                response.close()
            }
        }

    private fun validateSourceUrl(value: String): String = runCatching {
        IptvSourceUrlPolicy.normalize(value, ResourceArgument(CoreR.string.error_source_label))
    }.getOrElse { error ->
        throw GuideSourceException(
            (error as? LocalizedException)?.messageResource ?: CoreR.string.error_source_url_malformed,
            (error as? LocalizedException)?.messageArguments.orEmpty(),
            cause = error,
        )
    }
}

class GuideSourceException(
    @StringRes messageResource: Int,
    messageArguments: List<Any> = emptyList(),
    logMessage: String? = null,
    cause: Throwable? = null,
) : LocalizedException(messageResource, messageArguments, logMessage, cause)

/**
 * Wraps a transport failure so the sentence around it is translatable while
 * the provider's own detail still reaches the user - redacted, because these
 * messages routinely quote the playlist URL and its credentials.
 */
private fun transportFailure(error: Throwable): GuideSourceException {
    val detail = SecretRedactor.redact(error.message)
    return if (detail == null) {
        GuideSourceException(CoreR.string.error_transport_failed, cause = error)
    } else {
        GuideSourceException(
            CoreR.string.error_transport_failed_detail,
            listOf(detail),
            logMessage = detail,
            cause = error,
        )
    }
}
