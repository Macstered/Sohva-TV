package com.streammate.tv.app

/**
 * How large the interface is drawn. Everything scales together, layouts and
 * text alike, by changing the density the whole app is laid out with; a
 * projector or a very large screen reads better a step smaller.
 */
enum class InterfaceScale(val factor: Float) {
    NORMAL(1f),
    COMPACT(0.9f),
    SMALL(0.8f);

    companion object {
        val DEFAULT = NORMAL

        /** The stored name, or the default for anything unknown or absent. */
        fun fromStored(name: String?): InterfaceScale = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
