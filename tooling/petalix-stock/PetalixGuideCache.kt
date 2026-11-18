package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel

/** Bounded dataset cache. Focus/category changes reuse the expensive channel policy work.
 * Parental visibility is still applied by Nuvio after filtering, before presentation.
 */
internal class PetalixGuideCache {
    private var pool: List<LiveChannel>? = null
    private val filtered = LinkedHashMap<Set<String>, List<LiveChannel>>()
    private data class Projection(val channels: List<LiveChannel>, val bitrates: Map<String, Long>, val result: List<LiveChannel>)
    private val projections = ArrayList<Projection>()

    @Synchronized
    fun channels(channels: List<LiveChannel>, savedKeys: Set<String>): List<LiveChannel> {
        if (pool !== channels) {
            pool = channels
            filtered.clear()
            projections.clear()
        }
        filtered[savedKeys]?.let { return it }
        val result = channels.filter { petalixGuideChannelAllowed(it, it.key in savedKeys) }
        if (filtered.size >= 3) filtered.remove(filtered.keys.first())
        filtered[savedKeys.toSet()] = result
        return result
    }

    @Synchronized
    fun presentation(channels: List<LiveChannel>, bitrates: Map<String, Long>): List<LiveChannel> {
        projections.firstOrNull { it.bitrates === bitrates && it.channels == channels }?.let { return it.result }
        val result = petalixGuidePresentation(channels, bitrates)
        if (projections.size >= 2) projections.removeAt(0)
        projections.add(Projection(channels, bitrates, result))
        return result
    }
}

internal val petalixGuideCache = PetalixGuideCache()
