package com.sohva.tv.core.model.guide

/**
 * When a programme can be played from the provider's archive (spec 22 CATCH-FR-10). Pure, and
 * cheap enough for the guide to ask while it prepares a row's labels (CATCH-NFR-02).
 */
object CatchupRules {
    const val MAX_DAYS: Int = 365
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Types whose whole address comes from the channel's template. */
    private val TEMPLATE_TYPES = setOf("default", "append", "vod")

    /** Types that build their address from the live address alone. */
    private val BUILT_TYPES = setOf("shift", "timeshift", "xtream", "xc")

    /** The stored type as the rules read it: trimmed, lower-cased (root locale), blank = none. */
    fun normalType(type: String?): String? = type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** True when [type] can ever play: a template type with a template, or a built type. */
    fun supported(type: String?, hasTemplate: Boolean): Boolean {
        val t = normalType(type) ?: return false
        return t in BUILT_TYPES || (t in TEMPLATE_TYPES && hasTemplate)
    }

    /**
     * CATCH-FR-10: the channel declares an archive of [days] (capped at [MAX_DAYS]) of a type that
     * can play, and the programme started at or before [now] and no longer ago than the archive.
     */
    fun offers(type: String?, days: Int?, hasTemplate: Boolean, start: Long, now: Long): Boolean {
        val depth = days?.coerceAtMost(MAX_DAYS) ?: return false
        if (depth <= 0 || !supported(type, hasTemplate)) return false
        return start <= now && start >= now - depth * DAY_MS
    }
}
