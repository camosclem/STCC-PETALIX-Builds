package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.LiveChannel
import java.util.Locale

private val categorySpaces = Regex("\\s+")
private val streamingCategory = Regex("(?i)\\bstreaming(?:\\s+(?:tv|channels?|services?))?\\b")
private fun categoryTitle(title: String) = title.trim().lowercase(Locale.ROOT)
    .replace(categorySpaces, " ")
internal fun petalixStreamingCategory(title: String): Boolean =
    streamingCategory.containsMatchIn(title)

/** A presentation index only. Native group IDs, membership, saved settings and locks survive. */
internal class PetalixGuideGroupIndex(
    original: List<ChannelGroup>,
    members: (String) -> List<LiveChannel>
) {
    private val aliases = LinkedHashMap<String, List<String>>()
    private val canonical = HashMap<String, String>()
    val groups: List<ChannelGroup> = original.filterNot { !it.special && petalixStreamingCategory(it.title) }
        .groupBy { if (it.special) "special:${it.id}" else "category:${categoryTitle(it.title)}" }
        .values.map { copies ->
            // Native locks still gate each group's members; choose an unlocked destination when present.
            val first = copies.firstOrNull { !it.locked } ?: copies.first()
            val ids = copies.map { it.id }.distinct()
            aliases[first.id] = ids
            ids.forEach { canonical[it] = first.id }
            if (ids.size == 1) first else first.copy(
                count = ids.flatMap(members).distinctBy { it.key }.size
            )
        }
    fun canonicalId(id: String): String = canonical[id] ?: id
    fun idsFor(id: String): List<String> = aliases[id] ?: listOf(id)
}
