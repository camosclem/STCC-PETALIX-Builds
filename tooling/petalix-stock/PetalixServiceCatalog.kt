package com.nuvio.tv.petalix

import org.json.JSONArray
import org.json.JSONObject

/** Keep a customer's other collections and their service rows, while retiring old service tiles. */
internal object PetalixServiceCatalog {
    private const val collectionId = "collection-streaming-services-only"

    fun reconcile(current: String, bundled: String): String {
        val collections = JSONArray(current)
        val template = JSONArray(bundled).getJSONObject(0)
        val desired = template.getJSONArray("folders")
        val allowed = (0 until desired.length()).map { desired.getJSONObject(it).getString("id") }
        var found = false
        for (i in 0 until collections.length()) {
            val collection = collections.optJSONObject(i) ?: continue
            if (collection.optString("id") != collectionId) continue
            found = true
            val old = collection.optJSONArray("folders") ?: JSONArray()
            val folders = JSONArray()
            for (id in allowed) {
                val fresh = (0 until desired.length()).map { desired.getJSONObject(it) }
                    .first { it.getString("id") == id }
                val existing = (0 until old.length()).mapNotNull { old.optJSONObject(it) }
                    .firstOrNull { it.optString("id") == id }
                // Retain the owner's edited catalog sources. Branding and artwork come from this build.
                val revised = JSONObject(fresh.toString())
                if (existing != null && existing.has("sources")) revised.put("sources", existing.getJSONArray("sources"))
                folders.put(revised)
            }
            collection.put("folders", folders)
            collection.put("title", "Streaming Services")
        }
        if (!found) collections.put(template)
        return collections.toString()
    }
}
