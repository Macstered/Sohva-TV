package com.streammate.tv.core.model

data class IptvConfiguration(
    val m3uUrl: String,
    val xmlTvUrl: String,
)

enum class IptvSourceType {
    M3U,
    XTREAM,
}

enum class IptvImportScope {
    LIVE_TV,
    VOD,
    BOTH;

    val importsLiveTv: Boolean get() = this != VOD
    val importsVod: Boolean get() = this != LIVE_TV
}

data class IptvSourceConfiguration(
    val id: String,
    val name: String,
    val type: IptvSourceType,
    val enabled: Boolean = true,
    val connectionLimit: Int = DEFAULT_CONNECTION_LIMIT,
    val priority: Int = 0,
    val importScope: IptvImportScope = IptvImportScope.BOTH,
    val epgOffsetMinutes: Int = DEFAULT_EPG_OFFSET_MINUTES,
    val m3uUrl: String? = null,
    val xmlTvUrl: String? = null,
    val xtreamBaseUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
) {
    init {
        require(SOURCE_ID_PATTERN.matches(id)) { "Invalid source ID" }
        require(name.isNotBlank()) { "Source name must not be blank" }
        require(name.length <= MAX_SOURCE_NAME_LENGTH) { "Source name is too long" }
        require(connectionLimit in 1..MAX_CONNECTION_LIMIT) {
            "Connection limit must be between 1 and $MAX_CONNECTION_LIMIT"
        }
        require(epgOffsetMinutes in MIN_EPG_OFFSET_MINUTES..MAX_EPG_OFFSET_MINUTES) {
            "EPG offset must be between $MIN_EPG_OFFSET_MINUTES and $MAX_EPG_OFFSET_MINUTES minutes"
        }
        require(epgOffsetMinutes % EPG_OFFSET_STEP_MINUTES == 0) {
            "EPG offset must use $EPG_OFFSET_STEP_MINUTES minute steps"
        }
    }

    fun asM3uConfiguration(): IptvConfiguration? = if (
        type == IptvSourceType.M3U && importScope.importsLiveTv
    ) {
        val playlist = m3uUrl?.takeIf(String::isNotBlank) ?: return null
        val guide = xmlTvUrl?.takeIf(String::isNotBlank) ?: return null
        IptvConfiguration(playlist, guide)
    } else {
        null
    }

    override fun toString(): String =
        "IptvSourceConfiguration(id=$id, name=$name, type=$type, enabled=$enabled, " +
            "connectionLimit=$connectionLimit, priority=$priority, importScope=$importScope, " +
            "epgOffsetMinutes=$epgOffsetMinutes, " +
            "credentials=<redacted>)"

    companion object {
        const val DEFAULT_CONNECTION_LIMIT = 1
        const val MAX_CONNECTION_LIMIT = 16
        const val DEFAULT_EPG_OFFSET_MINUTES = 0
        const val MIN_EPG_OFFSET_MINUTES = -12 * 60
        const val MAX_EPG_OFFSET_MINUTES = 12 * 60
        const val EPG_OFFSET_STEP_MINUTES = 30
        const val LEGACY_SOURCE_ID = "m3u-primary"
        private const val MAX_SOURCE_NAME_LENGTH = 100
        private val SOURCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")

        fun fromLegacy(configuration: IptvConfiguration) = IptvSourceConfiguration(
            id = LEGACY_SOURCE_ID,
            name = "IPTV",
            type = IptvSourceType.M3U,
            m3uUrl = configuration.m3uUrl,
            xmlTvUrl = configuration.xmlTvUrl,
        )
    }
}
