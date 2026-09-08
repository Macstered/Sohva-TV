package com.streammate.tv.app

import kotlin.math.roundToInt

/**
 * How large the interface is drawn. Everything scales together, layouts and
 * text alike, by changing the density the whole app is laid out with; a
 * projector or a very large screen reads better a step smaller.
 */
enum class InterfaceScale(val factor: Float) {
    NORMAL(1f),
    COMPACT(0.9f),
    SMALL(0.8f),
    /** A tester on a large screen found Small one step short of comfortable. */
    SMALLER(0.7f);

    /** The scale as a whole percentage, for the picker's labels. */
    val percent: Int get() = (factor * 100).roundToInt()

    companion object {
        val DEFAULT = NORMAL

        /** The stored name, or the default for anything unknown or absent. */
        fun fromStored(name: String?): InterfaceScale = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
