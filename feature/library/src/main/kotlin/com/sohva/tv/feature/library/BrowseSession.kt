package com.sohva.tv.feature.library

import com.sohva.tv.core.data.vod.WallItem

/**
 * One wall's browse session (spec 40 VOD-FR-56): kept by the app for each mode while it runs, so
 * leaving Movies and coming back finds the same destination, view, search and card. Keys and
 * positions only: the focused card is the one entry kept, as the cursor its pages are read around.
 * Main thread only. Profiles (M6) reset it when the active profile changes.
 */
class BrowseSession {
    internal var view: RailView = RailView.GROUPS
    internal val lastOf: MutableMap<RailView, String> = HashMap()
    internal var selected: String? = null
    internal var search: String = ""
    internal var focused: WallItem? = null
    internal var focusedIndex: Int = 0
    internal var focusOnWall: Boolean = false

    /** Whether a wall of this mode has been shown: only the first entry focuses History (VOD-FR-49). */
    internal var entered: Boolean = false

    /** Back to a first visit: for a profile change (M6) and between tests. */
    fun clear() {
        view = RailView.GROUPS
        lastOf.clear()
        selected = null
        search = ""
        focused = null
        focusedIndex = 0
        focusOnWall = false
        entered = false
    }
}
