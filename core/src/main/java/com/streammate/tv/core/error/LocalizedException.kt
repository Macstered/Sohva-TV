package com.streammate.tv.core.error

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import com.streammate.tv.core.R
import com.streammate.tv.core.security.SecretRedactor
import java.io.IOException

/**
 * A failure that carries a message the user can read, in whatever language the
 * interface is in.
 *
 * Repositories, network clients and validators run without a Context, so they
 * cannot resolve a string. They used to hand back a finished sentence instead,
 * which meant the message was frozen in the language it was typed in - every
 * import failure, bad address and expired key spoke Finnish no matter what the
 * rest of the app was showing.
 *
 * Carrying [messageResource] and its arguments defers that decision to the
 * screen that displays the failure, via [userMessage].
 *
 * [message] stays populated with a stable English description so crash reports
 * and logs remain readable without a Context.
 */
open class LocalizedException(
    @StringRes val messageResource: Int,
    val messageArguments: List<Any> = emptyList(),
    logMessage: String? = null,
    cause: Throwable? = null,
) : IOException(logMessage ?: StoredFailureMessage.encode(messageResource, messageArguments), cause) {

    constructor(
        @StringRes messageResource: Int,
        vararg messageArguments: Any,
    ) : this(messageResource, messageArguments.toList())

    fun resolve(context: Context): String = context.getString(
        messageResource,
        *messageArguments.map { argument ->
            if (argument is ResourceArgument) context.getString(argument.id) else argument
        }.toTypedArray(),
    )
}

/**
 * A format argument that is itself translatable, such as the name of the field
 * a validation message is about. Resolved alongside the message it goes into.
 */
@JvmInline
value class ResourceArgument(@StringRes val id: Int)

/**
 * The message to show for [this], in the current interface language.
 *
 * Falls back to a plain [Throwable.message] for failures raised by libraries
 * outside our control, and to a generic string when even that is absent - so a
 * screen can always render something rather than an empty error area.
 */
fun Throwable.userMessage(context: Context): String = when (this) {
    is LocalizedException -> resolve(context)
    // Redacting here rather than at each call site means a message raised by a
    // library we do not control cannot put a playlist URL, and the credentials
    // in its query string, on screen just because someone forgot to wrap it.
    else -> SecretRedactor.redact(message) ?: context.getString(R.string.error_unknown)
}

/**
 * Builds a [LocalizedException] around a transport failure.
 *
 * The frame ("could not complete the request") is translatable, while the
 * provider's own detail - "connection refused", "certificate expired" - is kept
 * and redacted, because it is the part that actually tells someone what to fix.
 * Passing the exception's constructor as [build] lets each layer keep its own
 * type without repeating the redact-and-choose dance.
 */
fun <T : LocalizedException> localizedTransportFailure(
    error: Throwable,
    build: (Int, List<Any>, String?, Throwable?) -> T,
): T {
    // A failure that already speaks the viewer's language is passed through in
    // its own type; wrapping it again produced "could not complete the
    // request: resource:2131558691" on screen.
    if (error is LocalizedException) {
        return build(error.messageResource, error.messageArguments, error.message, error)
    }
    val detail = SecretRedactor.redact(error.message)
    return if (detail == null) {
        build(R.string.error_transport_failed, emptyList(), null, error)
    } else {
        build(R.string.error_transport_failed_detail, listOf(detail), detail, error)
    }
}

/**
 * A translatable failure written down without a Context - a refresh state's
 * last error, a [Throwable.message] in a log - and the way back into words.
 *
 * The form is the resource id and the arguments, tab-separated, so the health
 * rows and the diagnostics file resolve it in whatever language the interface
 * has by the time someone reads it. Storing the words themselves froze a
 * failure in one language, and a message with a placeholder kept the
 * placeholder: "The source responded with HTTP error %1$d".
 */
object StoredFailureMessage {
    private const val PREFIX = "resource:"
    private const val SEPARATOR = '\t'

    fun encode(@StringRes messageResource: Int, arguments: List<Any>): String = buildString {
        append(PREFIX).append(messageResource)
        arguments.forEach { argument ->
            append(SEPARATOR)
            append(
                when (argument) {
                    is ResourceArgument -> "@${argument.id}"
                    else -> argument.toString().replace(SEPARATOR, ' ').replace('\n', ' ')
                },
            )
        }
    }

    /**
     * [stored] in words for [resources]: a stored failure resolved with its
     * arguments, any other text as it is, nothing for blank. Resource ids are
     * renumbered between builds, so an id written by another build must
     * resolve to nothing rather than to whichever string holds that number
     * now; requiring an error's entry name is what catches it.
     */
    fun resolve(resources: Resources, stored: String?): String? {
        val text = stored?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!text.startsWith(PREFIX)) return text
        val parts = text.removePrefix(PREFIX).split(SEPARATOR)
        val id = parts.first().trim().toIntOrNull() ?: return null
        return runCatching {
            if (!resources.getResourceEntryName(id).startsWith("error_")) return null
            val arguments: List<Any> = parts.drop(1).map { part ->
                when {
                    part.startsWith("@") -> part.drop(1).toIntOrNull()?.let(resources::getString) ?: part
                    else -> part.toIntOrNull() ?: part
                }
            }
            resources.getString(id, *arguments.toTypedArray())
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}

/**
 * What a refresh state records for [this]: a translatable failure by its
 * resource and arguments, anything else by its redacted words.
 */
fun Throwable.storedFailureMessage(): String? = when (this) {
    is LocalizedException -> StoredFailureMessage.encode(messageResource, messageArguments)
    else -> SecretRedactor.redact(message)
}
