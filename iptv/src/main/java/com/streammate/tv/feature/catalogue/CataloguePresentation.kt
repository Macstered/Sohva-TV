package com.streammate.tv.feature.catalogue

/** Shared poster placeholder, also used by detail and cast cards. */
internal fun catalogueInitials(title: String): String {
    val words = title.trim()
        .split(' ', '.', '-', ':', '_', '\u00b7', '/')
        .filter { word -> word.firstOrNull()?.isLetter() == true }
    return when (words.size) {
        0 -> title.trim().take(2).uppercase()
        1 -> words.first().take(2).uppercase()
        else -> words.take(2).map { word -> word.first().uppercaseChar() }.joinToString("")
    }
}

/** Display-only normalization for detail breadcrumbs; provider keys stay untouched. */
internal fun catalogueDisplayCategory(category: String): String =
    CATEGORY_DECORATION.replace(category, " ").replace(WHITESPACE_RUN, " ").trim()
        .ifBlank { category }

private val CATEGORY_DECORATION = Regex("""\[[^\[\]]*]|\([^()]*\)""")
private val WHITESPACE_RUN = Regex("""\s{2,}""")
