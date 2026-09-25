package com.sohva.tv.core.data.prefs

import com.sohva.tv.core.model.vod.CustomGroup
import com.sohva.tv.core.model.vod.Genre
import org.json.JSONArray
import org.json.JSONObject

/**
 * `custom_catalogue_groups` (spec 42 ORG-FR-60): a JSON array of
 * `{"id", "name", "genres": [wire values], "fromYear"?, "toYear"?, "minRating"?}`, beta 23's shape,
 * so backups carry it unchanged. Reading never throws: an unparsable array is empty; entries
 * without id or name, or unusable once unknown genres are dropped, are left out.
 */
internal object CustomGroupCodec {
    fun decode(text: String?): List<CustomGroup> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> runCatching { group(array.getJSONObject(i)) }.getOrNull() }
    }

    private fun group(o: JSONObject): CustomGroup? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        val wires = o.optJSONArray("genres")
        val genres = (0 until (wires?.length() ?: 0)).mapNotNull { Genre.ofWire(wires!!.optString(it)) }.toSet()
        val group = CustomGroup(
            id, name, genres,
            fromYear = o.optInt("fromYear").takeIf { o.has("fromYear") && !o.isNull("fromYear") },
            toYear = o.optInt("toYear").takeIf { o.has("toYear") && !o.isNull("toYear") },
            minRating = o.optDouble("minRating").takeIf { o.has("minRating") && !o.isNull("minRating") && !it.isNaN() },
        )
        return group.takeIf { it.isUsable }
    }

    fun encode(groups: List<CustomGroup>): String {
        val array = JSONArray()
        for (g in groups) {
            val o = JSONObject().put("id", g.id).put("name", g.name).put("genres", JSONArray(g.genres.sortedBy { it.ordinal }.map { it.wire }))
            g.fromYear?.let { o.put("fromYear", it) }
            g.toYear?.let { o.put("toYear", it) }
            g.minRating?.let { o.put("minRating", it) }
            array.put(o)
        }
        return array.toString()
    }
}
