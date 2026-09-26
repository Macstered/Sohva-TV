package com.sohva.tv.core.model.update

/** One asset of a published release (spec 72 §7.1). */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** One release of the public feed, as much of it as the updater reads (spec 72 §7.1). */
data class Release(
    val tag: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

/** What the updater offers: the release, its APK, its checksum file and this device's profile (ABOUT-FR-07). */
data class OfferedUpdate(
    val version: String,
    val build: Int,
    val body: String,
    val apk: ReleaseAsset,
    val checksums: ReleaseAsset?,
    val profile: ReleaseAsset?,
)

/**
 * The feed's rules (spec 72 §4.2, §7.4, §7.5): every installed build since beta 3 reads releases
 * this way, so the rules are the contract. Regular expressions: callers run these off the main
 * thread (AGENTS.md §4 rule 1).
 */
object Releases {
    private val BUILD = Regex("""[Bb]uild \*\*(\d+)\*\*""")
    private val HEX = Regex("^[0-9a-f]{64}$")
    const val CHECKSUMS: String = "SHA256SUMS.txt"

    /** The first `build **N**` of [body] (§7.5 rule 2), or null. */
    fun statedBuild(body: String): Int? = BUILD.find(body)?.groupValues?.get(1)?.toIntOrNull()

    /** The version shown to viewers: the tag without a leading `v` (§7.5 rule 7). */
    fun version(tag: String): String = tag.removePrefix("v")

    /**
     * ABOUT-FR-07: of the non-draft releases that state a build above [installed] and carry an APK,
     * the highest build; prereleases count. [sdk] picks the profile (ABOUT-FR-13).
     */
    fun select(releases: List<Release>, installed: Int, sdk: Int): OfferedUpdate? {
        var best: OfferedUpdate? = null
        for (release in releases) {
            if (release.draft) continue
            val build = statedBuild(release.body) ?: continue
            if (build <= installed || (best != null && build <= best.build)) continue
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: continue
            best = OfferedUpdate(
                version = version(release.tag),
                build = build,
                body = release.body,
                apk = apk,
                checksums = release.assets.firstOrNull { it.name.equals(CHECKSUMS, ignoreCase = true) },
                profile = profileName(apk.name, sdk)?.let { name -> release.assets.firstOrNull { it.name == name } },
            )
        }
        return best
    }

    /** The installed build's own release (ABOUT-FR-06 step 3): the first non-draft one stating [installed]. */
    fun own(releases: List<Release>, installed: Int): Release? = releases.firstOrNull { !it.draft && statedBuild(it.body) == installed }

    /** ABOUT-FR-13: `.api31.dm` from Android 12, `.api28.dm` on Android 9–11, none below. */
    fun profileName(apkName: String, sdk: Int): String? {
        val base = apkName.removeSuffix(".apk").removeSuffix(".APK")
        return when {
            sdk >= 31 -> "$base.api31.dm"
            sdk >= 28 -> "$base.api28.dm"
            else -> null
        }
    }

    /**
     * The published digest of [name] in a `SHA256SUMS.txt` text (§7.4): per trimmed line, the digest
     * before the first space (lower-cased, 64 hex digits), the name after it without a leading `*`;
     * the first exact match wins.
     */
    fun digest(sums: String, name: String): String? {
        for (raw in sums.lineSequence()) {
            val line = raw.trim()
            val space = line.indexOf(' ')
            if (space <= 0) continue
            val digest = line.substring(0, space).lowercase()
            val file = line.substring(space + 1).trim().removePrefix("*")
            if (file == name && HEX.matches(digest)) return digest
        }
        return null
    }
}
