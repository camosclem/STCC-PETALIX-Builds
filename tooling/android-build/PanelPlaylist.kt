package com.nuvio.tv.petalix

import androidx.datastore.preferences.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Overlay the provider's guide rules without overwriting each profile's local choices. */
internal object PanelPlaylist {
    private val storage=stringPreferencesKey("panel_playlist_v1")
    private fun strings(a:JSONArray?):List<String> = if(a==null) emptyList() else (0 until a.length()).map { a.getString(it) }
    fun store(p:MutablePreferences,document:JSONObject) {
        val rules=document.getJSONObject("rules")
        require(rules.getJSONArray("hiddenChannels").length()<=30000)
        require(rules.getJSONArray("hiddenGroups").length()<=3000)
        require(rules.getJSONArray("groupOrder").length()<=3000)
        require(rules.getJSONObject("channelOrder").length()<=3000)
        val value=rules.toString();require(value.length<=2_000_000)
        if(p[storage]!=value)p[storage]=value
    }
    fun view(p:Preferences,source:String):Preferences {
        val raw=p[storage]?:return p
        val r=runCatching { JSONObject(raw) }.getOrNull()?:return p
        val out=p.toMutablePreferences()
        val channels=strings(r.optJSONArray("hiddenChannels")).map { "$source|$it" }.toSet()
        val groups=strings(r.optJSONArray("hiddenGroups")).flatMap { listOf(it,"$source::$it") }.toSet()
        if(channels.isNotEmpty())out[stringSetPreferencesKey("hidden_channels")]=(p[stringSetPreferencesKey("hidden_channels")]?:emptySet())+channels
        if(groups.isNotEmpty())out[stringSetPreferencesKey("hidden_groups")]=(p[stringSetPreferencesKey("hidden_groups")]?:emptySet())+groups
        val groupOrder=strings(r.optJSONArray("groupOrder")).flatMap { listOf(it,"$source::$it") }
        if(groupOrder.isNotEmpty()) {
            out[stringPreferencesKey("group_order")]=JSONArray(groupOrder).toString()
            out[stringPreferencesKey("group_sort")]="custom"
        }
        val orders=r.optJSONObject("channelOrder")?:JSONObject()
        if(orders.length()>0) {
            val merged=runCatching { JSONObject(p[stringPreferencesKey("channel_order")]?:"{}") }.getOrDefault(JSONObject())
            orders.keys().forEach { group ->
                val ids=JSONArray(strings(orders.optJSONArray(group)).map { "$source|$it" })
                merged.put(group,ids);merged.put("$source::$group",ids)
            }
            out[stringPreferencesKey("channel_order")]=merged.toString()
        }
        return out.toPreferences()
    }
}
