package com.streammate.tv.addons

import androidx.annotation.OptIn
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import com.sohva.tv.addons.AddonSubtitle
import com.sohva.tv.addons.AddonSubtitlePolicy
import java.security.MessageDigest
import java.util.Locale

/** Session-only identifiers. Never put a configured subtitle URL in semantics/logs. */
internal fun addonSubtitleChoiceKey(subtitle: AddonSubtitle, providerId: String?): String = "external-" +
    MessageDigest.getInstance("SHA-256").digest(listOf(providerId.orEmpty(), subtitle.id, subtitle.language, subtitle.url)
        .joinToString("") { "${it.length}:$it" }.toByteArray()).joinToString("") { "%02x".format(it) }

@OptIn(UnstableApi::class)
internal class AddonEmbeddedSubtitle(val group: TrackGroup, val index: Int, merged: Boolean = false) {
    val language = AddonSubtitlePolicy.language(group.getFormat(index).language) ?: "und"
    val label = group.getFormat(index).label?.takeIf { it.isNotBlank() }?.take(160)
    // MergingMediaSource prefixes video group IDs with "0:" while a sidecar is
    // present. Keep the choice stable when that sidecar is removed again.
    val key = "embedded:${if (merged) group.id.removePrefix("0:") else group.id}:$index"
}

internal fun addonSubtitleLanguageName(value: String, locale: Locale = Locale.getDefault(), unknownLanguage: String = "Unknown language"): String {
    val normalized = AddonSubtitlePolicy.language(value) ?: return unknownLanguage
    return Locale.forLanguageTag(normalized).getDisplayLanguage(locale).takeIf { it.isNotBlank() && it != normalized } ?: normalized.uppercase(Locale.ROOT)
}
