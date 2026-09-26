package com.sohva.tv.feature.discover.store

import com.sohva.tv.feature.discover.protocol.AddonManifest
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * A parsed manifest written back in the manifest's own schema, keeping only what the app uses:
 * stored rows stay small (a provider manifest can be hundreds of kilobytes of descriptions) and
 * are read with the same strict parser. Resources are written resolved (their types and prefixes
 * explicit), so no inheritance rule applies on reading.
 */
internal object ManifestJson {
    fun write(w: JsonWriter, m: AddonManifest) {
        w.beginObject()
        w.name("id").value(m.id)
        w.name("version").value(m.version)
        w.name("name").value(m.name)
        w.name("types")
        strings(w, m.types)
        w.name("resources").beginArray()
        m.resources.forEach { r ->
            w.beginObject()
            w.name("name").value(r.name)
            w.name("types")
            strings(w, r.types)
            w.name("idPrefixes")
            strings(w, r.idPrefixes)
            w.endObject()
        }
        w.endArray()
        w.name("catalogs").beginArray()
        m.catalogs.forEach { c ->
            w.beginObject()
            w.name("type").value(c.type)
            w.name("id").value(c.id)
            w.name("name").value(c.name)
            w.name("extra").beginArray()
            c.extras.forEach { e ->
                w.beginObject()
                w.name("name").value(e.name)
                w.name("isRequired").value(e.required)
                w.name("options")
                strings(w, e.options)
                w.endObject()
            }
            w.endArray()
            c.pageSize?.let { w.name("pageSize").value(it.toLong()) }
            w.name("showInHome").value(c.showInHome)
            w.endObject()
        }
        w.endArray()
        w.name("behaviorHints").beginObject().name("configurationRequired").value(m.configurationRequired).endObject()
        w.endObject()
    }

    fun read(r: JsonReader): AddonManifest = AddonManifest.read(r)

    private fun strings(w: JsonWriter, values: List<String>) {
        w.beginArray()
        values.forEach { w.value(it) }
        w.endArray()
    }
}
