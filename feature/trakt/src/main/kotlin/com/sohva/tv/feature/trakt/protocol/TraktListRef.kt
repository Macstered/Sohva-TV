package com.sohva.tv.feature.trakt.protocol

/**
 * What the viewer typed or pasted to add a Trakt list (spec 02 HOME-FR-99): a list's number, a
 * trakt.tv address (`/users/<user>/lists/<list>`, `/lists/<id>`, or a smart list's `/lists/smart/view/<name>`, on
 * trakt.tv or app.trakt.tv, with or
 * without the scheme, query or trailing slash), or else words to search list names with. No regex:
 * the text is short and split by hand (plan/07, AGENTS.md §4 rule 1).
 */
sealed interface TraktListRef {
    data class ById(val id: Long) : TraktListRef

    data class ByUser(val user: String, val list: String) : TraktListRef

    /** A smart list (HOME-FR-101) by its number or its address's name part (`lasten-sarjat-2eadc…`). */
    data class Smart(val id: String) : TraktListRef

    data class Search(val query: String) : TraktListRef

    companion object {
        private const val MAX_TEXT = 300
        private val HOSTS = setOf("trakt.tv", "www.trakt.tv", "app.trakt.tv")

        fun parse(text: String): TraktListRef? {
            val t = text.trim()
            if (t.isEmpty() || t.length > MAX_TEXT) return null
            t.toLongOrNull()?.let { return if (it > 0) ById(it) else null }
            address(t)?.let { return it }
            // Something that looks like an address but is not a list's is not a name either.
            if (t.contains("://") || HOSTS.any { t.startsWith(it) }) return null
            return Search(t.take(MAX_QUERY))
        }

        /** An address or number only (the phone page takes no names). */
        fun parseAddress(text: String): TraktListRef? = parse(text)?.takeIf { it !is Search }

        private fun address(text: String): TraktListRef? {
            var rest = text.substringAfter("://", text)
            val host = rest.substringBefore('/').lowercase()
            if (host !in HOSTS) return null
            rest = rest.substringAfter('/', "").substringBefore('?').substringBefore('#').trimEnd('/')
            val parts = rest.split('/').filter { it.isNotEmpty() }
            return when {
                // The Trakt app's smart lists: /lists/smart/view/<name>, /lists/smart/<name>, /smart-lists/<name>.
                parts.size == 4 && parts[0] == "lists" && parts[1] == "smart" && parts[2] == "view" && safe(parts[3]) -> Smart(parts[3])
                parts.size == 3 && parts[0] == "lists" && parts[1] == "smart" && safe(parts[2]) -> Smart(parts[2])
                parts.size == 2 && parts[0] == "smart-lists" && safe(parts[1]) -> Smart(parts[1])
                parts.size == 2 && parts[0] == "lists" -> parts[1].toLongOrNull()?.takeIf { it > 0 }?.let(::ById)
                parts.size == 4 && parts[0] == "users" && parts[2] == "lists" && safe(parts[1]) && safe(parts[3]) -> ByUser(parts[1], parts[3])
                else -> null
            }
        }

        /** Trakt user names and list slugs: letters, digits, `-`, `_` and `.`; nothing that could leave the path. */
        private fun safe(part: String): Boolean = part.length <= 100 && part != "." && part != ".." &&
            part.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

        private const val MAX_QUERY = 100
    }
}

/** A list's summary (HOME-FR-99): enough to choose it and to title its row. [smart]: a smart list (HOME-FR-101). */
data class TraktListInfo(val id: Long, val name: String, val owner: String?, val items: Int, val likes: Int, val smart: Boolean = false)
