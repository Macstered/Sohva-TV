package com.streammate.tv.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One asset of a GitHub release: the APK, the checksums, the tester pack. */
data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)

/** A GitHub release as the app reads it. */
data class AppRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>,
) {
    /** The Android version code the release body states, as "Android build **3**". */
    val versionCode: Int? get() = VERSION_CODE_PATTERN.find(body)?.groupValues?.get(1)?.toIntOrNull()

    val versionName: String get() = tagName.removePrefix("v")

    val apk: ReleaseAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    val checksums: ReleaseAsset? get() = assets.firstOrNull { it.name.equals("SHA256SUMS.txt", ignoreCase = true) }

    private companion object {
        val VERSION_CODE_PATTERN = Regex("""[Bb]uild \*\*(\d+)\*\*""")
    }
}

/** A release newer than the installed build, with what is needed to fetch it. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apk: ReleaseAsset,
    val checksums: ReleaseAsset?,
)

/**
 * Reading the public release list and deciding whether it holds an update.
 *
 * Pure functions, so the decision can be tested against captured JSON without
 * a network. The release body is the source of the version code because the
 * tag carries only the version name; every published release states
 * "Android build **N**" in its first lines, and the packaging receipt requires
 * it.
 */
object AppUpdates {
    const val RELEASES_URL = "https://api.github.com/repos/Macstered/Sohva-TV/releases?per_page=10"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseReleases(text: String): List<AppRelease> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val release = element.jsonObject
            AppRelease(
                tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                name = release["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false,
                draft = release["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { asset ->
                    val fields = asset.jsonObject
                    ReleaseAsset(
                        name = fields["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        downloadUrl = fields["browser_download_url"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null,
                        sizeBytes = fields["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                    )
                },
            )
        }

    /**
     * The newest published release whose stated build is above [installedVersionCode]
     * and that carries an APK. Drafts and releases with no stated build are
     * never offered: a release that cannot say what it is cannot be compared.
     */
    fun selectUpdate(releases: List<AppRelease>, installedVersionCode: Int): AvailableUpdate? =
        releases
            .asSequence()
            .filter { !it.draft }
            .mapNotNull { release ->
                val code = release.versionCode ?: return@mapNotNull null
                val apk = release.apk ?: return@mapNotNull null
                if (code <= installedVersionCode) return@mapNotNull null
                AvailableUpdate(
                    versionName = release.versionName,
                    versionCode = code,
                    notes = release.body,
                    apk = apk,
                    checksums = release.checksums,
                )
            }
            .maxByOrNull { it.versionCode }

    /** The lower-case hex digest `SHA256SUMS.txt` states for [fileName], or null. */
    fun expectedChecksum(checksums: String, fileName: String): String? =
        checksums.lineSequence()
            .map { it.trim() }
            .mapNotNull { line ->
                val digest = line.substringBefore(' ').lowercase()
                val name = line.substringAfter(' ').trim().removePrefix("*")
                if (name == fileName && digest.length == 64 && digest.all { it in "0123456789abcdef" }) digest else null
            }
            .firstOrNull()
}
