package com.nuvio.tv.petalix

import android.content.Context
import com.nuvio.tv.livetv.model.LiveChannel
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

internal object PetalixChannelLogos {
    private var index: Map<String, String>? = null
    private val originals = ConcurrentHashMap<String, String>()
    private fun key(value: String) = value.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    @Synchronized private fun index(context: Context): Map<String,String> {
        index?.let { return it }
        val rows = JSONArray(context.assets.open("petalix/channel-logo-sources.json").bufferedReader().use { it.readText() })
        val result = HashMap<String,String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val aliases = row.getJSONArray("aliases")
            for (j in 0 until aliases.length()) result.putIfAbsent(key(aliases.getString(j)), row.getString("url"))
        }
        index = result
        return result
    }
    fun improve(context: Context, channel: LiveChannel): LiveChannel {
        val logos = index(context)
        val improved = channel.tvgId?.let { logos[key(it)] } ?: logos[key(petalixCleanChannelName(channel.name))] ?: return channel
        channel.logo?.takeIf { it != improved && it.isNotBlank() }?.let { originals.putIfAbsent(improved,it) }
        return channel.copy(logo = improved)
    }
    fun fallback(url: String?): String? = url?.let { originals[it] }
}
