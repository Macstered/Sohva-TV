package com.streammate.tv.feature.catalogue

import com.streammate.tv.app.CataloguePreferredCopy

/**
 * How well a copy answers what matters in this house.
 *
 * Higher is better, and equal scores are left in library order - so a
 * preference that nothing satisfies, or that everything satisfies equally,
 * changes nothing at all.
 *
 * Everything below reads a claim rather than a fact. `FIN` in a name does not
 * guarantee Finnish audio, and a copy without it may still have it, so a
 * preference sorts copies by what providers assert and is exactly as reliable
 * as they are. Believing the stream over the filename needs the player rather
 * than the name, and is a later job.
 */
fun catalogueCopyScore(claims: CatalogueCopyClaims, preferred: CataloguePreferredCopy): Int =
    when (preferred) {
        CataloguePreferredCopy.NONE -> 0
        // A copy sold as Finnish beats one sold as carrying several audio
        // tracks, which beats one sold to the Nordics as a whole. Each is a
        // weaker version of the same claim rather than a different one.
        CataloguePreferredCopy.FINNISH_AUDIO -> when {
            CatalogueCopyLanguage.FINNISH in claims.languages -> 3
            CatalogueCopyLanguage.MULTIPLE_AUDIO in claims.languages -> 2
            CatalogueCopyLanguage.NORDIC in claims.languages -> 1
            else -> 0
        }
        // `MULTI-SUBS` is the only marker that is actually about subtitles. A
        // Finnish copy is the next best guess, because a copy sold here usually
        // carries them, and a Nordic one the guess after that.
        CataloguePreferredCopy.FINNISH_SUBTITLES -> when {
            CatalogueCopyLanguage.SUBTITLED in claims.languages -> 3
            CatalogueCopyLanguage.FINNISH in claims.languages -> 2
            CatalogueCopyLanguage.NORDIC in claims.languages -> 1
            else -> 0
        }
        // Size first, and dynamic range only to separate two copies of the same
        // size: 1080p HDR is a better picture than 1080p, and 4K is a better
        // picture than either.
        CataloguePreferredCopy.LARGEST_PICTURE ->
            pictureSize(claims.picture) * 2 + if (highDynamicRange(claims.picture)) 1 else 0
    }

/**
 * How large a copy says its picture is, in the terms providers write.
 *
 * A copy that says nothing scores zero rather than being guessed at, which
 * leaves it behind anything that made a claim and level with everything else
 * that stayed quiet.
 */
private fun pictureSize(picture: List<String>): Int = picture.maxOfOrNull { term ->
    when (term.lowercase()) {
        "4k uhd", "2160p" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }
} ?: 0

private fun highDynamicRange(picture: List<String>): Boolean =
    picture.any { it in HIGH_DYNAMIC_RANGE }

private val HIGH_DYNAMIC_RANGE = setOf("Dolby Vision", "HDR10+", "HDR10")
