package com.streammate.tv.app

/**
 * The languages TMDB answers in that the app offers for titles, plots and
 * artwork. Tags are what TMDB's `language` parameter takes.
 */
object MetadataLanguages {
    val TAGS: List<String> = listOf(
        "en-US", "fi-FI", "sv-SE", "nb-NO", "da-DK", "de-DE", "nl-NL", "fr-FR",
        "es-ES", "pt-PT", "pt-BR", "it-IT", "pl-PL", "cs-CZ", "hu-HU", "ru-RU",
        "tr-TR", "el-GR", "ar-SA", "ja-JP", "ko-KR", "zh-CN",
    )

    fun isSupported(tag: String?): Boolean = tag != null && tag in TAGS

    /** Finnish interface, Finnish metadata; every other interface language starts in English. */
    fun defaultFor(interfaceLanguageTag: String?): String =
        if (interfaceLanguageTag?.startsWith("fi") == true) "fi-FI" else "en-US"
}
